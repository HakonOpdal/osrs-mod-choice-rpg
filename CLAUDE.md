# Pathlocked — OSRS choice-unlock gamemode (RuneLite plugin)

Roguelite progression layer over OSRS: all regions and monsters start locked;
playing earns points (1 pt / 10 non-combat XP, kill points = combat level),
point thresholds trigger seeded 1-of-3 drafts (one reroll; every 5th choice is
a free pick from both categories).

**Standing rule: update the "Current Status" section below at the end of every
working session and before every commit, so the next session always starts
from the latest state. Keep it short — replace stale lines, don't append.**

## Current Status (updated 2026-08-16)

- **Stage-1 lanes done.** Lanes A (card overlay), B (region curation), C (wiki
  scraper) merged to `main` (PRs #1–#3). Lane D (hub-submission prep: README,
  icon, runelite-plugin.properties, checklist) lives on
  `emdash/hub-submission-qx2ti` — hub PR itself deferred to v0.2.
- README screenshot table is commented out until the four captures exist in
  `docs/images/` (shot list is in the README); Håkon owns the screenshots.
- Reviewed: /code-review self-pass + multiple Codex rounds across all lanes.
- Accepted decisions (do not re-litigate): own-hitsplat kill attribution
  (15s window); dungeons inherit surface square via ry−100 rule; new profiles
  bank the first threshold (instant draft at login); unlisted NPCs and
  non-UNLOCKED ground earn no XP points, kills reject only LOCKED (trespass).
- Next up (parked in ~/claude-projects/coding-agent-tasks.md): balancing pass,
  then v0.2 scope (item tags + skill locks), screenshots + hub submission.

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
