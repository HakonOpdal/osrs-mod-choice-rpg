"""Single entry point: run the full Lane C pipeline end-to-end.

Runs the monster verifier and the item-tag generator in sequence, producing:
  * scripts/out/monsters-report.md
  * scripts/out/item_tags.generated.json

Usage:
    python3 scripts/run_all.py [--refresh] [--no-cache]
"""

from __future__ import annotations

import argparse

import generate_item_tags
import verify_monsters
from wikiclient import WikiClient


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="ignore cached responses and refetch")
    parser.add_argument("--no-cache", action="store_true", help="do not read or write the disk cache")
    arguments = parser.parse_args()

    client = WikiClient(use_cache=not arguments.no_cache, refresh=arguments.refresh)

    print("== [1/2] Verifying monsters.json ==")
    report = verify_monsters.build_report(client)
    with open(verify_monsters.OUTPUT_PATH, "w", encoding="utf-8") as report_file:
        report_file.write(report)
    print(f"   wrote {verify_monsters.OUTPUT_PATH}")

    print("== [2/2] Generating item-tag candidates ==")
    import json
    import os

    output = generate_item_tags.build_output(client)
    os.makedirs(os.path.dirname(generate_item_tags.OUTPUT_PATH), exist_ok=True)
    with open(generate_item_tags.OUTPUT_PATH, "w", encoding="utf-8") as output_file:
        json.dump(output, output_file, indent=2, ensure_ascii=False)
        output_file.write("\n")
    print(f"   wrote {generate_item_tags.OUTPUT_PATH}")

    print(f"\nDone. {client.stats_line()}.")


if __name__ == "__main__":
    main()
