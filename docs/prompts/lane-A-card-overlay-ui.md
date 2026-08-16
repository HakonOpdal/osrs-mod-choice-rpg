# Lane A — Card-overlay draft UI

## Context
This repo is **Pathlocked**, a RuneLite plugin (choice-unlock gamemode). Read `CLAUDE.md` at the repo root first — it has current status, commands, and architecture. Drafting currently happens only in the Swing side panel (`ui/PathlockedPanel.java`); the in-game `ui/StatusOverlay.java` just shows a "Choice ready!" banner.

## Task
Build a center-screen, in-game draft card experience: when a draft is pending, show the 3 (or up to 6 on FREE picks) options as large cards — trading-card style: option name, category, tier, detail line, and a visual treatment per category (region vs monster). Clickable in-game if feasible; researching clickability is part of the task:
- RuneLite overlays are not natively clickable. The proven approach is registering a `MouseListener` via `MouseManager` and hit-testing card bounds rendered by your overlay. Investigate and implement that; fall back to "cards are display-only + keybind or panel click" only if hit-testing proves unworkable, and document why.
- Include a reroll affordance and respect the existing pick semantics: picks are identified by `(index, expectedName)` — see `PathlockedPanel.Actions`.

## File ownership (hard boundary)
- You may create/edit files ONLY in `src/main/java/com/pathlocked/ui/` (new classes preferred) and `src/test/java/` for tests.
- You may NOT edit `PathlockedPlugin.java`, `DraftService.java`, `CLAUDE.md`, `resources/*.json`, or anything in `content/ points/ unlocks/ enforcement/`.
- For wiring, define a small interface in `ui/` (e.g. `DraftCardPresenter` receiving the offers snapshot + an `Actions` callback) and write exact wiring instructions to `docs/integration-notes/lane-A.md` — the integration session will connect it to the plugin.

## Definition of done
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jbr-17.0.5-osx-aarch64-b653.14/Contents/Home ./gradlew build` green.
- Card overlay renders correctly at common client sizes (test with a fake offers list; a small test main or screenshot via `./gradlew run` is ideal).
- `docs/integration-notes/lane-A.md` written: wiring steps, config toggles you expect, known limitations.
- Commit to this worktree's branch with a clear message.
