#!/usr/bin/env python3
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FAMILY = ROOT / "docs/locomotives/diesel/tem2-family"
GLOBAL_MANIFEST = ROOT / "docs/locomotives/manifests/locomotive_families.json"
FAMILY_MANIFEST = FAMILY / "family_manifest.json"
SOURCES = FAMILY / "common/source_registry.json"
STAGE2 = FAMILY / "common/stage2_model.json"
STAGE3 = FAMILY / "common/stage3_model.json"
STAGE4 = FAMILY / "common/stage4_acceptance.json"

PROFILES = {"tem2-base", "tem2u-improved"}
PHASES = {"OUTSIDE", "ENGINE_ROOM", "CAB", "BRAKE_PNEUMATIC"}
EXPECTED_STAGES = [
    "FOUNDATION",
    "ATLAS_SCHEMES_INTERACTIVE",
    "DIAGNOSTICS_ASSISTANT_SEMANTICS",
    "ACCEPTANCE_CROSS_LAYER_QA",
]
FINAL_STATE = "KNOWLEDGE_FOUNDATION_COMPLETE_READY_FOR_APP_INTEGRATION"


def fail(message: str):
    raise SystemExit(f"TEM2_STAGE4_FAIL: {message}")


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def stage2_index(data):
    equipment = {}
    for row in data.get("equipment") or []:
        if not isinstance(row, list) or len(row) < 5:
            fail("invalid Stage 2 equipment row")
        eid, title, system_id, profiles, source_groups = row[:5]
        if eid in equipment:
            fail(f"duplicate Stage 2 equipment ID {eid}")
        equipment[eid] = {
            "title": title,
            "system": system_id,
            "profiles": set(profiles),
            "sourceGroups": set(source_groups),
        }
    systems = {row[0] for row in data.get("systems") or [] if isinstance(row, list) and row}
    schemes = {row.get("id") for row in data.get("schemes") or [] if isinstance(row, dict)}
    if len(equipment) != 36:
        fail(f"Stage 2 equipment count drifted: {len(equipment)} != 36")
    if len(systems) != 9:
        fail(f"Stage 2 system count drifted: {len(systems)} != 9")
    if len(schemes) != 9:
        fail(f"Stage 2 scheme count drifted: {len(schemes)} != 9")
    coverage = Counter()
    for item in equipment.values():
        if not item["profiles"] or not item["profiles"].issubset(PROFILES):
            fail(f"Stage 2 equipment has invalid profile set: {item['profiles']}")
        for profile in item["profiles"]:
            coverage[profile] += 1
    expected = {"tem2-base": 33, "tem2u-improved": 35}
    if dict(coverage) != expected:
        fail(f"Stage 2 effective equipment coverage drifted: {dict(coverage)}")
    return equipment, systems, schemes, expected


def source_index(data):
    result = {}
    for source in data.get("sources") or []:
        sid = str(source.get("id") or "").strip()
        if not sid or sid in result:
            fail(f"missing/duplicate source ID: {sid!r}")
        result[sid] = source
    return result


def scenario_index(data, equipment, schemes):
    scenarios = {}
    profile_shape = Counter()
    for scenario in data.get("scenarios") or []:
        sid = str(scenario.get("id") or "").strip()
        if not sid or sid in scenarios:
            fail(f"missing/duplicate Stage 3 scenario ID {sid!r}")
        profiles = set(scenario.get("profileIds") or [])
        if not profiles or not profiles.issubset(PROFILES):
            fail(f"{sid}: invalid profile set {profiles}")
        refs = set(scenario.get("equipmentIds") or [])
        if not refs or not refs.issubset(equipment):
            fail(f"{sid}: unresolved equipment refs {sorted(refs - set(equipment))}")
        for profile in profiles:
            if not any(profile in equipment[eid]["profiles"] for eid in refs):
                fail(f"{sid}: no equipment applicable to {profile}")
        scheme_refs = set(scenario.get("schemeRefs") or [])
        if not scheme_refs.issubset(schemes):
            fail(f"{sid}: unresolved scheme refs {sorted(scheme_refs - schemes)}")
        graph = scenario.get("runtimeGraph") or {}
        if not graph.get("startNodeId") or not graph.get("nodes"):
            fail(f"{sid}: runtime-v2 graph missing")
        scenarios[sid] = {"profiles": profiles, "equipment": refs}
        profile_shape[tuple(sorted(profiles))] += 1

    if len(scenarios) != 24:
        fail(f"Stage 3 scenario count drifted: {len(scenarios)} != 24")
    if profile_shape[("tem2-base", "tem2u-improved")] != 22:
        fail(f"expected 22 common scenarios, got {profile_shape[(('tem2-base', 'tem2u-improved'))]}")
    if profile_shape[("tem2u-improved",)] != 2:
        fail(f"expected 2 TEM2U-only scenarios, got {profile_shape[(('tem2u-improved',))]}")
    if any("tem2-base" in profiles and len(profiles) == 1 for profiles in profile_shape):
        fail("unexpected TEM2-base-only Stage 3 scenario")
    return scenarios


