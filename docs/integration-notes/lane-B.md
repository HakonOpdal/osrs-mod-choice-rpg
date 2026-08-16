# Lane B — Region & dungeon data curation

## What changed (data changelog)

Region/dungeon data in `src/main/resources/com/pathlocked/regions.json` was
verified against OSRS map-square coordinates (`rx = worldX>>6`, `ry = worldY>>6`,
`id = (rx<<8)|ry`) and extended with explicit dungeon ownership.

### Surface-square verification

Every surface square's `rx/ry/name` was cross-checked. Confirmed-correct
suspects (no change needed):

- **Corsair Cove** `(40,44)` — town centre ~(2567, 2861) → `(40,44)`. ✓
- **Emir's Arena** `(52,50)` — arena ~(3360, 3250) → `(52,50)`. ✓ (renamed from
  Duel Arena in-game; data already used the current name.)
- **Crafting Guild Coast** `(45,51)` — guild ~(2935, 3283) → `(45,51)`. ✓
- **Mudskipper Point** `(46,48)` — ~(3000, 3100) → `(46,48)`. ✓

- **Dwarven Mine** `(47,53)` — this is the **surface** square holding the mine's
  north/ramp entrance (~3019, 3448). Its `notes` now say "surface entrance"; the
  actual underground mine is mapped explicitly (below) rather than left to the
  generic `ry−100` fallback, which would otherwise misattribute the mine's
  eastern columns to Falador East / Barbarian Village.

### Explicit dungeon mappings (added)

Coordinates verified against oldschool.runescape.wiki (`{{Map}}` centres,
`focusarea` polygons, and per-monster spawn tables). A region square is 2D, so
stacked planes share one id.

| Owner (surface) | Dungeon | Underground squares `(rx,ry)` |
|---|---|---|
| Dwarven Mine `(47,53)` | Dwarven Mine | `(47,152) (47,153)` |
| Barbarian Village `(48,53)` | Stronghold of Security (all 4 levels) | `(29,79) (29,80) (29,81) (30,76) (30,77) (30,78) (30,79) (30,80) (31,76) (31,77)` |
| Varrock `(50,53)` | Varrock sewers | `(49,154) (50,153) (50,154) (50,155)` |
| Edgeville `(48,54)` | Edgeville dungeon | `(48,153) (48,154) (48,155)` |
| Mudskipper Point `(46,48)` | Asgarnian Ice Dungeon | `(46,149) (47,149)` |
| Karamja Volcano `(44,49)` | Karamja dungeon (SE) | `(44,149) (45,149)` |
| Crandor `(44,50)` | Crandor tunnel + Elvarg lab (NW) | `(44,150) (45,150) (44,151)` |
| Corsair Cove `(40,44)` | Corsair Cove Dungeon | `(30,140) (31,140)` |
| Wilderness 41-48 Mid-West `(47,60)` | King Black Dragon lair | `(35,73)` |

Notes on non-obvious choices:

- **KBD lair `(35,73)` = region 9033.** This is the runtime region RuneLite
  reports (`getRegionID()`) inside the lair, which is the *only* thing the plugin
  reads. The OSRS Wiki lists the lair on its detached map (mapID 26) at world
  (3109, 10265) → `(48,160)`, but that is a wiki-map display coordinate for the
  instanced lair, not the runtime region — do not use it. The lair is nowhere
  near `surface_ry + 100`, so the generic rule leaves it `UNCHARTED`; the
  explicit mapping is what ties it to the Lava Maze square `(47,60)`.
- **Edgeville ↔ Varrock seam `(49,154)`.** Both dungeons genuinely touch this
  64-tile square (Edgeville's NE passage vs. Varrock's western moss giants).
  It is assigned to **Varrock** because Edgeville's core is the 48-column;
  `validate()` guarantees it is claimed exactly once.
- **Karamja / Crandor** interpenetrate underground; split by column — the SE
  volcano squares go to Karamja, the NW tunnel + Elvarg's instanced lab
  `(44,151)` to Crandor — so neither owns the other's square.
- **Left to the generic `ry−100` fallback** (not explicitly mapped): shallow
  dungeons that sit cleanly under a single correct surface owner and where the
  generic rule already resolves right, e.g. the Wizard's Tower basement.

All four Stronghold floors are now confirmed from wiki spawn tables (Minotaur
L1 → `(29,81)`; Flesh Crawler L2 → `(29,79)(29,80)(30,79)(30,80)`; Catablepon
L3 → `(30,78)`; Ankou L4 → `(30,76)(30,77)(31,76)(31,77)`).

Residual uncertainty (mapped the confirmed core; deliberately excluded fringe
squares to stay inside the "claimed once" invariant): Dwarven Mine's western
`rx46` fringe (borders the Mining Guild complex) and the Corsair Cove Dungeon's
western Myths' Guild half. Widen these later if in-game testing shows an unowned
dungeon square reading as `UNCHARTED`.

### New schema field: `underground`

`RegionDef` gained an optional per-region array:

```json
{ "rx": 47, "ry": 53, "name": "Dwarven Mine", "tier": 2,
  "underground": [[47,151],[47,152],[47,153]] }
```

Each `[rx, ry]` pair is an underground map square owned by that region's unlock.
This is authored data — it does **not** change surface adjacency, the draft
frontier, or the softlock guarantee (the `underground` field is orthogonal to
the `regions`/`links` graph the simulation walks).

## New API: `ContentRepository.surfaceOwnerOf(int regionId)`

```java
Integer owner = content.surfaceOwnerOf(undergroundRegionId);
```

Returns the owning **surface** region id for an explicitly-mapped underground
square, or `null` when the square has no explicit mapping. Backed by an index
built at load time from every region's `underground` array.

## How to switch the plugin off the generic `ry−100` fallback

`PathlockedPlugin.java` currently derives a dungeon's owner with the generic rule
(do **not** edit it as part of Lane B). Two call sites resolve `surfaceId`
(around lines 474 and 586):

```java
int surfaceId = regionId - 100;
if ((regionId & 0xFF) >= 100 && content.isKnownRegion(surfaceId))
{
    return profile.isRegionUnlocked(surfaceId) ? UNLOCKED : LOCKED;
}
```

To adopt the explicit mappings, prefer `surfaceOwnerOf` and fall back to the
generic rule so behaviour is a strict superset of today's:

```java
Integer explicit = content.surfaceOwnerOf(regionId);
int surfaceId = explicit != null ? explicit : regionId - 100;
boolean mapped = explicit != null
    || ((regionId & 0xFF) >= 100 && content.isKnownRegion(surfaceId));
if (mapped && content.isKnownRegion(surfaceId))
{
    return profile.isRegionUnlocked(surfaceId) ? UNLOCKED : LOCKED;
}
```

Key points for whoever wires this in:

- `surfaceOwnerOf` covers dungeons that are **not** at `surface_ry + 100`
  (KBD lair, Stronghold of Security, Corsair Cove Dungeon), which the generic
  rule cannot reach at all — those squares are `UNCHARTED` today.
- For dungeons that *are* at `+100` but spread under several surface columns
  (Dwarven Mine, Varrock sewers, Edgeville dungeon), the explicit mapping
  pins every square to one intended owner instead of whatever square sits
  directly above it.
- The mapping never claims a surface square (`validate()` enforces this), so
  adopting it cannot change how a normal above-ground square is gated.

## Validation / tests

`ContentRepository.validate()` now also checks: each `underground` entry is a
two-element pair, no square is mapped by two regions, and no underground square
collides with a surface region id. `ContentDataTest` asserts the round-trip and
a couple of known owner mappings. The 500-seed `DraftSimulationTest` stays green.
