#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_ROOT = ROOT / "docs/locomotives/diesel/tem2-family"
GLOBAL_MANIFEST = ROOT / "docs/locomotives/manifests/locomotive_families.json"


def load(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def fail(message: str):
    raise SystemExit(f"TEM2_STAGE1_FAIL: {message}")


def main():
    required = [
        FAMILY_ROOT / "family_manifest.json",
        FAMILY_ROOT / "common/source_registry.json",
        FAMILY_ROOT / "common/profile_contract.json",
        FAMILY_ROOT / "tem2/profile.json",
        FAMILY_ROOT / "tem2u/profile.json",
        GLOBAL_MANIFEST,
    ]
    missing = [str(p.relative_to(ROOT)) for p in required if not p.exists()]
    if missing:
        fail(f"missing files: {missing}")

    family = load(FAMILY_ROOT / "family_manifest.json")
    sources = load(FAMILY_ROOT / "common/source_registry.json")
    contract = load(FAMILY_ROOT / "common/profile_contract.json")
    tem2 = load(FAMILY_ROOT / "tem2/profile.json")
    tem2u = load(FAMILY_ROOT / "tem2u/profile.json")
    global_manifest = load(GLOBAL_MANIFEST)

    if family.get("familyId") != "tem2-family":
        fail("wrong familyId")
    if family.get("tractionType") != "diesel":
        fail("TEM2 family must be diesel")
    if family.get("migrationState") != "STAGE_1_FOUNDATION_COMPLETE":
        fail("family migrationState must be STAGE_1_FOUNDATION_COMPLETE")

    profiles = family.get("profiles") or []
    expected_profiles = {"tem2-base", "tem2u-improved"}
    actual_profiles = {p.get("profileId") for p in profiles}
    if actual_profiles != expected_profiles:
        fail(f"profile set mismatch: {actual_profiles}")

    for p in profiles:
        if p.get("completedStages") != ["FOUNDATION"]:
            fail(f"{p.get('profileId')} must contain only FOUNDATION at Stage 1")
        required_before = set(p.get("requiredBeforeIntegration") or [])
        expected_required = {"ATLAS_SCHEMES_INTERACTIVE", "DIAGNOSTICS_ASSISTANT_SEMANTICS", "ACCEPTANCE_CROSS_LAYER_QA"}
        if required_before != expected_required:
            fail(f"{p.get('profileId')} integration gates incomplete")

    by_id = {}
    for source in sources.get("sources") or []:
        sid = source.get("id")
        if not sid or sid in by_id:
            fail(f"invalid/duplicate source id: {sid}")
        by_id[sid] = source
        if not source.get("title") or not source.get("provenanceStatus") or not source.get("authorityForAction"):
            fail(f"source presentation/provenance incomplete: {sid}")

    if len(by_id) < 8:
        fail(f"source registry unexpectedly small: {len(by_id)}")

    current_sources = {sid for sid, src in by_id.items() if src.get("currentness") == "CURRENT_CONFIRMED"}
    required_current = {"TEM2-SRC-BRAKES-2026", "TEM2-SRC-POT-RZD-342-2025"}
    if not required_current.issubset(current_sources):
        fail("current brake/safety sources missing or not confirmed")

    for profile in (tem2, tem2u):
        if profile.get("stage") != "STAGE_1_FOUNDATION_COMPLETE":
            fail(f"{profile.get('profileId')} wrong stage")
        if profile.get("completedStages") != ["FOUNDATION"]:
            fail(f"{profile.get('profileId')} must not claim later stages")
        if profile.get("integrationState") != "FOUNDATION_ONLY_NOT_READY_FOR_APP_INTEGRATION":
            fail(f"{profile.get('profileId')} must not claim integration readiness")
        for ref in profile.get("evidenceRefs") or []:
            if ref not in by_id:
                fail(f"unresolved evidenceRef {ref} in {profile.get('profileId')}")

    tem2u_text = json.dumps(tem2u, ensure_ascii=False).upper()
    required_exclusions = ["TEM2UM", "TEM2T", "1PD-4A", "RHEOSTATIC/ELECTRODYNAMIC"]
    for token in required_exclusions:
        if token.upper() not in tem2u_text:
            fail(f"TEM2U exclusion missing: {token}")

    variant_traits = " ".join(tem2u.get("variantTraits") or []).upper()
    forbidden_traits = ["1PD-4A", "RHEOSTATIC", "ELECTRODYNAMIC BRAKE", "BRAKING RESISTOR"]
    for token in forbidden_traits:
        if token in variant_traits:
            fail(f"adjacent variant leakage into TEM2U traits: {token}")

    dimensions = {d.get("id") for d in contract.get("profileDimensions") or []}
    required_dimensions = {"series_execution", "diesel_execution", "electrical_execution", "brake_equipment_execution", "local_modernization"}
    if not required_dimensions.issubset(dimensions):
        fail(f"profile dimensions incomplete: {required_dimensions - dimensions}")

    families = {f.get("familyId"): f for f in global_manifest.get("families") or []}
    registered = families.get("tem2-family")
    if not registered:
        fail("tem2-family not registered in global manifest")
    if set(registered.get("profiles") or []) != expected_profiles:
        fail("global manifest TEM2 profiles mismatch")

    print("TEM2_STAGE1_FOUNDATION_PASS")
    print(f"profiles={len(expected_profiles)} sources={len(by_id)} current_sources={len(current_sources)} dimensions={len(dimensions)}")
    print("adjacent_variant_isolation=TEM2UM,TEM2T,TEM2A,LATER_TEM_FAMILY")


if __name__ == "__main__":
    main()
