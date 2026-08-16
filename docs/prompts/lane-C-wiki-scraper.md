# Lane C — OSRS Wiki scraper tooling + item-tag schema

## Context
This repo is **Pathlocked**, a RuneLite plugin (choice-unlock gamemode). Read `CLAUDE.md` at the repo root first. Content data (`monsters.json`, `regions.json`) is hand-authored; v0.2 needs `item_tags.json` (~300–500 curated item tags like "bronze tier", "normal logs", "basic food"), which is too big to hand-author. The OSRS Wiki (oldschool.runescape.wiki) has structured data via its MediaWiki API and dumps.

## Task
1. Build Python tooling under `scripts/` (new directory) that:
   - **Verifies `monsters.json`**: for each monster, fetch the wiki page and check name, combat level(s), F2P status. Output a diff report (`scripts/out/monsters-report.md`) — do NOT edit the resource JSON (another lane owns it this stage).
   - **Generates item-tag candidates**: pull F2P items with categories (equipment tier/slot, food, tools, runes, logs/ores/bars, seeds…) and emit `scripts/out/item_tags.generated.json`.
2. **Define the `item_tags.json` schema** (this unblocks the next stage): document it in `docs/item-tags-schema.md`. Suggested shape: `{ "tags": [{ "name": "Bronze melee", "tier": 1, "category": "equipment", "itemNames": [...], "notes": "" }] }` — item names not ids (consistent with monsters.json name-matching). Justify any deviation.
3. Scripts must be rerunnable, cached (don't hammer the wiki; respect their API etiquette), and documented in `scripts/README.md`.

## File ownership (hard boundary)
- You may create/edit ONLY: `scripts/**`, `docs/item-tags-schema.md`, `docs/integration-notes/lane-C.md`.
- You may NOT edit anything under `src/`, the resource JSONs, or `CLAUDE.md`.

## Definition of done
- `python3 scripts/<entry>.py` runs end-to-end and produces the report + generated tags.
- Schema doc written; integration notes in `docs/integration-notes/lane-C.md` (how the generated file becomes `src/main/resources/com/pathlocked/item_tags.json` next stage).
- Commit to this worktree's branch.
