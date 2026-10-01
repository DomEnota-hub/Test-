#!/usr/bin/env python3
"""Validate the isolated ChME3/ChME3T knowledge package.

The validator intentionally checks data contracts, not Android integration.
It is safe to run before the package is wired into the application.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "docs" / "locomotives" / "chme3"


def load(name: str):
    path = PKG / name
    if not path.exists():
        raise AssertionError(f"missing required package file: {path.relative_to(ROOT)}")
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def unique(values, label: str):
    values = list(values)
    duplicates = sorted({value for value in values if values.count(value) > 1})
    if duplicates:
        raise AssertionError(f"duplicate {label}: {duplicates}")
    return set(values)


def main() -> int:
    registry = load("source_registry.json")
    registry_addendum = load("source_registry_addendum_atlas.json")
    profiles = load("profiles.json")
    atlas = load("atlas_index.json")
    cards = load("atlas_cards_scheme_equipment.json")
    designations = load("atlas_scheme_designations.json")
    id_contract = load("atlas_id_contract.json")

    source_rows = registry.get("sources", []) + registry_addendum.get("sources", [])
    source_ids = unique((row["id"] for row in source_rows), "source IDs")

    system_ids = unique((row["id"] for row in atlas.get("systems", [])), "system IDs")
    indexed_equipment = atlas.get("equipment", [])
    card_equipment = cards.get("equipment", [])
    equipment_rows = indexed_equipment + card_equipment
    equipment_ids = unique((row["id"] for row in equipment_rows), "equipment IDs")

    canonical_map: dict[str, str] = {}
    for mapping in id_contract.get("canonicalMappings", []):
        canonical = mapping["canonical"]
        canonical_map[canonical] = canonical
        for alias in mapping.get("aliases", []):
            if alias in canonical_map and canonical_map[alias] != canonical:
                raise AssertionError(f"ID alias maps to multiple canonicals: {alias}")
            canonical_map[alias] = canonical

    def canonical(value: str) -> str:
        return canonical_map.get(value, value)

    def require_equipment(value: str, where: str):
        resolved = canonical(value)
        if resolved not in equipment_ids:
            raise AssertionError(f"dangling equipment reference {value!r} -> {resolved!r} in {where}")

    def require_source(value: str, where: str):
        if value not in source_ids:
            raise AssertionError(f"unknown evidence/source reference {value!r} in {where}")

    # Atlas rows must point to a declared system; aliases are allowed to be empty.
    for row in equipment_rows:
        system_id = row.get("systemId")
        if system_id and system_id not in system_ids:
            raise AssertionError(f"unknown system {system_id!r} for {row['id']}")
        for ref in row.get("relatedIds", []):
            require_equipment(ref, row["id"] + ".relatedIds")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, row["id"] + ".evidenceRefs")

    # Every scheme designation and functional edge must resolve to canonical atlas IDs.
    for section_name in ("electrical", "pneumatic"):
        section = designations.get(section_name, {})
        for ref in section.get("evidenceRefs", []):
            require_source(ref, f"atlas_scheme_designations.{section_name}.evidenceRefs")
        for row in section.get("elements", []):
            require_equipment(row["equipmentId"], f"{section_name}.elements:{row.get('designation') or row.get('schemeNo')}")
        for row in section.get("functionalLinks", []):
            require_equipment(row["from"], f"{section_name}.functionalLinks:{row['id']}.from")
            for target in row.get("to", []):
                require_equipment(target, f"{section_name}.functionalLinks:{row['id']}.to")

    # Profiles must never reference a source that is absent from the package registries.
    for profile in profiles.get("profiles", []):
        for ref in profile.get("evidenceRefs", []):
            require_source(ref, f"profiles:{profile['id']}")

    # All canonical IDs declared as migrations must actually exist by checkpoint 2.
    for mapping in id_contract.get("canonicalMappings", []):
        require_equipment(mapping["canonical"], "atlas_id_contract.canonicalMappings")

    # Reserved IDs are a checklist, not a second namespace: by checkpoint 2 they must exist.
    missing_reserved = sorted(
        canonical(value)
        for value in id_contract.get("reservedEquipmentIds", [])
        if canonical(value) not in equipment_ids
    )
    if missing_reserved:
        raise AssertionError(f"reserved scheme equipment still missing from atlas: {missing_reserved}")

    print(
        "ChME3 package OK: "
        f"{len(source_ids)} sources, "
        f"{len(system_ids)} systems, "
        f"{len(equipment_ids)} equipment IDs, "
        f"{sum(len(designations.get(name, {}).get('elements', [])) for name in ('electrical', 'pneumatic'))} scheme designations"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError) as exc:
        print(f"ChME3 package validation FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
