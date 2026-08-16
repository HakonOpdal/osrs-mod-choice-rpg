# Plugin-hub submission checklist

Source of truth: the [runelite/plugin-hub README](https://github.com/runelite/plugin-hub/blob/master/README.md)
(fetched 2026-08-16). This repo was generated from `runelite/example-plugin`.
**Do not submit until v0.2** — this document is the pre-submission audit so the
eventual PR is a formality.

---

## The submission process (what actually happens)

Submitting is **not** a PR to *this* repo. It's a two-repo dance:

1. **This repo** stays a normal public GitHub repo. Its `runelite-plugin.properties`,
   `icon.png`, `LICENSE`, and `README.md` are read by the hub build.
2. You fork **`runelite/plugin-hub`**, add a one-file **marker** under
   `plugins/` that points at this repo + a commit hash, and PR *that*.

### Steps

1. Make sure this repo is **public** and the latest commit builds green.
2. Fork `https://github.com/runelite/plugin-hub`.
3. Create a branch (name it after the plugin, e.g. `pathlocked`).
4. Add a new file `plugins/pathlocked` (no extension) containing exactly:
   ```
   repository=https://github.com/<owner>/osrs-mod-choice-rpg.git
   commit=<full 40-char commit hash of this repo's release commit>
   ```
   - `repository` = the **HTTPS** clone URL (must end in `.git`).
   - `commit` = the full 40-character hash (not a tag, not a short hash).
5. Commit, push to your fork, open a PR against `runelite/plugin-hub` (Compare
   across forks). Write a short description of what the plugin does.
6. Watch two checks on the PR:
   - **`build (pull_request)`** — the actual Gradle build of this plugin.
   - **`RuneLite Plugin Hub Checks`** — static/lint checks. If it says
     *"Changes are needed."*, read the requested changes.
   Fix issues here, push a new commit to *this* repo, and update `commit=` in
   the marker file (all in the **same** hub PR — don't open new PRs).
7. Wait for a maintainer to review and merge.

### Updating later
Bump `commit=` in the marker file via a fresh branch off `upstream/master` and
open a new PR. (See the hub README's "Updating a plugin" section.)

---

## `runelite-plugin.properties` audit

Required/expected fields per the hub README example:

| Field | Required | This repo | Status |
|---|---|---|---|
| `displayName` | yes | `Pathlocked` | ✅ |
| `author` | yes | `Hakon Andreas Opdal` | ✅ |
| `description` | yes | present, one line | ✅ |
| `tags` | recommended | `gamemode,unlock,draft,restriction,bronzeman,tileman` | ✅ |
| `plugins` | yes | `com.pathlocked.PathlockedPlugin` (FQN, matches class) | ✅ |
| `version` | optional | `0.1.0` | ✅ (added; else commit is used) |
| `build` | yes | `standard` | ✅ (added) |

`build=standard` means the hub **replaces** `build.gradle`/`settings.gradle` at
package time. That is fine here: this plugin has **no third-party dependencies**
beyond RuneLite's transitive set, so no dependency-verification metadata is
needed (which would otherwise slow review significantly). If v0.2 ever adds a
dependency, switch to `build=gradle` and follow the hub's dependency-verification
process.

---

## Asset & repo requirements

| Requirement | Rule | This repo | Status |
|---|---|---|---|
| Icon | optional `icon.png` at repo root, **≤ 48×72 px** | `icon.png` = **48×64 px** | ✅ |
| Icon (in-jar copy) | loaded via classpath for the nav button | `src/main/resources/com/pathlocked/icon.png`, byte-identical | ✅ |
| License | BSD 2-Clause recommended | `LICENSE` = BSD 2-Clause | ✅ |
| README | "write a nice README" | present, with features + shot list | ✅ |
| Repo visibility | must be **public** | _verify before PR_ | ⚠️ check |
| `runeLiteVersion` | should be `'latest.release'` | `build.gradle` uses `latest.release` | ✅ |

---

## Static-check / rejection-reason audit

The hub rejects malicious plugins, ones that break
[Jagex's third-party guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1),
or ones matching a
[Rejected/Rolled-back feature](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).
The automated checks also flag common code smells. Audit of this repo
(`grep` over `src/main/java`, 2026-08-16):

| Check | Concern | Finding |
|---|---|---|
| **No `new Gson()`** | direct Gson instantiation is flagged; use the injected `Gson` | ✅ none — `Gson` is `@Inject`ed and threaded through `ContentRepository`/`ProfileManager` |
| **No reflection** | `setAccessible`, `Class.forName`, `java.lang.reflect` | ✅ none |
| **No external processes** | `Runtime.exec`, `ProcessBuilder` | ✅ none |
| **No raw networking** | `new URL`, `HttpURLConnection`, sockets, unmanaged OkHttp | ✅ none — the plugin is fully offline |
| **Resource loading** | prefer `getResourceAsStream` over `getResource` (jar-URL trap) | ✅ uses `getResourceAsStream` + `ImageUtil.loadImageResource`; no `getResource(` |
| **Filesystem scope** | writes confined to `RUNELITE_DIR` | ✅ only `~/.runelite/pathlocked/` via `RuneLite.RUNELITE_DIR`, atomic temp-then-move |
| **No bundled binaries** | only images/sounds/data under `src/main/resources` | ✅ only `icon.png` + three JSON data files |
| **Gamemode legitimacy** | not a botting/automation aid | ✅ purely restrictive/cosmetic overlays + menu deprioritization; no automation |

**No blockers found.** The one runtime item to double-check manually: attack
**hard-blocking** consumes a menu click (`MenuOptionClicked.consume()`). This is
a standard, accepted pattern for restriction gamemodes (Bronzeman-style plugins
do the same), not input automation — but it's the only behavior a reviewer might
question, so call it out plainly in the PR description.

---

## Pre-submission audit result

**Ready pending v0.2 content + the four screenshots.** Concretely, before opening
the hub PR:

1. [ ] Confirm this repo is **public** on GitHub.
2. [ ] Capture the four README screenshots into `docs/images/` (see shot list).
3. [ ] Tag/commit the release; `version=` and the marker `commit=` should match it.
4. [ ] Green `./gradlew build` on the release commit.
5. [ ] Fork plugin-hub, add `plugins/pathlocked`, open the PR.
6. [ ] In the PR body, describe the gamemode and pre-empt the attack-consume
       question (restriction UX, not automation).

Everything in the code/properties/asset audit above is already green.
