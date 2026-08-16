# Lane D — Plugin Hub submission prep

## Context
This repo is **Pathlocked**, a RuneLite plugin (choice-unlock gamemode). Read `CLAUDE.md` at the repo root first. The repo was scaffolded from `runelite/example-plugin` and already has `runelite-plugin.properties`, `LICENSE` (BSD-2), and a generated `icon.png`. Target: a submission-ready repo for a `runelite/plugin-hub` PR (submission itself happens later, after v0.2).

## Task
1. **README.md**: proper plugin README — what the gamemode is (elevator pitch), how it plays (points → thresholds → drafts), screenshots/GIF placeholders with a shot list (exact scenes to capture: draft cards, locked-region banner + shading, side panel, unlock chat message), config options table, FAQ (honor-mode nature, F2P v0.1 scope, profile file location, compatibility with Bronzeman/TCG).
2. **Icon polish**: improve `icon.png` (and the copy at `src/main/resources/com/pathlocked/icon.png` — keep them identical). Hub listing icons must be at most 48×72 px. Keep the padlock identity.
3. **Hub checklist**: research the current `runelite/plugin-hub` CONTRIBUTING requirements and write `docs/hub-submission.md`: exact PR steps, properties-file requirements, common rejection reasons (e.g. direct Gson instantiation — already fixed — verify nothing else on their static-check list applies), and a pre-submission audit result for this repo.
4. Audit `runelite-plugin.properties` fields against current hub requirements.

## File ownership (hard boundary)
- You may edit ONLY: `README.md`, `icon.png`, `src/main/resources/com/pathlocked/icon.png`, `docs/**` (except other lanes' integration notes), `runelite-plugin.properties`.
- You may NOT edit any `.java` file, other resource JSONs, or `CLAUDE.md`.

## Definition of done
- README reads as a finished hub listing (screenshots pending the shot list).
- `docs/hub-submission.md` checklist complete with the audit results.
- Integration notes (if any) in `docs/integration-notes/lane-D.md`.
- Commit to this worktree's branch.