def validate_manifest(family_manifest, global_manifest):
    if family_manifest.get("familyId") != "tem2-family":
        fail("family manifest identity drifted")
    if family_manifest.get("migrationState") != FINAL_STATE:
        fail(f"family migrationState must be {FINAL_STATE}")
    if family_manifest.get("nextStage") != "APP_INTEGRATION":
        fail("family nextStage must be APP_INTEGRATION")

    profiles = family_manifest.get("profiles") or []
    if {row.get("profileId") for row in profiles} != PROFILES:
        fail("family manifest profile set drifted")
    for row in profiles:
        if row.get("supportState") != FINAL_STATE:
            fail(f"{row.get('profileId')}: supportState is not final foundation state")
        if row.get("completedStages") != EXPECTED_STAGES:
            fail(f"{row.get('profileId')}: completedStages drifted")
        if row.get("requiredBeforeIntegration") not in ([], None):
            fail(f"{row.get('profileId')}: requiredBeforeIntegration must be empty after Stage 4")

    boundary = family_manifest.get("integrationBoundary") or {}
    required_true = (
        "foundationReady",
        "atlasSchemesInteractiveReady",
        "diagnosticsAssistantSemanticsReady",
        "acceptanceCrossLayerReady",
        "profileContextRegistered",
    )
    for key in required_true:
        if boundary.get(key) is not True:
            fail(f"integrationBoundary.{key} must be true")
    for key in ("androidRuntimeIntegrated", "assistantEngineMerged"):
        if boundary.get(key) is not False:
            fail(f"integrationBoundary.{key} must remain false before app integration")

    families = {row.get("familyId"): row for row in global_manifest.get("families") or []}
    global_tem2 = families.get("tem2-family")
    if not global_tem2:
        fail("global manifest lost tem2-family")
    if set(global_tem2.get("profiles") or []) != PROFILES:
        fail("global manifest TEM2 profile set drifted")
    if global_tem2.get("migrationState") != FINAL_STATE:
        fail("global manifest TEM2 state is stale")


