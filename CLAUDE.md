# Pathlocked — OSRS choice-unlock gamemode (RuneLite plugin)

Roguelite progression layer over OSRS: all regions and monsters start locked;
playing earns points (1 pt / 10 non-combat XP, kill points = combat level),
point thresholds trigger seeded 1-of-3 drafts (one reroll; every 5th choice is
a free pick from both categories).

**Standing rule: update the "Current Status" section below at the end of every
working session and before every commit, so the next session always starts
from the latest state. Keep it short — replace stale lines, don't append.**

## Current Status (updated 2026-08-16)

- **v0.1 complete, playtested, committed** on branch `emdash/init-cepqd`
  (latest: `fa0e003`). Build green, 19/19 tests.
- Playtest T1–T8 all verified by Håkon (checklist: `docs/v0.1-test-handoff.html`).
- Reviewed: /code-review self-pass (10 findings fixed) + multiple Codex rounds
  (~15 findings fixed). Codex loop stopped per non-convergence rule.
- Accepted decisions (do not re-litigate): own-hitsplat kill attribution
  (15s window); dungeons inherit surface square via ry−100 rule; new profiles
  bank the first threshold (instant draft at login); unlisted NPCs and
  non-UNLOCKED ground earn no XP points, kills reject only LOCKED (trespass).
- Next up (parked in ~/claude-projects/coding-agent-tasks.md): card-overlay
  draft UI (Pokemon-card style), region border/naming curation + explicit
  dungeon mappings, balancing pass, then v0.2 scope (item tags + skill locks).

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
