#!/usr/bin/env python3
"""Validate ChME3/ChME3T Atlas pass 2 before Android integration."""
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
    return json.loads(path.read_text(encoding="utf-8"))


def unique(values, label: str):
    values = list(values)
    dup = sorted({v for v in values if values.count(v) > 1})
    if dup:
        raise AssertionError(f"duplicate {label}: {dup}")
    return set(values)


def rows_from(document: dict):
    for key in ("records", "equipment"):
        value = document.get(key)
        if isinstance(value, list):
            return value
    return []


def main() -> int:
    registry = load("source_registry.json")
    registry_addendum = load("source_registry_addendum_atlas.json")
    profiles = load("profiles.json")
    atlas = load("atlas_index.json")
    atlas_extension = load("atlas_index_extension.json")
    id_contract = load("atlas_id_contract.json")
    designations = load("atlas_scheme_designations.json")

    card_files = [
        "atlas_cards_powerplant.json",
        "atlas_cards_electric_brake.json",
        "atlas_cards_scheme_equipment.json",
        "atlas_cards_running_auxiliary.json",
        "atlas_cards_engine_control_remaining.json",
    ]
    scheme_files = ["atlas_schemes_functional.json", "atlas_schemes_extension.json"]

    source_rows = registry.get("sources", []) + registry_addendum.get("sources", [])
    source_ids = unique((row["id"] for row in source_rows), "source IDs")
    system_ids = unique((row["id"] for row in atlas.get("systems", [])), "system IDs")
    index_rows = atlas.get("equipment", []) + atlas_extension.get("equipment", [])
    equipment_ids = unique((row["id"] for row in index_rows), "canonical equipment IDs")

    canonical_map = {}
    legacy_ids = set()
    for mapping in id_contract.get("canonicalMappings", []):
        target = mapping["canonical"]
        canonical_map[target] = target
        for alias in mapping.get("aliases", []):
            previous = canonical_map.get(alias)
            if previous and previous != target:
                raise AssertionError(f"ID alias maps to multiple canonicals: {alias}")
            canonical_map[alias] = target
            legacy_ids.add(alias)

    def canonical(value: str) -> str:
        return canonical_map.get(value, value)

    def require_equipment(value: str, where: str):
        resolved = canonical(value)
        if resolved not in equipment_ids:
            raise AssertionError(f"dangling equipment reference {value!r} -> {resolved!r} in {where}")
        if value != resolved:
            raise AssertionError(f"legacy equipment ID {value!r} used in active data at {where}; use {resolved!r}")

    def require_source(value: str, where: str):
        if value not in source_ids:
            raise AssertionError(f"unknown source {value!r} in {where}")

    for row in index_rows:
        if row["id"] in legacy_ids:
            raise AssertionError(f"legacy ID in canonical atlas index: {row['id']}")
        if row.get("systemId") not in system_ids:
            raise AssertionError(f"unknown system {row.get('systemId')!r} for {row['id']}")

    detail_rows = []
    origin = {}
    for filename in card_files:
        rows = rows_from(load(filename))
        unique((row["id"] for row in rows), f"card IDs inside {filename}")
        for row in rows:
            if row["id"] in origin:
                raise AssertionError(f"equipment card {row['id']} defined in both {origin[row['id']]} and {filename}")
            origin[row["id"]] = filename
            detail_rows.append(row)

    detail_ids = {row["id"] for row in detail_rows}
    missing = sorted(equipment_ids - detail_ids)
    extra = sorted(detail_ids - equipment_ids)
    if missing:
        raise AssertionError(f"canonical equipment without detailed card: {missing}")
    if extra:
        raise AssertionError(f"detailed cards outside canonical index: {extra}")

    scheme_rows = []
    scheme_origin = {}
    for filename in scheme_files:
        for row in load(filename).get("records", []):
            if row["id"] in scheme_origin:
                raise AssertionError(f"scheme {row['id']} defined in two files")
            scheme_origin[row["id"]] = filename
            scheme_rows.append(row)
    scheme_ids = {row["id"] for row in scheme_rows}

    for row in detail_rows:
        require_equipment(row["id"], f"card ID in {origin[row['id']]}")
        if row.get("systemId") not in system_ids:
            raise AssertionError(f"unknown system {row.get('systemId')!r} for card {row['id']}")
        for required in ("purpose", "evidenceRefs", "assistantTerms"):
            if not row.get(required):
                raise AssertionError(f"card {row['id']} has no {required}")
        for ref in row.get("relatedIds", []):
            require_equipment(ref, row["id"] + ".relatedIds")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, row["id"] + ".evidenceRefs")
        for ref in row.get("schemeRefs", []):
            if ref not in scheme_ids:
                raise AssertionError(f"unknown scheme {ref!r} in {row['id']}.schemeRefs")

    for row in scheme_rows:
        for node in row.get("nodes", []):
            require_equipment(node, row["id"] + ".nodes")
        for edge in row.get("flows", []):
            if len(edge) < 2:
                raise AssertionError(f"malformed flow in {row['id']}: {edge}")
            require_equipment(edge[0], row["id"] + ".flows.from")
            require_equipment(edge[1], row["id"] + ".flows.to")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, row["id"] + ".evidenceRefs")

    designation_count = 0
    for section_name in ("electrical", "pneumatic"):
        section = designations.get(section_name, {})
        for ref in section.get("evidenceRefs", []):
            require_source(ref, f"designation {section_name}")
        for row in section.get("elements", []):
            designation_count += 1
            require_equipment(row["equipmentId"], f"designation {section_name}")
        for row in section.get("functionalLinks", []):
            require_equipment(row["from"], row["id"] + ".from")
            for target in row.get("to", []):
                require_equipment(target, row["id"] + ".to")

    for profile in profiles.get("profiles", []):
        for ref in profile.get("evidenceRefs", []):
            require_source(ref, f"profile {profile['id']}")

    for mapping in id_contract.get("canonicalMappings", []):
        require_equipment(mapping["canonical"], "ID contract")

    missing_reserved = sorted(
        canonical(value)
        for value in id_contract.get("reservedEquipmentIds", [])
        if canonical(value) not in equipment_ids
    )
    if missing_reserved:
        raise AssertionError(f"reserved scheme equipment missing from Atlas: {missing_reserved}")

    print(
        "ChME3 Atlas checkpoint OK: "
        f"{len(source_ids)} sources, {len(system_ids)} systems, "
        f"{len(equipment_ids)} canonical equipment IDs, {len(detail_rows)} detailed cards, "
        f"{len(scheme_ids)} functional schemes, {designation_count} scheme designations"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError) as exc:
        print(f"ChME3 Atlas validation FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
