#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "docs" / "locomotives" / "chme3"
PROFILE_ROOT = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family" / "chme3e"
FAMILY = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family" / "family_manifest.json"


def load(path: Path):
    if not path.exists():
        raise AssertionError(f"missing file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def unique(values, label: str):
    values = list(values)
    seen = set()
    dup = set()
    for value in values:
        if value in seen:
            dup.add(value)
        seen.add(value)
    if dup:
        raise AssertionError(f"duplicate {label}: {sorted(dup)}")
    return set(values)


def main() -> int:
    family = load(FAMILY)
    profile = load(PROFILE_ROOT / "profile.json")
    inheritance = load(PROFILE_ROOT / "inheritance.json")
    source1 = load(PROFILE_ROOT / "source_registry.json")
    source2 = load(PROFILE_ROOT / "source_registry_stage2.json")
    atlas = load(PROFILE_ROOT / "atlas_variant.json")
    cards_e = load(PROFILE_ROOT / "atlas_cards_electronic.json")
    cards_pr = load(PROFILE_ROOT / "atlas_cards_preheat_remote.json")
    schemes = load(PROFILE_ROOT / "schemes_variant.json")
    designations = load(PROFILE_ROOT / "scheme_designations.json")

    legacy_index = load(LEGACY / "atlas_index.json")
    legacy_extension = load(LEGACY / "atlas_index_extension.json")

    if profile.get("profileId") != "chme3e-electronic":
        raise AssertionError("wrong profile identity")
    if inheritance.get("migrationMode") != "BRIDGE_UNTIL_FAMILY_COMMON_IS_PHYSICALLY_MIGRATED":
        raise AssertionError("legacy/common migration bridge is not explicit")

    family_profiles = {row["profileId"]: row for row in family.get("profiles", [])}
    if "chme3e-electronic" not in family_profiles:
        raise AssertionError("CHME3E missing from family manifest")

    # Shared namespace is not 'all CHME3 files': only records explicitly applicable to ЧМЭ3Э.
    shared_rows = []
    for doc in (legacy_index, legacy_extension):
        for row in doc.get("equipment", []):
            if "ЧМЭ3Э" in row.get("applicability", []):
                shared_rows.append(row)
    shared_ids = unique((row["id"] for row in shared_rows), "inherited shared equipment IDs")

    common_systems = {row["id"] for row in legacy_index.get("systems", [])}
    if "CHME3-SYS-EDB" in {row.get("systemId") for row in shared_rows}:
        raise AssertionError("CHME3T EDB system leaked through inherited shared equipment")

    variant_system_rows = atlas.get("systems", [])
    variant_system_ids = unique((row["id"] for row in variant_system_rows), "variant system IDs")
    if len(variant_system_ids) != 3:
        raise AssertionError(f"expected 3 CHME3E variant systems, got {len(variant_system_ids)}")
    for row in variant_system_rows:
        if row.get("applicability") != ["ЧМЭ3Э"]:
            raise AssertionError(f"variant system not CHME3E-only: {row['id']}")

    variant_rows = atlas.get("equipment", [])
    variant_ids = unique((row["id"] for row in variant_rows), "variant equipment IDs")
    if len(variant_ids) != 33:
        raise AssertionError(f"expected 33 CHME3E variant equipment IDs, got {len(variant_ids)}")
    for row in variant_rows:
        if not row["id"].startswith("CHME3E-EQ-"):
            raise AssertionError(f"variant ID outside CHME3E namespace: {row['id']}")
        if row.get("applicability") != ["ЧМЭ3Э"]:
            raise AssertionError(f"variant equipment not CHME3E-only: {row['id']}")
        if row.get("systemId") not in common_systems | variant_system_ids:
            raise AssertionError(f"unknown system {row.get('systemId')} in {row['id']}")

    all_ids = shared_ids | variant_ids

    source_rows = source1.get("sources", []) + source2.get("sources", [])
    source_ids = unique((row["id"] for row in source_rows), "CHME3E evidence IDs")
    for row in source_rows:
        if row.get("authorityForAction") not in {"NO", "NO_BY_ITSELF"}:
            raise AssertionError(f"stage-2 evidence grants operational authority: {row['id']}")

    card_rows = cards_e.get("records", []) + cards_pr.get("records", [])
    card_ids = unique((row["id"] for row in card_rows), "detailed card IDs")
    if card_ids != variant_ids:
        missing = sorted(variant_ids - card_ids)
        extra = sorted(card_ids - variant_ids)
        raise AssertionError(f"variant card coverage mismatch: missing={missing}, extra={extra}")

    scheme_rows = schemes.get("records", [])
    scheme_ids = unique((row["id"] for row in scheme_rows), "variant scheme IDs")
    if len(scheme_ids) != 6:
        raise AssertionError(f"expected 6 CHME3E variant schemes, got {len(scheme_ids)}")

    for row in card_rows:
        for required in ("purpose", "normalState", "deviationSigns", "assistantTerms", "evidenceRefs", "schemeRefs"):
            if not row.get(required):
                raise AssertionError(f"card {row['id']} has no {required}")
        if row.get("systemId") not in common_systems | variant_system_ids:
            raise AssertionError(f"unknown card system {row.get('systemId')} in {row['id']}")
        for ref in row.get("relatedIds", []):
            if ref not in all_ids:
                raise AssertionError(f"dangling relatedId {ref} in {row['id']}")
        for ref in row.get("schemeRefs", []):
            if ref not in scheme_ids:
                raise AssertionError(f"unknown schemeRef {ref} in {row['id']}")
        for ref in row.get("evidenceRefs", []):
            if ref not in source_ids:
                raise AssertionError(f"unknown evidenceRef {ref} in {row['id']}")

    for row in scheme_rows:
        if row.get("applicability") != ["ЧМЭ3Э"]:
            raise AssertionError(f"variant scheme not CHME3E-only: {row['id']}")
        if row.get("actionAuthority") != "INFORMATION_ONLY":
            raise AssertionError(f"stage-2 scheme grants action authority: {row['id']}")
        for node in row.get("nodes", []):
            if node not in all_ids:
                raise AssertionError(f"unknown node {node} in {row['id']}")
        for flow in row.get("flows", []):
            if len(flow) < 2:
                raise AssertionError(f"malformed flow in {row['id']}: {flow}")
            if flow[0] not in all_ids or flow[1] not in all_ids:
                raise AssertionError(f"dangling flow in {row['id']}: {flow[:2]}")
        for ref in row.get("evidenceRefs", []):
            if ref not in source_ids:
                raise AssertionError(f"unknown scheme evidence {ref} in {row['id']}")

    designation_rows = designations.get("electrical", [])
    if len(designation_rows) != 29:
        raise AssertionError(f"expected 29 CHME3E designation rows, got {len(designation_rows)}")
    unique((row["designation"] for row in designation_rows), "scheme designations")
    for row in designation_rows:
        if row.get("equipmentId") not in variant_ids:
            raise AssertionError(f"designation points outside CHME3E variant Atlas: {row}")
    for ref in designations.get("evidenceRefs", []):
        if ref not in source_ids:
            raise AssertionError(f"unknown designation evidence {ref}")

    required_variant = {
        "CHME3E-EQ-CONTROLLER-HH106",
        "CHME3E-EQ-ELECTRONIC-REGULATOR-GC40P",
        "CHME3E-EQ-CONTACTOR-KOG1",
        "CHME3E-EQ-CONTACTOR-KOG2",
        "CHME3E-EQ-CONTACTOR-KOP",
        "CHME3E-EQ-CURRENT-SENSOR-DTG",
        "CHME3E-EQ-VOLTAGE-SENSOR-DNG",
        "CHME3E-EQ-HEATER-ELEMENTS-R85-R88",
        "CHME3E-EQ-ELECTRIC-WATER-PUMPS",
    }
    missing_required = sorted(required_variant - variant_ids)
    if missing_required:
        raise AssertionError(f"key CHME3E equipment missing: {missing_required}")

    hard_exclusions = set(inheritance.get("hardExclusions", []))
    required_exclusions = {
        "CHME3-SYS-EDB",
        "CHME3T-EQ-BRAKE-RESISTORS",
        "CHME3T-EQ-BRAKE-RESISTOR-FAN",
        "CHME3T-EQ-EDB-CONTROL",
    }
    if not required_exclusions.issubset(hard_exclusions):
        raise AssertionError("CHME3T rheostatic exclusions incomplete")
    active_refs = set()
    for row in variant_rows + card_rows:
        active_refs.add(row.get("systemId"))
        active_refs.update(row.get("relatedIds", []))
    for row in scheme_rows:
        active_refs.update(row.get("nodes", []))
        for flow in row.get("flows", []):
            active_refs.update(flow[:2])
    leaked = sorted(required_exclusions & active_refs)
    if leaked:
        raise AssertionError(f"CHME3T EDB content leaked into active CHME3E data: {leaked}")

    external = next((row for row in scheme_rows if row["id"] == "CHME3E-SCH-PREHEAT-EXTERNAL"), None)
    if not external or not external.get("safetyNote"):
        raise AssertionError("external three-phase preheat scheme lacks explicit safety boundary")

    print(
        "CHME3E STAGE 2 OK: "
        f"{len(shared_ids)} applicable shared equipment IDs + {len(variant_ids)} variant equipment, "
        f"{len(card_ids)}/{len(variant_ids)} detailed variant cards, {len(variant_system_ids)} variant systems, "
        f"{len(scheme_ids)} variant schemes, {len(designation_rows)} designations, "
        f"{len(source_ids)} profile evidence records; CHME3T EDB isolation PASS"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError) as exc:
        print(f"CHME3E STAGE 2 FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
