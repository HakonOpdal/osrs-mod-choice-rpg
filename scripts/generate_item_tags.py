"""Generate item-tag candidates from the OSRS Wiki into item_tags.generated.json.

Each tag in ``tag_config.TAG_DEFINITIONS`` is resolved to a concrete list of F2P
item names by intersecting wiki categories (see tag_config for the model). The
output is a *candidate* file for human curation next stage — it is NOT the final
``item_tags.json`` (see docs/item-tags-schema.md and docs/integration-notes/lane-C.md).

Usage:
    python3 scripts/generate_item_tags.py [--refresh] [--no-cache]
"""

from __future__ import annotations

import argparse
import json
import os

from tag_config import FREE_TO_PLAY_CATEGORY, TAG_DEFINITIONS
from wikiclient import WikiClient

OUTPUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "out", "item_tags.generated.json"
)

# Titles that are category index/portal pages rather than real items. The
# cmtype=page + namespace-0 filter removes most cruft; these are the stragglers
# (a self-named index page inside a category, or a material category's landing
# page). NOTE: "Logs" is deliberately NOT here — the page titled "Logs" is the
# actual standard F2P logs item, so filtering it would drop a core item.
_INDEX_PAGE_TITLES = {
    "Food",
    "Runes",
    "Ores",
    "Seeds",
    "Tools",
    "Ammunition",
    "Bronze",
    "Iron",
    "Steel",
    "Black",
    "Mithril",
    "Adamant",
    "Rune",
}


# Parenthetical suffixes that mark a minigame/instance duplicate of a real item
# (e.g. "Bronze arrow (Last Man Standing)"). These are noise for gamemode tags —
# the base item is already present — so we drop them. This is deliberately a
# small, explicit denylist; genuine variants like "Cabbage (Draynor Manor)" stay.
_MINIGAME_SUFFIX_MARKERS = (
    "(Last Man Standing)",
    "(Deadman",
    "(Tutorial Island)",
    "(Nightmare Zone)",
    "(beta)",
    "(historical)",
)


def looks_like_real_item(title: str) -> bool:
    if ":" in title:  # namespaced (RuneScape:, Module:, ...) — not an item
        return False
    if title in _INDEX_PAGE_TITLES:
        return False
    if any(marker in title for marker in _MINIGAME_SUFFIX_MARKERS):
        return False
    return True


def resolve_tag(client: WikiClient, definition: dict, free_to_play_items: set) -> dict:
    """Resolve one tag definition to its item-name list via category intersection."""
    category_sets = [set(client.category_members(category)) for category in definition["all_of"]]
    included = set.intersection(*category_sets) if category_sets else set()
    included &= free_to_play_items  # F2P gamemode: drop members-only items
    for excluded_category in definition.get("none_of", []):
        included -= set(client.category_members(excluded_category))
    item_names = sorted(title for title in included if looks_like_real_item(title))
    return {
        "name": definition["name"],
        "tier": definition["tier"],
        "category": definition["category"],
        "itemNames": item_names,
        "notes": definition["notes"],
    }


def build_output(client: WikiClient) -> dict:
    free_to_play_items = set(client.category_members(FREE_TO_PLAY_CATEGORY))
    print(f"  {len(free_to_play_items)} free-to-play items in the base filter set.")

    tags = []
    for definition in TAG_DEFINITIONS:
        tag = resolve_tag(client, definition, free_to_play_items)
        print(f"  {tag['name']:24s} -> {len(tag['itemNames'])} items")
        tags.append(tag)

    total_item_slots = sum(len(tag["itemNames"]) for tag in tags)
    return {
        "comment": (
            "AUTO-GENERATED candidate item tags from the OSRS Wiki via "
            "scripts/generate_item_tags.py. NOT the final item_tags.json — this is "
            "input for human curation next stage. Item names (not ids) match the "
            "name-based convention of monsters.json. See docs/item-tags-schema.md."
        ),
        "source": "https://oldschool.runescape.wiki (category intersections, F2P only)",
        "tagCount": len(tags),
        "totalItemSlots": total_item_slots,
        "tags": tags,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="ignore cached responses and refetch")
    parser.add_argument("--no-cache", action="store_true", help="do not read or write the disk cache")
    arguments = parser.parse_args()

    client = WikiClient(use_cache=not arguments.no_cache, refresh=arguments.refresh)
    print("Generating item-tag candidates from the OSRS Wiki...")
    output = build_output(client)

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as output_file:
        json.dump(output, output_file, indent=2, ensure_ascii=False)
        output_file.write("\n")
    print(
        f"Wrote {OUTPUT_PATH}: {output['tagCount']} tags, "
        f"{output['totalItemSlots']} item slots ({client.stats_line()})."
    )


if __name__ == "__main__":
    main()
