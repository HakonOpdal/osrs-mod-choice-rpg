# Pathlocked — OSRS choice-unlock gamemode (RuneLite plugin)

Roguelite progression layer over OSRS: all regions and monsters start locked;
playing earns points (1 pt / 10 non-combat XP, kill points = combat level),
point thresholds trigger seeded 1-of-3 drafts (one reroll; every 5th choice is
a free pick from both categories).

**Standing rule: update the "Current Status" section below at the end of every
working session and before every commit, so the next session always starts
from the latest state. Keep it short — replace stale lines, don't append.**

## Current Status (updated 2026-08-16)

- **v0.2 core implemented** on branch `emdash/v02-core` (item-tag + skill
  unlocks, void XP, keystone drafts, unlock-tree tab). Build green, 40/40
  tests. In review: /code-review self-pass -> Codex ensemble pending.
- v0.1 + Stage-1 lanes (card overlay, region curation, wiki scraper, hub prep,
  README screenshots) all merged to `main` (PRs #1-#5). Hub-submission PR to
  runelite/plugin-hub still deferred until v0.2 ships.
- v0.2 gameplay decisions (approved 2026-08-16, do not re-litigate): starters
  Attack/Str/HP + instant forced skill draft (also on migration, which banks
  one threshold); rotation region->monster->item, 5th free (<=6 mixed cards),
  10th skill keystone; 16 umbrella item tags with metal-tier chain (t needs
  t-1), starter tags Bronze tier/Basic food/Tools; enforcement = menu
  deprioritize+hard-block on Wield/Wear/Equip/Eat/Drink + inventory/bank/equip
  greying; locked-skill XP is void (tracked per skill, warned once/session);
  GE/shop + training-verb blocking deferred to v0.3.
- v0.1 accepted decisions (do not re-litigate): own-hitsplat kill attribution
  (15s window); dungeons inherit surface square via explicit mapping then
  ry-100 rule; unlisted NPCs/items and non-UNLOCKED ground earn no XP points,
  kills reject only LOCKED (trespass).
- Next up: finish v0.2 review rounds, playtest handoff, balancing pass
  (parked), then hub submission.

## Key commands

- Build + tests: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jbr-17.0.5-osx-aarch64-b653.14/Contents/Home ./gradlew build`
- Run dev client: same JAVA_HOME + `./gradlew run` (macOS needs the
  `--add-exports com.apple.eawt` jvmArgs already in build.gradle)
- Jagex accounts can't log into the dev client directly: launch official
  RuneLite via Jagex Launcher once with `--insecure-write-credentials`, then
  the dev client reuses `~/.runelite/credentials.properties`.

## Architecture (see docs/ for full design)

- `docs/choice-rpg-design.html` — full design exploration (v0.2+ ideas live here)
- `docs/v0.1-skeleton-plan.html` — the implemented plan
- Pure-logic core (`content/ points/ draft/ unlocks/`) has no RuneLite client
  imports and is unit-tested; only `enforcement/ ui/` + plugin class touch the client.
- Content data: `src/main/resources/com/pathlocked/{regions,monsters,starter_kit}.json`
  — region id = (rx<<8)|ry; adjacency derived from grid + explicit links;
  monsters matched by NPC name, not id. Validated by ContentDataTest;
  DraftSimulationTest proves 500 seeds reach 100% completion (softlock guard).
- Profiles: `~/.runelite/pathlocked/profile-<accountHash>.json`.

## Review workflow

Gradle build + tests green → one /code-review self-pass → `codex exec review
-m gpt-5.6-sol -c model_reasoning_effort="medium" --base main` (needs
`# codex-preflight: allow` comment in a Gradle repo, and `git add -A` first so
new files appear in the diff).
