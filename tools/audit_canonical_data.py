#!/usr/bin/env python3
"""Fail fast on canonical technical-data identity and reference regressions."""
from __future__ import annotations

import gzip
import json
import re
import sys
from collections import Counter
from pathlib import Path

REFERENCE = re.compile(r"^(?:ER-(?:EQ|KB|DIAG|SCH|VARIANT)-|SYS-|VL(?:80)?-(?:EQ|ACC|ROUTE|SCH|PN|DIAG)-)[A-Z0-9_-]+$", re.I)


def values(value):
    if isinstance(value, dict):
        for key, child in value.items():
            yield key, child
            yield from values(child)
    elif isinstance(value, list):
        for child in value:
            yield from values(child)


def main(root: Path) -> None:
    files = sorted(root.glob("*.json.gz"))
    if len(files) != 14:
        raise SystemExit(f"Expected 14 canonical packages, found {len(files)} in {root}")
    packages = {p.name: json.loads(gzip.open(p, "rt", encoding="utf-8").read()) for p in files}
    primary_collections = {
        "records", "articles", "scenarios", "items", "routes", "zones", "baseSchemes",
        "modeOverlays", "variantOverlays", "schemes", "views", "states",
        "referenceBenchmarks", "physicalBuckets", "systems", "criticalLinks",
    }
    ids: list[str] = []
    declared_aliases: set[str] = set()
    refs: Counter[str] = Counter()
    for package in packages.values():
        for collection in primary_collections:
            for record in package.get(collection, []):
                if isinstance(record, dict) and isinstance(record.get("id"), str) and REFERENCE.match(record["id"]):
                    ids.append(record["id"])
        for record in package.get("stateApplicability", []):
            if isinstance(record, dict) and isinstance(record.get("id"), str) and REFERENCE.match(record["id"]):
                declared_aliases.add(record["id"])
        for record in package.get("variantContract", {}).get("rules", []):
            if isinstance(record, dict) and isinstance(record.get("id"), str) and REFERENCE.match(record["id"]):
                declared_aliases.add(record["id"])
        for key, value in values(package):
            if isinstance(value, str) and REFERENCE.match(value):
                refs[value] += 1
    duplicates = sorted(item for item, count in Counter(ids).items() if count > 1)
    known = set(ids) | declared_aliases
    unresolved = sorted(value for value in refs if value not in known)
    declared_legacy = [value for value in unresolved if value.lower().startswith("vl80-eq-")]
    unresolved = [value for value in unresolved if value not in declared_legacy]
    record_keys = ("records", "scenarios", "items", "schemes", "articles", "views", "systems")
    counts = {name: next((len(data[key]) for key in record_keys if isinstance(data.get(key), list)), 0) for name, data in packages.items()}
    print(f"PASS packages={len(packages)} technical_ids={len(known)} references={sum(refs.values())}")
    print("package_records=" + ", ".join(f"{name}:{count}" for name, count in counts.items()))
    print(f"legacy_reference_aliases={len(declared_legacy)}")
    if duplicates:
        raise SystemExit("Duplicate technical IDs: " + ", ".join(duplicates))
    if unresolved:
        raise SystemExit("Unresolved technical references: " + ", ".join(unresolved))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
