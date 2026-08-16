# Lane C — integration notes

What this lane produced and how it plugs into the next stage.

## Delivered

- `scripts/` — Python wiki tooling (no third-party deps). Entry point:
  `python3 scripts/run_all.py`. See `scripts/README.md`.
- `scripts/out/monsters-report.md` — read-only verification of `monsters.json`.
- `scripts/out/item_tags.generated.json` — candidate item tags (22 tags,
  ~518 item slots) in the schema below.
- `docs/item-tags-schema.md` — the `item_tags.json` schema (unblocks next stage).

Hard boundary respected: this lane touched **only** `scripts/**`,
`docs/item-tags-schema.md`, and this file. No `src/`, no resource JSON, no
`CLAUDE.md`.

## Monster verification — action for the content-owning lane

`scripts/out/monsters-report.md`: **46/47 clean**. All combat levels match a wiki
variant; all names resolve. One flag:

- **King Black Dragon — members-only on the wiki**, but Pathlocked is F2P.
  Almost certainly intentional (end-game boss / aspirational unlock). Decision
  belongs to whoever owns `monsters.json`; noted here so it isn't a surprise.
  If it should stay, add a note in `monsters.json`; if not, drop it.

Re-run `python3 scripts/verify_monsters.py --refresh` after any `monsters.json`
edit to regenerate the report.

## Turning the generated candidates into the shipped resource

The generated file is **candidates**, not the final data. Next-stage steps:

1. **Curate** `scripts/out/item_tags.generated.json`: remove noise (joke items
   like "Cabbage rune", stray minigame variants), merge/rename tags, and decide
   which tags v0.2 actually drafts on. The generator's category intersections are
   a starting point, not gospel.
2. **Reshape to the resource form**: keep `comment` + `tags`; the generated
   top-level `source`/`tagCount`/`totalItemSlots` keys can be dropped (the Java
   loader ignores unknown fields, so leaving them is harmless too).
3. **Place** the curated file at
   `src/main/resources/com/pathlocked/item_tags.json`.
4. **Load it** in `ContentRepository` alongside `monsters.json`/`regions.json`
   (add an `ItemTagDef` record mirroring `MonsterDef`), matching item names
   **case-insensitively** like the monster lookup.
5. **Validate** in `ContentDataTest`: unique tag names, `category` in the enum,
   `tier` null or 1..7, non-empty `itemNames`. See `docs/item-tags-schema.md`.

## Regenerating

```bash
python3 scripts/run_all.py            # cached; 0 live requests on a warm cache
python3 scripts/run_all.py --refresh  # refetch from the wiki
```

To change which tags are generated, edit `scripts/tag_config.py` (each entry is a
tag name + tier + category + the wiki categories to intersect). The cache lives in
`scripts/.cache/` (git-ignored).
