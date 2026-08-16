# Pathlocked wiki-scraper tooling (Lane C)

Python tooling that talks to the **Old School RuneScape Wiki**
(<https://oldschool.runescape.wiki>) to (1) verify the hand-authored
`monsters.json` and (2) generate item-tag candidates for the v0.2 `item_tags.json`.

No third-party dependencies — standard library only, so `python3 scripts/<entry>.py`
runs on a clean machine.

## Quick start

```bash
python3 scripts/run_all.py          # runs both steps, writes to scripts/out/
python3 scripts/verify_monsters.py  # just the monsters report
python3 scripts/generate_item_tags.py  # just the item-tag candidates
```

Flags (all entry points):

| Flag | Effect |
| --- | --- |
| `--refresh` | Ignore cached responses and refetch everything from the wiki. |
| `--no-cache` | Neither read nor write the disk cache (fully live run). |

## Outputs (committed, in `scripts/out/`)

- **`monsters-report.md`** — a read-only diff report of `monsters.json` vs the
  wiki: name resolves, combat level matches, F2P status. It never edits the
  resource JSON (another lane owns content this stage).
- **`item_tags.generated.json`** — auto-generated **candidate** item tags. This
  is *not* the final `item_tags.json`; it is input for human curation next stage.
  Schema: [`docs/item-tags-schema.md`](../docs/item-tags-schema.md).
  Integration path: [`docs/integration-notes/lane-C.md`](../docs/integration-notes/lane-C.md).

## Files

| File | Role |
| --- | --- |
| `wikiclient.py` | Cached, rate-limited API client (see "Wiki etiquette" below). |
| `verify_monsters.py` | Monster verifier → `out/monsters-report.md`. |
| `generate_item_tags.py` | Item-tag generator → `out/item_tags.generated.json`. |
| `tag_config.py` | **The curated part** — tag definitions as category intersections. |
| `run_all.py` | Single entry point running both steps with one shared client/cache. |

## How the wiki data is queried

The OSRS Wiki does **not** have Semantic MediaWiki (`action=ask`) or Cargo
(`action=cargoquery`) — both return `badvalue`. It uses two mechanisms we rely on:

- **Bucket** (`action=bucket`) — Weird Gloop's Lua-statement query language over
  infobox data. We use `bucket('infobox_monster')` to read combat levels, e.g.
  `bucket('infobox_monster').select('combat_level').where('name','Chicken').limit(100).run()`.
  Schemas live at wiki pages `Bucket:Infobox monster` / `Bucket:Infobox item`.
  (Note: Bucket serialises BOOLEAN fields as `""`, so we never read the boolean
  value directly — we filter by category or by `.where('is_members_only', false)`.)
- **Category membership** (`action=query&list=categorymembers`) — the item/monster
  taxonomy. Item tags are built as **category intersections**, e.g.
  `Bronze ∩ Melee weapons ∩ Free-to-play items`. F2P status is itself a category
  (`Free-to-play items` / `Free-to-play monsters`).

To adjust which tags are generated, edit `tag_config.py` — each entry is a tag
name + tier + category + the wiki categories to intersect (`all_of` / `none_of`).

## Wiki etiquette (respected by `wikiclient.py`)

- **Disk cache** under `scripts/.cache/` (git-ignored), keyed by request URL. A
  second run is served entirely from cache — a full `run_all.py` rerun makes
  **0 live requests**. Use `--refresh` to intentionally refetch.
- **Descriptive User-Agent** identifying the tool (wiki policy asks for one).
- **Rate limiting**: a minimum interval between live requests, `maxlag=5`, and
  exponential backoff that honours `Retry-After` on HTTP 429/503.

## Caveats

- `item_tags.generated.json` is **candidates**, not final data. Category-based
  generation surfaces a little noise (joke items, some minigame variants). The
  most obvious minigame duplicates are filtered (`_MINIGAME_SUFFIX_MARKERS` in
  `generate_item_tags.py`); the rest is intentionally left for human curation.
- The monster report flags but never fixes issues. As of this writing it flags
  **King Black Dragon** as members-only — expected for a boss, a content call
  for the lane that owns `monsters.json`.
- SSL: system Python on macOS often lacks a CA bundle; the client falls back to
  `certifi` → `/etc/ssl/cert.pem` → system default. It never disables verification.
