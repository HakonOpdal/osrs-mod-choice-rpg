"""Cached, rate-limited client for the Old School RuneScape Wiki API.

The OSRS Wiki (https://oldschool.runescape.wiki) exposes structured data through
two mechanisms we use here:

  * the standard MediaWiki ``action=query`` API — used for category membership
    (``list=categorymembers``), and
  * the Weird Gloop **Bucket** extension (``action=bucket``) — a Lua-statement
    query language over structured infobox data. NOTE: the wiki does NOT have
    Semantic MediaWiki (``action=ask``) or Cargo (``action=cargoquery``); both
    return ``badvalue``. Bucket is the current mechanism (as of 2026-08).

Design goals (see scripts/README.md):
  * **Rerunnable & cached** — every GET is cached on disk under ``.cache/`` keyed
    by the full request URL. Reruns hit the cache and never touch the network
    unless ``--refresh`` is passed (or a specific cache entry is missing).
  * **Polite** — a descriptive User-Agent (wiki policy asks for one), a minimum
    interval between *live* requests, and the ``maxlag`` parameter so we back off
    when the wiki's replicas are lagging.
"""

from __future__ import annotations

import hashlib
import json
import os
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request

API_ENDPOINT = "https://oldschool.runescape.wiki/api.php"

# Wiki etiquette asks for a unique, descriptive User-Agent that identifies the
# tool and a way to reach the author. This is a hobby-project scraper for the
# Pathlocked RuneLite plugin; it makes a few hundred cached requests per run.
USER_AGENT = (
    "Pathlocked-wiki-scraper/0.1 "
    "(https://github.com/pathlocked; RuneLite choice-unlock gamemode tooling)"
)

CACHE_DIRECTORY = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")

# Minimum seconds between two *live* (cache-miss) requests. The wiki is fine with
# steady low-rate serial access; this keeps us well under any rate limit.
MIN_SECONDS_BETWEEN_LIVE_REQUESTS = 0.5


def _build_ssl_context() -> ssl.SSLContext:
    """Build an SSL context with a working CA bundle.

    System Python on macOS frequently ships without a usable CA store, so the
    default context raises ``CERTIFICATE_VERIFY_FAILED``. Try, in order: the
    ``certifi`` bundle (if installed), then common system bundle locations, then
    the plain default. We never disable verification.
    """
    try:
        import certifi  # type: ignore

        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        pass
    for candidate in (
        "/etc/ssl/cert.pem",  # macOS / many Linux distros
        "/etc/ssl/certs/ca-certificates.crt",  # Debian/Ubuntu
        "/usr/local/etc/openssl@3/cert.pem",  # Homebrew (Intel)
        "/opt/homebrew/etc/openssl@3/cert.pem",  # Homebrew (Apple Silicon)
    ):
        if os.path.exists(candidate):
            return ssl.create_default_context(cafile=candidate)
    return ssl.create_default_context()


