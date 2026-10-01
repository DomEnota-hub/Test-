#!/usr/bin/env python3
"""Validate ChME3 acceptance contract against Atlas, Diagnostics and source provenance."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "docs" / "locomotives" / "chme3"


def load(name: str):
    path = PKG / name
    if not path.exists():
        raise AssertionError(f"missing required file: {path.relative_to(ROOT)}")
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def unique(values, label: str):
    seen = set()
    duplicates = set()
    for value in values:
        if value in seen:
            duplicates.add(value)
        seen.add(value)
    if duplicates:
        raise AssertionError(f"duplicate {label}: {sorted(duplicates)}")
    return seen


def main() -> int:
    atlas_rows = []
    systems = set()
    for name in ("atlas_index.json", "atlas_index_extension.json"):
        data = load(name)
        systems.update(row["id"] for row in data.get("systems", []))
        rows = data.get("equipment", [])
        unique((row["id"] for row in rows), f"equipment IDs inside {name}")
        atlas_rows.extend(rows)
    equipment = {row["id"]: row for row in atlas_rows}
    if len(equipment) != 70:
        raise AssertionError(f"expected 70 canonical Atlas IDs, got {len(equipment)}")
    if not systems:
        systems = {row.get("systemId") for row in atlas_rows if row.get("systemId")}

    scenario_ids = set()
    for path in sorted(PKG.glob("diagnostics_*_pass3.json")):
        with path.open("r", encoding="utf-8") as fh:
            rows = json.load(fh).get("scenarios", [])
        scenario_ids.update(row["id"] for row in rows)
    if len(scenario_ids) != 56:
        raise AssertionError(f"expected 56 diagnostic scenarios, got {len(scenario_ids)}")

    source_rows = []
    for path in sorted(set(PKG.glob("*source_registry*.json"))):
        with path.open("r", encoding="utf-8") as fh:
            source_rows.extend(json.load(fh).get("sources", []))
    source_ids = unique((row["id"] for row in source_rows), "source IDs")

    contract = load("acceptance_contract_pass4.json")
    required = load("acceptance_required_pass4.json").get("items", [])
    required_ids = unique((row["id"] for row in required), "required acceptance IDs")
    if len(required) != 21:
        raise AssertionError(f"expected 21 baseline/profile acceptance items, got {len(required)}")
    common = [row for row in required if not row.get("profiles")]
    if len(common) != 20:
        raise AssertionError(f"expected 20 common baseline checks, got {len(common)}")

    for row in required:
        rid = row["id"]
        if row.get("phase") != "MANDATORY_CHECK":
            raise AssertionError(f"baseline item is not MANDATORY_CHECK: {rid}")
        if not row.get("check"):
            raise AssertionError(f"missing check text: {rid}")
        refs = row.get("sourceRefs", [])
        if not refs:
            raise AssertionError(f"baseline item without source: {rid}")
        for ref in refs:
            if ref not in source_ids:
                raise AssertionError(f"unknown source {ref} in {rid}")
        for eq in row.get("equipmentIds", []):
            if eq not in equipment:
                raise AssertionError(f"unknown equipment {eq} in {rid}")
        for diag in row.get("diagnosticIds", []):
            if diag not in scenario_ids:
                raise AssertionError(f"unknown diagnostic {diag} in {rid}")

    edb = next((row for row in required if row["id"] == "CHME3T-REQ-21"), None)
    if not edb or edb.get("profiles") != ["ЧМЭ3Т"] or edb.get("profileGate") != "chme3t-rheostatic":
        raise AssertionError("ChME3T EDB acceptance gate is missing or unsafe")

    brake_ids = {"CHME3-REQ-06", "CHME3-REQ-07", "CHME3-REQ-08", "CHME3-REQ-09", "CHME3-REQ-10"}
    for row in required:
        if row["id"] in brake_ids and "CHME3-SRC-BRAKE-RULES-2025-P83" not in row.get("sourceRefs", []):
            raise AssertionError(f"current 2026 brake rules missing from {row['id']}")

    phases = contract.get("phaseRules", {})
    if set(phases) != {"OUTSIDE", "ENGINE_ROOM", "CAB", "BRAKE_PNEUMATIC"}:
        raise AssertionError(f"unexpected acceptance phases: {sorted(phases)}")

    def phase_members(rule):
        included = {
            eid for eid, row in equipment.items()
            if row.get("systemId") in set(rule.get("systems", []))
        }
        included.update(rule.get("alsoEquipmentIds", []))
        included.difference_update(rule.get("excludeEquipmentIds", []))
        unknown = included - set(equipment)
        if unknown:
            raise AssertionError(f"phase references unknown equipment: {sorted(unknown)}")
        return included

    phase_sets = {name: phase_members(rule) for name, rule in phases.items()}
    covered = set().union(*phase_sets.values())
    missing = sorted(set(equipment) - covered)
    if missing:
        raise AssertionError(f"Atlas equipment missing from expanded acceptance: {missing}")

    routes = contract.get("routes", [])
    route_ids = unique((row["id"] for row in routes), "route IDs")
    expected_routes = {
        "CHME3-ROUTE-required", "CHME3-ROUTE-route_canonical", "CHME3-ROUTE-route_from_outside",
        "CHME3-ROUTE-route_from_cab", "CHME3-ROUTE-route_engine_room",
        "CHME3-ROUTE-route_running_gear", "CHME3-ROUTE-route_brakes"
    }
    if route_ids != expected_routes:
        raise AssertionError(f"route set mismatch: {sorted(route_ids)}")
    for route in routes:
        for phase in route.get("phaseOrder", []):
            if phase not in phases:
                raise AssertionError(f"route {route['id']} uses unknown phase {phase}")

    states = contract.get("sessionContract", {}).get("states", [])
    if states != ["NOT_CHECKED", "OK", "NOTE", "NOT_APPLICABLE"]:
        raise AssertionError(f"acceptance state contract drift: {states}")

    profiles = contract.get("profileRules", [])
    chme3t = next((row for row in profiles if row.get("profile") == "ЧМЭ3Т"), None)
    if not chme3t or "CHME3-SYS-EDB" not in chme3t.get("includeSystems", []):
        raise AssertionError("ChME3T profile does not include EDB")
    for profile in ("ЧМЭ3", "ЧМЭ3Э"):
        rule = next((row for row in profiles if row.get("profile") == profile), None)
        if not rule or "CHME3-SYS-EDB" not in rule.get("excludeSystems", []):
            raise AssertionError(f"{profile} does not explicitly exclude ChME3T EDB")

    # Deterministic generated acceptance IDs must be one-to-one with Atlas IDs.
    generated = {"CHME3-ACC-" + eid.removeprefix("CHME3-EQ-").removeprefix("CHME3T-EQ-") for eid in equipment}
    if len(generated) != len(equipment):
        raise AssertionError("generated acceptance ID collision")

    print(
        "ChME3 acceptance OK: "
        f"{len(required)} baseline/profile checks, {len(equipment)} expanded Atlas checks, "
        f"{len(routes)} routes, {len(scenario_ids)} diagnostic targets, {len(source_ids)} sources"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError) as exc:
        print(f"ChME3 acceptance validation FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
