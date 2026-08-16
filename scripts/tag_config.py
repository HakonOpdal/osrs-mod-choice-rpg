"""Curated definitions for the item-tag candidate generator.

Each entry describes ONE candidate tag. The generator resolves an entry to a
concrete item list by intersecting OSRS Wiki categories: a page is included only
if it belongs to *every* category in ``all_of`` (and none in ``none_of``). The
free-to-play filter is applied on top of every tag automatically, because
Pathlocked is a F2P gamemode — see ``FREE_TO_PLAY_CATEGORY``.

This file is the human-curated part. To add/adjust a tag, edit this list; the
generator (``generate_item_tags.py``) does the fetching and set arithmetic.

Schema of each dict (mirrors docs/item-tags-schema.md):
  name      display name of the tag
  tier      integer tier (1..7 for equipment metal tiers) or None
  category  broad bucket: equipment | food | resource | rune | tool | ammo
  all_of    wiki categories the item must ALL belong to
  none_of   wiki categories that exclude an item (optional)
  notes     free-text provenance / curation note
"""

# Category that marks an item as free-to-play. Applied to every tag.
FREE_TO_PLAY_CATEGORY = "Free-to-play items"

# The seven F2P metal equipment tiers. The wiki tags each item with its material
# category ("Bronze", "Iron", ...) and "Equipable items" for wearable gear.
_METAL_TIERS = [
    ("Bronze", 1),
    ("Iron", 2),
    ("Steel", 3),
    ("Black", 4),
    ("Mithril", 5),
    ("Adamant", 6),
    ("Rune", 7),
]

TAG_DEFINITIONS = []

# Equipment by metal tier (all wearable gear of that material).
for _material, _tier in _METAL_TIERS:
    TAG_DEFINITIONS.append(
        {
            "name": f"{_material} tier",
            "tier": _tier,
            "category": "equipment",
            "all_of": [_material, "Equipable items"],
            "none_of": [],
            "notes": f"F2P wearable {_material.lower()} gear (weapons + armour).",
        }
    )
    # A finer split some balancing may want: just the melee weapons of that tier.
    TAG_DEFINITIONS.append(
        {
            "name": f"{_material} melee weapons",
            "tier": _tier,
            "category": "equipment",
            "all_of": [_material, "Melee weapons"],
            "none_of": [],
            "notes": f"F2P {_material.lower()} melee weapons only.",
        }
    )

# Consumables and gathered resources (tier-less; category carries the meaning).
TAG_DEFINITIONS.extend(
    [
        {
            "name": "Basic food",
            "tier": None,
            "category": "food",
            "all_of": ["Food", "Edible items"],
            "none_of": [],
            "notes": "F2P edible food (fish, bread, pies, etc.).",
        },
        {
            "name": "Basic runes",
            "tier": None,
            "category": "rune",
            "all_of": ["Runes"],
            "none_of": [],
            "notes": "F2P elemental/catalytic runes.",
        },
        {
            "name": "Normal logs",
            "tier": None,
            "category": "resource",
            "all_of": ["Logs"],
            "none_of": [],
            "notes": "F2P woodcutting logs.",
        },
        {
            "name": "Ores",
            "tier": None,
            "category": "resource",
            "all_of": ["Ores"],
            "none_of": [],
            "notes": "F2P mining ores.",
        },
        {
            "name": "Metal bars",
            "tier": None,
            "category": "resource",
            "all_of": ["Metal bars"],
            "none_of": [],
            "notes": "F2P smithing bars.",
        },
        {
            "name": "Seeds",
            "tier": None,
            "category": "resource",
            "all_of": ["Seeds"],
            "none_of": [],
            "notes": "F2P farming/planting seeds.",
        },
        {
            "name": "Tools",
            "tier": None,
            "category": "tool",
            "all_of": ["Tools"],
            "none_of": [],
            "notes": "F2P skilling tools (axes, pickaxes, etc.).",
        },
        {
            "name": "Ammunition",
            "tier": None,
            "category": "ammo",
            "all_of": ["Ammunition slot items"],
            "none_of": [],
            "notes": "F2P ranged ammunition (arrows, bolts, etc.). "
            "Uses the equipment-slot category — the 'Ammunition' category is cannonballs only.",
        },
    ]
)