class WikiClient:
    """A small, cached, polite wrapper over the OSRS Wiki API."""

    def __init__(self, use_cache: bool = True, refresh: bool = False, verbose: bool = True):
        # use_cache=False bypasses reads AND writes (rarely needed).
        # refresh=True ignores existing cache entries but still writes fresh ones.
        self.use_cache = use_cache
        self.refresh = refresh
        self.verbose = verbose
        self.ssl_context = _build_ssl_context()
        self._last_live_request_time = 0.0
        self.live_request_count = 0
        self.cache_hit_count = 0
        if self.use_cache:
            os.makedirs(CACHE_DIRECTORY, exist_ok=True)

    # -- low-level GET with disk cache ------------------------------------

    def _cache_path_for(self, url: str) -> str:
        digest = hashlib.sha256(url.encode("utf-8")).hexdigest()
        return os.path.join(CACHE_DIRECTORY, f"{digest}.json")

    def get_json(self, parameters: dict) -> dict:
        """GET the API with ``parameters`` and return parsed JSON, using the cache."""
        query_parameters = {"format": "json", "maxlag": "5", **parameters}
        url = API_ENDPOINT + "?" + urllib.parse.urlencode(query_parameters)
        cache_path = self._cache_path_for(url)

        if self.use_cache and not self.refresh and os.path.exists(cache_path):
            self.cache_hit_count += 1
            with open(cache_path, "r", encoding="utf-8") as cache_file:
                return json.load(cache_file)

        payload = self._fetch_live_with_retries(url)

        if self.use_cache:
            with open(cache_path, "w", encoding="utf-8") as cache_file:
                json.dump(payload, cache_file)
        return payload

    def _fetch_live_with_retries(self, url: str, max_attempts: int = 5) -> dict:
        for attempt in range(1, max_attempts + 1):
            self._respect_rate_limit()
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            try:
                with urllib.request.urlopen(request, timeout=30, context=self.ssl_context) as response:
                    self.live_request_count += 1
                    payload = json.load(response)
            except urllib.error.HTTPError as error:
                # 429 (rate limited) / 503 (maxlag) → honor Retry-After and back off.
                if error.code in (429, 503) and attempt < max_attempts:
                    retry_after = float(error.headers.get("Retry-After", attempt * 2))
                    if self.verbose:
                        print(f"  [wiki] HTTP {error.code}; backing off {retry_after:.0f}s")
                    time.sleep(retry_after)
                    continue
                raise
            except urllib.error.URLError:
                if attempt < max_attempts:
                    time.sleep(attempt * 2)
                    continue
                raise

            # maxlag is reported as a normal 200 with an error body on some paths;
            # handle the documented error code defensively.
            if isinstance(payload, dict) and payload.get("error", {}).get("code") == "maxlag":
                if attempt < max_attempts:
                    time.sleep(attempt * 2)
                    continue
            return payload
        raise RuntimeError(f"Exhausted retries fetching {url}")

    def _respect_rate_limit(self) -> None:
        elapsed = time.monotonic() - self._last_live_request_time
        if elapsed < MIN_SECONDS_BETWEEN_LIVE_REQUESTS:
            time.sleep(MIN_SECONDS_BETWEEN_LIVE_REQUESTS - elapsed)
        self._last_live_request_time = time.monotonic()

    # -- high-level helpers ------------------------------------------------

    def bucket_query(self, lua_statement: str) -> list:
        """Run a Bucket ``action=bucket`` Lua statement; return the result rows.

        Raises ``WikiQueryError`` if the API reports a query error (e.g. an
        unknown bucket or field), so callers fail loudly rather than silently
        treating a typo as "no results".
        """
        payload = self.get_json({"action": "bucket", "query": lua_statement})
        if "error" in payload:
            raise WikiQueryError(f"Bucket error for `{lua_statement}`: {payload['error']}")
        # Result rows arrive under the "bucket" key.
        return payload.get("bucket", [])

    def category_members(self, category: str, namespace: int = 0) -> list:
        """Return all page titles in ``Category:<category>`` (namespace 0 = articles).

        Fully paginates via ``cmcontinue``. Category membership is how the wiki
        exposes item taxonomy (materials like ``Bronze``, types like ``Food``,
        and crucially ``Free-to-play items``).
        """
        titles: list = []
        continue_token = None
        while True:
            parameters = {
                "action": "query",
                "list": "categorymembers",
                "cmtitle": f"Category:{category}",
                "cmlimit": "500",
                "cmtype": "page",
                "cmnamespace": str(namespace),
            }
            if continue_token:
                parameters["cmcontinue"] = continue_token
            payload = self.get_json(parameters)
            titles.extend(member["title"] for member in payload["query"]["categorymembers"])
            continue_token = payload.get("continue", {}).get("cmcontinue")
            if not continue_token:
                break
        return titles

    def stats_line(self) -> str:
        return (
            f"{self.live_request_count} live request(s), "
            f"{self.cache_hit_count} cache hit(s)"
        )


class WikiQueryError(RuntimeError):
    """Raised when the wiki API returns a structured error for a query."""
