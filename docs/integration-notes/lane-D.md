# Lane D — hub submission prep (integration notes)

## What changed (this worktree)
- `README.md` — full hub listing: elevator pitch, how-it-plays (points →
  thresholds → drafts), a screenshot table + 4-scene shot list, config table,
  FAQ (honor-mode, F2P scope, profile path, Bronzeman/TCG compatibility).
- `icon.png` + `src/main/resources/com/pathlocked/icon.png` — replaced the flat
  32×32 padlock with a crisper **48×64** brass padlock (supersampled AA, kept
  the padlock identity). Both copies are byte-identical (verified with `cmp`).
- `runelite-plugin.properties` — added `version=0.1.0` and `build=standard`
  (the hub example lists both; `build` was missing).
- `docs/hub-submission.md` — submission steps, properties/asset audit, and a
  clean static-check/rejection audit (no blockers).
- `docs/images/README.md` — filenames the README expects for the screenshots.

## Boundaries respected
No `.java`, no other resource JSON, no `CLAUDE.md` touched. Only README, icon
(both copies), `runelite-plugin.properties`, and `docs/**`.

## For other lanes / integration
- **Do not** submit to plugin-hub yet — target is post-v0.2 (see
  `docs/hub-submission.md`). This is prep only.
- If Lane B changes the region/monster **counts**, update the two numbers in the
  README FAQ ("83 regions and 47 monsters") — they're the only hard-coded
  content stats in my files. Current source counts: `regions.json` = 83,
  `monsters.json` = 47.
- The README's config table mirrors `PathlockedConfig` exactly (5 options). If
  Lane A/other lanes add/rename a `@ConfigItem`, sync the table.
- `build=standard` assumes **no new third-party dependencies**. If a later lane
  adds one, flip to `build=gradle` and follow the hub's dependency-verification
  flow (noted in `docs/hub-submission.md`).
