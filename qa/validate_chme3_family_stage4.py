#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "docs" / "locomotives" / "chme3"
FAMILY = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family"
COMMON = FAMILY / "common"
CHME3E = FAMILY / "chme3e"


def load(path: Path):
    if not path.exists():
        raise AssertionError(f"missing file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def unique(values, label: str) -> set[str]:
    seen: set[str] = set()
    dup: set[str] = set()
    for value in values:
        if not value:
            raise AssertionError(f"empty {label}")
        if value in seen:
            dup.add(value)
        seen.add(value)
    if dup:
        raise AssertionError(f"duplicate {label}: {sorted(dup)}")
    return seen


def collect_sources() -> tuple[set[str], dict[str, dict]]:
    paths = sorted(LEGACY.glob("*source_registry*.json")) + [
        CHME3E / "source_registry.json",
        CHME3E / "source_registry_stage2.json",
    ]
    by_id: dict[str, dict] = {}
    for path in paths:
        for row in load(path).get("sources", []):
            sid = row.get("id")
            if not sid:
                raise AssertionError(f"source without id in {path.relative_to(ROOT)}")
            if sid in by_id:
                # Parent/reference records can legitimately be reused by family layers only if identical in identity.
                previous = by_id[sid]
                if previous.get("title") != row.get("title"):
                    raise AssertionError(f"conflicting duplicate source {sid}")
                continue
            by_id[sid] = row
    return set(by_id), by_id


def collect_legacy_equipment() -> list[dict]:
    rows: list[dict] = []
    for name in ("atlas_index.json", "atlas_index_extension.json"):
        rows.extend(load(LEGACY / name).get("equipment", []))
    ids = unique((r.get("id") for r in rows), "legacy equipment IDs")
    if len(ids) != 70:
        raise AssertionError(f"expected 70 legacy Atlas equipment IDs, got {len(ids)}")
    return rows


def collect_legacy_diagnostics() -> tuple[set[str], set[str]]:
    source_ids: set[str] = set()
    for path in sorted(LEGACY.glob("diagnostics_*_pass3.json")):
        for row in load(path).get("scenarios", []):
            sid = row.get("id")
            if sid in source_ids:
                raise AssertionError(f"duplicate legacy diagnostic {sid}")
            source_ids.add(sid)
    runtime_ids: set[str] = set()
    for path in sorted(LEGACY.glob("diagnostic_runtime_graphs_v2*.json")):
        for row in load(path).get("scenarios", []):
            sid = row.get("scenarioId")
            if sid in runtime_ids:
                raise AssertionError(f"duplicate legacy runtime graph {sid}")
            runtime_ids.add(sid)
    if len(source_ids) != 56 or source_ids != runtime_ids:
        raise AssertionError(f"legacy diagnostic/runtime mismatch: source={len(source_ids)}, runtime={len(runtime_ids)}")
    return source_ids, runtime_ids


def main() -> int:
    manifest = load(COMMON / "acceptance_stage4_manifest.json")
    if manifest.get("stage") != "STAGE_4_ACCEPTANCE_CROSS_LAYER_QA":
        raise AssertionError("wrong Stage 4 manifest stage")

    states = manifest.get("sessionContract", {}).get("states", [])
    if states != ["NOT_CHECKED", "OK", "NOTE", "NOT_APPLICABLE"]:
        raise AssertionError(f"family session-state contract drift: {states}")

    legacy_contract = load(LEGACY / "acceptance_contract_pass4.json")
    legacy_required = load(LEGACY / "acceptance_required_pass4.json").get("items", [])
    if len(legacy_required) != 21:
        raise AssertionError(f"legacy acceptance item count drift: {len(legacy_required)}")
    common_required = [r for r in legacy_required if not r.get("profiles") and not r.get("profileGate")]
    if len(common_required) != 20:
        raise AssertionError(f"expected 20 inherited common acceptance items, got {len(common_required)}")
    edb_required = [r for r in legacy_required if r.get("id") == "CHME3T-REQ-21"]
    if len(edb_required) != 1 or edb_required[0].get("profileGate") != "chme3t-rheostatic":
        raise AssertionError("legacy ChME3T EDB acceptance gate missing")

    route_ids = unique((r.get("id") for r in legacy_contract.get("routes", [])), "legacy acceptance route IDs")
    if len(route_ids) != 7:
        raise AssertionError(f"expected 7 inherited routes, got {len(route_ids)}")

    legacy_equipment = collect_legacy_equipment()
    legacy_by_id = {r["id"]: r for r in legacy_equipment}
    chme3e_shared = {
        r["id"] for r in legacy_equipment
        if "ЧМЭ3Э" in r.get("applicability", [])
    }
    if len(chme3e_shared) != 67:
        raise AssertionError(f"expected 67 shared equipment IDs applicable to ChME3E, got {len(chme3e_shared)}")
    if any(eid.startswith("CHME3T-") for eid in chme3e_shared):
        raise AssertionError("ChME3T-specific equipment leaked into ChME3E shared equipment")

    variant_doc = load(CHME3E / "atlas_variant.json")
    variant_rows = variant_doc.get("equipment", [])
    variant_ids = unique((r.get("id") for r in variant_rows), "ChME3E variant equipment IDs")
    if len(variant_ids) != 33:
        raise AssertionError(f"expected 33 ChME3E variant equipment IDs, got {len(variant_ids)}")
    if chme3e_shared & variant_ids:
        raise AssertionError("shared/variant ChME3E equipment ID collision")
    if any(r.get("applicability") != ["ЧМЭ3Э"] for r in variant_rows):
        raise AssertionError("ChME3E variant equipment has non-exclusive applicability")
    combined = chme3e_shared | variant_ids
    if len(combined) != 100:
        raise AssertionError(f"expected 100 effective ChME3E equipment IDs, got {len(combined)}")

    source_ids, source_meta = collect_sources()
    legacy_diag, legacy_runtime = collect_legacy_diagnostics()

    chme3e_diag_rows = load(CHME3E / "diagnostics_stage3.json").get("scenarios", [])
    chme3e_diag_ids = unique((r.get("id") for r in chme3e_diag_rows), "ChME3E diagnostic IDs")
    if len(chme3e_diag_ids) != 12:
        raise AssertionError(f"expected 12 ChME3E diagnostics, got {len(chme3e_diag_ids)}")
    chme3e_runtime_rows = load(CHME3E / "diagnostic_runtime_graphs_v2.json").get("scenarios", [])
    chme3e_runtime_ids = unique((r.get("scenarioId") for r in chme3e_runtime_rows), "ChME3E runtime IDs")
    if chme3e_runtime_ids != chme3e_diag_ids:
        raise AssertionError("ChME3E diagnostic/runtime coverage mismatch")

    acceptance = load(CHME3E / "acceptance_stage4.json")
    if acceptance.get("stage") != "STAGE_4_ACCEPTANCE_CROSS_LAYER_QA":
        raise AssertionError("wrong ChME3E acceptance stage")
    if acceptance.get("inheritance", {}).get("inheritCommonRequiredItems") != 20:
        raise AssertionError("ChME3E does not inherit exactly 20 common required items")
    if "CHME3T-REQ-21" not in acceptance.get("inheritance", {}).get("excludeRequiredItemIds", []):
        raise AssertionError("ChME3E does not explicitly exclude ChME3T EDB required item")
    if acceptance.get("sessionContract", {}).get("states") != states:
        raise AssertionError("ChME3E session states differ from family contract")

    extended = acceptance.get("profileExtendedChecks", [])
    extended_ids = unique((r.get("id") for r in extended), "ChME3E profile acceptance IDs")
    if len(extended_ids) != 4:
        raise AssertionError(f"expected 4 ChME3E profile-extended checks, got {len(extended_ids)}")
    linked_diags: set[str] = set()
    linked_equipment: set[str] = set()
    for row in extended:
        rid = row["id"]
        if row.get("status") != "PROFILE_EXTENDED_CHECK":
            raise AssertionError(f"{rid}: profile check incorrectly marked mandatory/other")
        if not row.get("check") or not row.get("actionBoundary"):
            raise AssertionError(f"{rid}: missing check/actionBoundary")
        refs = row.get("sourceRefs", [])
        if not refs:
            raise AssertionError(f"{rid}: missing provenance")
        for ref in refs:
            if ref not in source_ids:
                raise AssertionError(f"{rid}: unknown source {ref}")
        for eid in row.get("equipmentIds", []):
            if eid not in variant_ids:
                raise AssertionError(f"{rid}: profile-extended check references non-variant/unknown equipment {eid}")
            linked_equipment.add(eid)
        for sid in row.get("diagnosticIds", []):
            if sid not in chme3e_diag_ids or sid not in chme3e_runtime_ids:
                raise AssertionError(f"{rid}: diagnostic link is not promoted ChME3E runtime content: {sid}")
            linked_diags.add(sid)
    if linked_diags != chme3e_diag_ids:
        raise AssertionError(f"profile acceptance does not link all 12 ChME3E diagnostics; missing={sorted(chme3e_diag_ids-linked_diags)}")

    # Training/reference evidence may define what to observe, but Stage 4 must not silently call it network-mandatory.
    if "not declared a universal network-wide mandatory" not in acceptance.get("requirednessPolicy", {}).get("profileExtended", ""):
        raise AssertionError("profile-extended requiredness boundary is missing")

    phase_rules = acceptance.get("phaseRules", {})
    if set(phase_rules) != {"OUTSIDE", "ENGINE_ROOM", "CAB", "BRAKE_PNEUMATIC"}:
        raise AssertionError(f"unexpected ChME3E phase set: {sorted(phase_rules)}")
    variant_by_id = {r["id"]: r for r in variant_rows}
    covered: set[str] = set()
    for phase, rule in phase_rules.items():
        systems = set(rule.get("systems", []))
        ids = {eid for eid, row in variant_by_id.items() if row.get("systemId") in systems}
        ids.update(rule.get("alsoEquipmentIds", []))
        ids.difference_update(rule.get("excludeEquipmentIds", []))
        unknown = ids - variant_ids
        if unknown:
            raise AssertionError(f"{phase}: unknown variant acceptance equipment {sorted(unknown)}")
        covered.update(ids)
    if covered != variant_ids:
        raise AssertionError(f"ChME3E variant expanded acceptance mismatch; missing={sorted(variant_ids-covered)}, extra={sorted(covered-variant_ids)}")

    expanded = acceptance.get("expandedVariantItems", {})
    if expanded.get("expectedEquipmentCount") != 33:
        raise AssertionError("ChME3E expanded acceptance expected count drift")
    generated = {"CHME3E-ACC-" + eid.removeprefix("CHME3E-EQ-") for eid in variant_ids}
    if len(generated) != 33:
        raise AssertionError("ChME3E deterministic acceptance ID collision")

    inherited_routes = set(acceptance.get("routes", {}).get("inheritLegacyRouteIds", []))
    if inherited_routes != route_ids:
        raise AssertionError(f"ChME3E route inheritance mismatch: {sorted(inherited_routes ^ route_ids)}")

    # Explicit profile-isolation guard across all Stage 4 active references.
    active_blob = json.dumps(acceptance, ensure_ascii=False)
    forbidden = ["CHME3T-EQ-BRAKE-RESISTORS", "CHME3T-EQ-BRAKE-RESISTOR-FAN", "CHME3T-EQ-EDB-CONTROL", "CHME3-DIAG-110"]
    for token in forbidden:
        if token in active_blob:
            # CHME3T-REQ-21 is allowed only as an explicit excluded acceptance ID, not active equipment/diagnostic content.
            raise AssertionError(f"ChME3T EDB content leaked into active ChME3E Stage 4 layer: {token}")

    # The profile extensions are expected to touch most electronic/preheat/remote architecture while deterministic expanded acceptance covers all 33.
    if len(linked_equipment) < 25:
        raise AssertionError(f"profile-extended ChME3E acceptance is too shallow: only {len(linked_equipment)} equipment links")

    profile_entry = next((r for r in manifest.get("profiles", []) if r.get("profileId") == "chme3e-electronic"), None)
    if not profile_entry:
        raise AssertionError("family Stage 4 manifest has no ChME3E profile")
    if profile_entry.get("expectedSharedApplicableEquipment") != len(chme3e_shared):
        raise AssertionError("family manifest shared ChME3E count drift")
    if profile_entry.get("expectedVariantEquipment") != len(variant_ids):
        raise AssertionError("family manifest variant ChME3E count drift")
    if profile_entry.get("expectedExpandedEquipment") != len(combined):
        raise AssertionError("family manifest effective ChME3E acceptance count drift")

    print(
        "CHME3 FAMILY STAGE 4 OK: "
        f"legacy required=20 common + 1 ChME3T profile, routes={len(route_ids)}; "
        f"ChME3E required=20 inherited + {len(extended)} profile-extended, expanded={len(chme3e_shared)} shared + {len(variant_ids)} variant = {len(combined)}; "
        f"diagnostic links={len(linked_diags)}/12 promoted, variant coverage={len(covered)}/33, sources={len(source_ids)}; EDB isolation PASS"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError, StopIteration) as exc:
        print(f"CHME3 FAMILY STAGE 4 FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