def validate_stage4(stage4, equipment, systems, coverage, sources, scenarios):
    if stage4.get("schemaVersion") != 4 or stage4.get("familyId") != "tem2-family":
        fail("Stage 4 identity/schema drifted")
    if stage4.get("stage") != "STAGE_4_ACCEPTANCE_CROSS_LAYER_QA":
        fail("Stage 4 marker drifted")
    if set(stage4.get("profiles") or []) != PROFILES:
        fail("Stage 4 profiles drifted")

    session = stage4.get("sessionContract") or {}
    if session.get("states") != ["NOT_CHECKED", "OK", "NOTE", "NOT_APPLICABLE"]:
        fail("acceptance session states drifted")
    for key in ("routeStateShared", "notesPerPhysicalItem", "resumeSupported", "routeSwitchKeepsSession"):
        if session.get(key) is not True:
            fail(f"acceptance session contract missing {key}")

    policy = stage4.get("scopePolicy") or {}
    expected_policy = {
        "networkWideMandatoryClaimAllowed": False,
        "localApprovedProcessMayExtendScope": True,
        "currentBrakeRulesTakePriorityFrom": "2026-07-01",
        "historicalFactoryManualsAreCurrentActionAuthority": False,
        "actualInstalledEquipmentOverridesSeriesAssumption": True,
        "unknownExecution": "FAIL_CLOSED",
        "adjacentVariantInheritance": "DENY",
    }
    for key, value in expected_policy.items():
        if policy.get(key) != value:
            fail(f"scope policy drift: {key}={policy.get(key)!r}, expected {value!r}")

    routes = stage4.get("routes") or []
    if len(routes) != 2:
        fail("Stage 4 must have exactly two entry routes")
    route_by_id = {row.get("id"): row for row in routes}
    outside = route_by_id.get("TEM2-ACC-ROUTE-OUTSIDE-FIRST")
    cab = route_by_id.get("TEM2-ACC-ROUTE-CAB-FIRST")
    if not outside or outside.get("phases", [None])[0] != "OUTSIDE":
        fail("outside-first route missing")
    if not cab or cab.get("phases", [None])[0] != "CAB":
        fail("cab-first route missing")
    for route in routes:
        phases = route.get("phases") or []
        if set(phases) != PHASES or len(phases) != len(PHASES):
            fail(f"{route.get('id')}: route phases incomplete/duplicated")

    expanded = stage4.get("expandedAcceptance") or {}
    if expanded.get("mode") != "ATLAS_FILTER_BY_PROFILE":
        fail("expanded acceptance must derive deterministically from Stage 2 Atlas")
    if expanded.get("expectedEquipmentCountByProfile") != coverage:
        fail("expanded acceptance profile counts do not match Stage 2")
    system_phase = expanded.get("systemPhaseMap") or {}
    if set(system_phase) != systems:
        fail(f"systemPhaseMap does not cover Stage 2 systems: missing={sorted(systems - set(system_phase))}")
    if any(phase not in PHASES for phase in system_phase.values()):
        fail("invalid acceptance navigation phase")
    overrides = expanded.get("phaseOverrides") or {}
    if not set(overrides).issubset(equipment):
        fail(f"phase override references unknown equipment {sorted(set(overrides) - set(equipment))}")
    if any(phase not in PHASES for phase in overrides.values()):
        fail("invalid phase override")

    authored = (stage4.get("coreChecks") or []) + (stage4.get("profileChecks") or [])
    if len(stage4.get("coreChecks") or []) != 14 or len(stage4.get("profileChecks") or []) != 2:
        fail("expected 14 shared core checks + 2 TEM2U profile checks")
    ids = [row.get("id") for row in authored]
    if any(not item for item in ids) or len(ids) != len(set(ids)):
        fail("missing/duplicate acceptance check IDs")

    unsafe_imperative = re.compile(
        r"(?:установить|поставить|замкнуть|зашунтировать|перемкнуть|заклинить|подпереть|принудительно\s+включить)\s+(?:провод|перемыч|контактор|реле|аппарат|защит)",
        re.I,
    )
    active_coverage = {profile: {eid for eid, item in equipment.items() if profile in item["profiles"]} for profile in PROFILES}
    direct_diagnostic_links = set()

    for check in authored:
        cid = check["id"]
        profiles = set(check.get("profiles") or [])
        if not profiles or not profiles.issubset(PROFILES):
            fail(f"{cid}: invalid profile set {profiles}")
        if cid.startswith("TEM2U-") and profiles != {"tem2u-improved"}:
            fail(f"{cid}: TEM2U profile check leaked into TEM2")
        if cid.startswith("TEM2-ACC-") and profiles != PROFILES:
            fail(f"{cid}: shared core check is not shared")
        if check.get("phase") not in PHASES:
            fail(f"{cid}: invalid phase")
        if not all(str(check.get(key) or "").strip() for key in ("title", "scopeStatus", "checkType", "observation", "normalState", "deviationState")):
            fail(f"{cid}: user-facing acceptance content incomplete")

        refs = set(check.get("equipmentRefs") or [])
        if not refs or not refs.issubset(equipment):
            fail(f"{cid}: unresolved equipment refs {sorted(refs - set(equipment))}")
        if any(not (equipment[eid]["profiles"] & profiles) for eid in refs):
            fail(f"{cid}: equipment ref has no profile overlap")
        for profile in profiles:
            if not (refs & active_coverage[profile]):
                fail(f"{cid}: no applicable equipment for profile {profile}")

        src_refs = set(check.get("sourceRefs") or [])
        if not src_refs or not src_refs.issubset(sources):
            fail(f"{cid}: unresolved source refs {sorted(src_refs - set(sources))}")

        diag_refs = set(check.get("diagnosticRefs") or [])
        if not diag_refs.issubset(scenarios):
            fail(f"{cid}: unresolved diagnostic refs {sorted(diag_refs - set(scenarios))}")
        for sid in diag_refs:
            if not (scenarios[sid]["profiles"] & profiles):
                fail(f"{cid}: diagnostic {sid} has no profile overlap")
        direct_diagnostic_links.update(diag_refs)

        visible = " ".join(str(check.get(key) or "") for key in ("title", "observation", "normalState", "deviationState"))
        if unsafe_imperative.search(visible):
            fail(f"{cid}: possible unsafe procedural instruction in acceptance content")
        if re.search(r"\bTEM2(?:U)?-(?:EQ|DIAG|SCH|SRC)-", visible):
            fail(f"{cid}: raw internal ID leaked into user-facing text")

        if check.get("scopeStatus") == "CURRENT_NORMATIVE_CONTEXT_LOCAL_VOLUME_REQUIRED":
            if "TEM2-SRC-BRAKES-2026" not in src_refs:
                fail(f"{cid}: current brake acceptance check lacks 2026 brake rules")
            if "TEM2-SRC-POT-RZD-342-2025" not in src_refs:
                fail(f"{cid}: current brake acceptance check lacks current safety source")

    # Deterministic expanded acceptance means every applicable Stage-2 entity is covered for each profile.
    if len(active_coverage["tem2-base"]) != 33 or len(active_coverage["tem2u-improved"]) != 35:
        fail("effective acceptance coverage no longer matches Stage 2")

    # Every diagnostic must have a profile-valid bridge through equipment into expanded acceptance.
    for sid, scenario in scenarios.items():
        for profile in scenario["profiles"]:
            if not (scenario["equipment"] & active_coverage[profile]):
                fail(f"{sid}: no Acceptance↔Diagnostics bridge for {profile}")

    # Exact profile-isolation sentinels.
    u_only_equipment = {eid for eid, item in equipment.items() if item["profiles"] == {"tem2u-improved"}}
    if u_only_equipment != {"TEM2U-EQ-DIESEL", "TEM2U-EQ-ONE-PERSON", "TEM2U-EQ-MU-CONTROL"}:
        fail(f"TEM2U-only Stage 2 equipment set drifted: {sorted(u_only_equipment)}")
    if any(eid in active_coverage["tem2-base"] for eid in u_only_equipment):
        fail("TEM2U-only equipment leaked into TEM2 effective acceptance")

    cross = stage4.get("crossLayerPolicy") or {}
    for key in ("atlasToAcceptance", "acceptanceToDiagnostics", "acceptanceToSources", "profileIsolation", "brakePriority", "unknownExecution"):
        if not str(cross.get(key) or "").strip():
            fail(f"cross-layer policy missing {key}")
    if "READY_FOR_APP_INTEGRATION" not in str(stage4.get("integrationDecision") or ""):
        fail("Stage 4 integration decision missing READY_FOR_APP_INTEGRATION gate")

    print(
        "TEM2 STAGE 4 PASS: 2 routes; 14 shared + 2 TEM2U profile checks; "
        "expanded Atlas acceptance 33/33 TEM2 + 35/35 TEM2U; 24/24 diagnostics bridged; "
        "sources/profile isolation/current brake boundary/fail-closed contract OK; "
        f"direct scenario links authored={len(direct_diagnostic_links)}"
    )


def main():
    family_manifest = load(FAMILY_MANIFEST)
    global_manifest = load(GLOBAL_MANIFEST)
    source_data = load(SOURCES)
    stage2 = load(STAGE2)
    stage3 = load(STAGE3)
    stage4 = load(STAGE4)

    equipment, systems, schemes, coverage = stage2_index(stage2)
    sources = source_index(source_data)
    scenarios = scenario_index(stage3, equipment, schemes)
    validate_manifest(family_manifest, global_manifest)
    validate_stage4(stage4, equipment, systems, coverage, sources, scenarios)


if __name__ == "__main__":
    main()
