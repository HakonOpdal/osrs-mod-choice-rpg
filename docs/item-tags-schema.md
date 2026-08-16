# `item_tags.json` schema (v0.2)

Defines the shape of `src/main/resources/com/pathlocked/item_tags.json` — the
curated item-tag data that v0.2 needs (skill/item locks and item-based drafts).
It is too large to hand-author from scratch, so `scripts/generate_item_tags.py`
produces a **candidate** file (`scripts/out/item_tags.generated.json`) in this
exact shape, which a human then curates into the real resource.

## Design principles

- **Item _names_, not ids** — consistent with `monsters.json`, which matches NPCs
  by name so cache/id churn doesn't break the data. `ContentRepository` already
  does case-insensitive name matching for monsters; item tags follow suit.
- **A tag groups many items under one draftable concept** ("Bronze tier", "Basic
  food"). Drafts and locks reference the tag; the tag expands to its item names.
- **Tier is optional.** Metal equipment has a natural 1–7 progression (bronze →
  rune); consumables/resources don't, so `tier` is `null` for them.

## Shape

```jsonc
{
  "comment": "F2P item tags for Pathlocked, matched by item name. See docs/item-tags-schema.md.",
  "tags": [
    {
      "name": "Bronze tier",        // required, unique display name / stable key
      "tier": 1,                     // required; integer 1..7 for metal tiers, else null
      "category": "equipment",       // required; see enum below
      "itemNames": [                 // required; wiki item names (may be empty during curation)
        "Bronze dagger",
        "Bronze full helm",
        "Bronze platebody"
      ],
      "notes": "F2P wearable bronze gear."  // optional free-text
    }
  ]
}
```

### Field reference

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `name` | string | yes | Unique. Used as the stable key a draft/lock refers to. |
| `tier` | integer \| null | yes | 1..7 for metal equipment tiers (bronze=1 … rune=7); `null` otherwise. |
| `category` | string enum | yes | One of: `equipment`, `food`, `resource`, `rune`, `tool`, `ammo`. |
| `itemNames` | string[] | yes | OSRS Wiki item names. Case-insensitive match at runtime (mirror `monsters.json`). May be empty while curating. |
| `notes` | string | no | Provenance / curation note. |

`category` enum meanings: `equipment` (wearable gear), `food` (edibles),
`resource` (logs/ores/bars/seeds — gathered/processed), `rune`, `tool` (skilling
tools), `ammo` (ranged ammunition). Extend the enum here if v0.2 needs more.

### Top-level metadata

The **generated** file additionally carries `source`, `tagCount`, and
`totalItemSlots` for provenance/debugging. These are informational; the final
curated resource only needs `comment` + `tags` (extra keys are harmless — the
Java loader reads `tags` and ignores unknown fields, like the existing
`monsters.json`/`regions.json` `comment` field).

## Deviations from the suggested shape (and why)

The lane prompt suggested `{ "name", "tier", "category", "itemNames", "notes" }`.
This schema keeps that verbatim. The only additions are **top-level provenance
metadata on the generated file** (not required in the curated resource) and an
explicit, documented `category` **enum** so the six buckets are validated rather
than free-form. No field was renamed or dropped.

## Validation expectations (next stage)

When the curated `item_tags.json` lands under `src/main/resources/...`, extend
`ContentDataTest` to assert: unique `name`s; `category` in the enum; `tier` is
`null` or 1..7; `itemNames` non-empty for shipped tags. (Mirrors how
`ContentDataTest` already guards `monsters.json` / `regions.json`.)
