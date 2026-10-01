#!/usr/bin/env python3
"""Validate the isolated ChME3/ChME3T knowledge package.

This validator checks the data contract before Android integration. It treats the
Atlas index as the canonical namespace, detailed card files as one-card-per-ID
enrichments, and functional schemes as references to those canonical IDs.
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


def rows_from(document: dict, *keys: str):
    for key in keys:
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
    powerplant_cards = load("atlas_cards_powerplant.json")
    electric_brake_cards = load("atlas_cards_electric_brake.json")
    scheme_cards = load("atlas_cards_scheme_equipment.json")
    schemes = load("atlas_schemes_functional.json")
    designations = load("atlas_scheme_designations.json")
    id_contract = load("atlas_id_contract.json")

    source_rows = registry.get("sources", []) + registry_addendum.get("sources", [])
    source_ids = unique((row["id"] for row in source_rows), "source IDs")

    system_ids = unique((row["id"] for row in atlas.get("systems", [])), "system IDs")

    base_index_rows = atlas.get("equipment", [])
    extension_index_rows = atlas_extension.get("equipment", [])
    index_rows = base_index_rows + extension_index_rows
    equipment_ids = unique((row["id"] for row in index_rows), "canonical equipment IDs")

    canonical_map: dict[str, str] = {}
    alias_ids: set[str] = set()
    for mapping in id_contract.get("canonicalMappings", []):
        canonical_id = mapping["canonical"]
        canonical_map[canonical_id] = canonical_id
        for alias in mapping.get("aliases", []):
            if alias in canonical_map and canonical_map[alias] != canonical_id:
                raise AssertionError(f"ID alias maps to multiple canonicals: {alias}")
            canonical_map[alias] = canonical_id
            alias_ids.add(alias)

    def canonical(value: str) -> str:
        return canonical_map.get(value, value)

    def require_canonical_equipment(value: str, where: str):
        resolved = canonical(value)
        if resolved not in equipment_ids:
            raise AssertionError(f"dangling equipment reference {value!r} -> {resolved!r} in {where}")
        if value != resolved:
            raise AssertionError(f"legacy equipment ID {value!r} used in active data at {where}; use {resolved!r}")

    def require_source(value: str, where: str):
        if value not in source_ids:
            raise AssertionError(f"unknown evidence/source reference {value!r} in {where}")

    # Canonical index must never use migration aliases as record IDs.
    for row in index_rows:
        row_id = row["id"]
        if row_id in alias_ids:
            raise AssertionError(f"legacy ID is present in active atlas index: {row_id}")
        system_id = row.get("systemId")
        if system_id and system_id not in system_ids:
            raise AssertionError(f"unknown system {system_id!r} for {row_id}")

    # Detailed card IDs are unique across all active detail files and must already
    # exist in the canonical index or its explicit pass-2 extension.
    detail_sets = [
        ("atlas_cards_powerplant.json", rows_from(powerplant_cards, "records", "equipment")),
        ("atlas_cards_electric_brake.json", rows_from(electric_brake_cards, "records", "equipment")),
        ("atlas_cards_scheme_equipment.json", rows_from(scheme_cards, "records", "equipment")),
    ]
    detail_rows = []
    detail_origin: dict[str, str] = {}
    for filename, rows in detail_sets:
        unique((row["id"] for row in rows), f"card IDs inside {filename}")
        for row in rows:
            row_id = row["id"]
            if row_id in detail_origin:
                raise AssertionError(
                    f"equipment card {row_id} is defined in both {detail_origin[row_id]} and {filename}"
                )
            detail_origin[row_id] = filename
            detail_rows.append(row)

    for row in detail_rows:
        row_id = row["id"]
        require_canonical_equipment(row_id, f"card ID in {detail_origin[row_id]}")
        system_id = row.get("systemId")
        if system_id and system_id not in system_ids:
            raise AssertionError(f"unknown system {system_id!r} for card {row_id}")
        for ref in row.get("relatedIds", []):
            require_canonical_equipment(ref, row_id + ".relatedIds")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, row_id + ".evidenceRefs")

    # Functional scheme graph.
    scheme_rows = schemes.get("records", [])
    scheme_ids = unique((row["id"] for row in scheme_rows), "functional scheme IDs")
    for row in scheme_rows:
        for node in row.get("nodes", []):
            require_canonical_equipment(node, row["id"] + ".nodes")
        for edge in row.get("flows", []):
            if len(edge) < 2:
                raise AssertionError(f"malformed flow in {row['id']}: {edge}")
            require_canonical_equipment(edge[0], row["id"] + ".flows.from")
            require_canonical_equipment(edge[1], row["id"] + ".flows.to")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, row["id"] + ".evidenceRefs")

    # Cards may only link to known functional schemes.
    for row in detail_rows:
        for scheme_ref in row.get("schemeRefs", []):
            if scheme_ref not in scheme_ids:
                raise AssertionError(f"unknown scheme reference {scheme_ref!r} in {row['id']}.schemeRefs")

    # Scheme designation tables are separate from functional graphs, but their
    # equipment IDs and evidence still resolve through the same canonical namespace.
    for section_name in ("electrical", "pneumatic"):
        section = designations.get(section_name, {})
        for ref in section.get("evidenceRefs", []):
            require_source(ref, f"atlas_scheme_designations.{section_name}.evidenceRefs")
        for row in section.get("elements", []):
            require_canonical_equipment(
                row["equipmentId"],
                f"{section_name}.elements:{row.get('designation') or row.get('schemeNo')}",
            )
        for row in section.get("functionalLinks", []):
            require_canonical_equipment(row["from"], f"{section_name}.functionalLinks:{row['id']}.from")
            for target in row.get("to", []):
                require_canonical_equipment(target, f"{section_name}.functionalLinks:{row['id']}.to")

    for profile in profiles.get("profiles", []):
        for ref in profile.get("evidenceRefs", []):
            require_source(ref, f"profiles:{profile['id']}")

    for mapping in id_contract.get("canonicalMappings", []):
        require_canonical_equipment(mapping["canonical"], "atlas_id_contract.canonicalMappings")

    missing_reserved = sorted(
        canonical(value)
        for value in id_contract.get("reservedEquipmentIds", [])
        if canonical(value) not in equipment_ids
    )
    if missing_reserved:
        raise AssertionError(f"reserved scheme equipment still missing from atlas: {missing_reserved}")

    print(
        "ChME3 Atlas checkpoint OK: "
        f"{len(source_ids)} sources, "
        f"{len(system_ids)} systems, "
        f"{len(equipment_ids)} canonical equipment IDs, "
        f"{len(detail_rows)} detailed cards, "
        f"{len(scheme_ids)} functional schemes, "
        f"{sum(len(designations.get(name, {}).get('elements', [])) for name in ('electrical', 'pneumatic'))} scheme designations"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError) as exc:
        print(f"ChME3 package validation FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
