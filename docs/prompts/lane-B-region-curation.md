# Lane B — Region & dungeon data curation

## Context
This repo is **Pathlocked**, a RuneLite plugin (choice-unlock gamemode). Read `CLAUDE.md` at the repo root first. Region data lives in `src/main/resources/com/pathlocked/regions.json`: F2P surface map squares with `rx/ry` (region id = `(rx<<8)|ry`), grid-derived adjacency plus explicit `links`. The data was authored from memory and is flagged for verification. Underground squares currently inherit the surface square directly above via a generic ry−100 rule implemented in the plugin (which you must NOT edit).

## Task
1. **Verify every square** against the OSRS Wiki / in-game coordinates: names, rx/ry, tiers, links, and the wilderness grid. Fix wrong or missing squares (known suspects: Dwarven Mine naming — the surface square (47,53) vs the actual underground mine at ry≈151–153 under Falador-area columns; Corsair Cove and its cave; Emir's Arena; Crafting Guild coast).
2. **Add explicit dungeon mappings**: extend the schema with an optional `"underground": [[rx, ry], ...]` array per region (underground squares owned by that region's unlock). Implement loading + an API in `ContentRepository` — e.g. `Integer surfaceOwnerOf(int regionId)` returning the owning surface region id for explicitly mapped underground squares (null otherwise). Cover at least: Dwarven Mine, Edgeville dungeon, Varrock sewers, Port Sarim ice dungeon (Mudskipper Point), Karamja volcano dungeon, Crandor tunnel, Corsair Cove cave, KBD lair, Stronghold of Security (Barbarian Village).
3. Extend `validate()` and `ContentDataTest` for the new field (no square mapped twice, owner exists, etc.). Keep the softlock simulation green.

## File ownership (hard boundary)
- You may edit ONLY: `src/main/resources/com/pathlocked/*.json`, `src/main/java/com/pathlocked/content/`, and `src/test/java/`.
- You may NOT edit `PathlockedPlugin.java`, anything in `ui/ points/ draft/ unlocks/ enforcement/`, or `CLAUDE.md`. The plugin's ry−100 fallback stays; write instructions for switching it to `surfaceOwnerOf` in `docs/integration-notes/lane-B.md`.

## Definition of done
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jbr-17.0.5-osx-aarch64-b653.14/Contents/Home ./gradlew build` green (all validation + 500-seed simulation tests pass).
- A short data changelog + integration instructions in `docs/integration-notes/lane-B.md`.
- Commit to this worktree's branch.
