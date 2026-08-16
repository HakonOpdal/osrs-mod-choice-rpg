"""Single entry point: run the full Lane C pipeline end-to-end.

Runs the monster verifier and the item-tag generator in sequence, producing:
  * scripts/out/monsters-report.md
  * scripts/out/item_tags.generated.json

Usage:
    python3 scripts/run_all.py [--refresh] [--no-cache]
"""

from __future__ import annotations

import argparse
import json
import os

import generate_item_tags
import verify_monsters
from wikiclient import WikiClient


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="ignore cached responses and refetch")
    parser.add_argument("--no-cache", action="store_true", help="do not read or write the disk cache")
    arguments = parser.parse_args()

    client = WikiClient(use_cache=not arguments.no_cache, refresh=arguments.refresh)

    # Ensure the output directory exists before the first write — the documented
    # end-to-end command must work from a clean/cleared out/ directory.
    os.makedirs(os.path.dirname(verify_monsters.OUTPUT_PATH), exist_ok=True)
    os.makedirs(os.path.dirname(generate_item_tags.OUTPUT_PATH), exist_ok=True)

    print("== [1/2] Verifying monsters.json ==")
    report = verify_monsters.build_report(client)
    with open(verify_monsters.OUTPUT_PATH, "w", encoding="utf-8") as report_file:
        report_file.write(report)
    print(f"   wrote {verify_monsters.OUTPUT_PATH}")

    print("== [2/2] Generating item-tag candidates ==")
    output = generate_item_tags.build_output(client)
    with open(generate_item_tags.OUTPUT_PATH, "w", encoding="utf-8") as output_file:
        json.dump(output, output_file, indent=2, ensure_ascii=False)
        output_file.write("\n")
    print(f"   wrote {generate_item_tags.OUTPUT_PATH}")

    print(f"\nDone. {client.stats_line()}.")


if __name__ == "__main__":
    main()
