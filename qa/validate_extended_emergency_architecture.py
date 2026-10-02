#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
PATCH = ROOT / "patch/app"
CHME = ROOT / "docs/locomotives/chme3"


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_ARCH_FAIL: {message}")


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path):
    try:
        return json.loads(read(path))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def unique_ids(rows, label):
    ids = [row.get("id") for row in rows]
    if any(not item for item in ids):
        fail(f"{label}: missing id")
    if len(ids) != len(set(ids)):
        fail(f"{label}: duplicate id")
    return set(ids)


def flatten_text(value):
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        return " ".join(flatten_text(item) for item in value)
    if isinstance(value, dict):
        return " ".join(flatten_text(item) for item in value.values())
    return ""


def validate_policy_and_ui():
    policy_app = APP / "src/main/assets/technical/extended_emergency_policy.json"
    policy_patch = PATCH / "src/main/assets/technical/extended_emergency_policy.json"
    if policy_app.read_bytes() != policy_patch.read_bytes():
        fail("runtime policy app/patch mirror mismatch")
    policy = load_json(policy_app)

    if policy.get("schemaVersion") != 2:
        fail("expanded policy schema must be v2 after Stage 0 audit")
    if policy.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
        fail("wrong modeId")
    if policy.get("defaultEnabled") is not False:
        fail("expanded mode must default OFF")

    warning = policy.get("warningPolicy") or {}
    if warning.get("acknowledgement") != "FIRST_ENABLE_ONLY":
        fail("first-enable acknowledgement contract missing")
    if warning.get("repeatPerScenario") is not False:
        fail("general warning must not repeat per scenario")
    if warning.get("badge") != "Расширенный сценарий":
        fail("expanded badge drifted")
    if "бирюз" not in warning.get("warningTextMustNameColor", "").lower():
        fail("warning must explicitly name turquoise frame")

    visual = policy.get("visualSemantics") or {}
    if visual.get("themeIndependent") is not True:
        fail("expanded semantic color must be independent from app accent palette")
    if visual.get("lightBorder") != "#0F766E" or visual.get("darkBorder") != "#5EEAD4":
        fail("fixed turquoise tokens drifted")
    if visual.get("colorNeverSoleCarrier") is not True:
        fail("color cannot be the sole status carrier")
    if visual.get("dangerInsideScenarioRemainsRed") is not True:
        fail("danger semantics must stay independently red")

    required_classes = {"ARCHIVED_OFFICIAL", "MANUFACTURER_EXTENDED", "HISTORICAL_TRAINING", "FIELD_PRACTICE"}
    actual_classes = {row.get("id") for row in policy.get("provenanceClasses") or []}
    if actual_classes != required_classes:
        fail(f"provenance classes mismatch: {actual_classes}")

    required_dispositions = {"INFORMATION_ONLY", "CONDITIONAL_ACTION", "PROHIBITED"}
    actual_dispositions = {row.get("id") for row in policy.get("actionDispositions") or []}
    if actual_dispositions != required_dispositions:
        fail(f"action dispositions mismatch: {actual_dispositions}")

    required_metadata = {
        "id", "profiles", "provenanceClass", "sourceRefs", "sourceStatus", "riskClass", "actionDisposition"
    }
    if set(policy.get("requiredScenarioMetadata") or []) != required_metadata:
        fail("required expanded-scenario metadata contract drifted")

    runtime = policy.get("runtimeContract") or {}
    exact_runtime = {
        "standardModeMayEnterExtendedBranch": False,
        "extendedModeMayUpgradeAuthority": False,
        "sourceAuthorityAndDisplayDispositionSeparate": True,
        "profileApplicabilityRequired": True,
        "unknownExecution": "FAIL_CLOSED",
        "sourceRequired": True,
        "sourceStatusRequired": True,
        "actionDispositionRequired": True,
        "fieldPracticeDoesNotAutoAuthorizeAction": True,
        "manufacturerOrArchivedSourceDoesNotAutoAuthorizeCurrentAction": True,
        "dangerousStepKeepsIndependentSafetyMarking": True,
        "dangerousStepRequiresDedicatedSafetyGate": True,
        "prohibitedStepNeverExecutable": True,
        "bypassOrProtectionDefeatRequiresExplicitRecord": True,
        "bypassOrProtectionDefeatDispositionDefault": "PROHIBITED",
        "conditionalBypassRequiresDedicatedSafetyGate": True,
        "adjacentVariantInheritance": "DENY_UNLESS_EXPLICITLY_EVIDENCED",
    }
    for key, expected in exact_runtime.items():
        if runtime.get(key) != expected:
            fail(f"runtime contract drift: {key}={runtime.get(key)!r}, expected {expected!r}")

    mirror_paths = [
        "src/main/java/ru/railbrake/calculator/data/ExtendedEmergencyModeRepository.kt",
        "src/main/java/ru/railbrake/calculator/ui/ExtendedEmergencySettingsSection.kt",
        "src/main/java/ru/railbrake/calculator/ui/theme/Color.kt",
        "src/main/java/ru/railbrake/calculator/ui/theme/Theme.kt",
        "src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt",
    ]
    for rel in mirror_paths:
        a, b = APP / rel, PATCH / rel
        if a.read_bytes() != b.read_bytes():
            fail(f"app/patch mirror mismatch: {rel}")

    repository = read(APP / mirror_paths[0])
    for token in (
        "EXTENDED_EMERGENCY_KNOWLEDGE",
        "warning_acknowledged_v1",
        "Расширенный сценарий",
        "enableAfterAcknowledgement",
        "enablePreviouslyAcknowledged",
        "disable",
    ):
        if token not in repository:
            fail(f"repository contract missing {token}")
    if "if (!hasAcknowledgedWarning()) return" not in repository:
        fail("re-enable path must remain impossible before acknowledgement")

    settings = read(APP / mirror_paths[1])
    for token in (
        "Расширенные аварийные приёмы",
        "Архивные, заводские и полевые методы — включаются отдельно",
        "Стандартная диагностика не показывает расширенные ветви.",
        "бирюзовой рамкой",
        "Расширенный сценарий",
        "не означает, что описанное действие разрешено",
        "не будет повторяться при открытии каждого расширенного сценария",
        "отдельную красную маркировку",
    ):
        if token not in settings:
            fail(f"settings warning/copy missing: {token}")
    if "обычные разрешённые маршруты" in settings:
        fail("Settings must not overclaim authority of every standard route")
    if "opt-in" in settings.lower():
        fail("user-facing Settings copy must not expose internal opt-in jargon")

    colors = read(APP / mirror_paths[2])
    for token in ("ExtendedEmergencyLight = Color(0xFF0F766E)", "ExtendedEmergencyDark = Color(0xFF5EEAD4)"):
        if token not in colors:
            fail(f"semantic color missing: {token}")

    theme = read(APP / mirror_paths[3])
    for token in ("extendedEmergency", "extendedEmergencyContainer", "extendedEmergencyBorder"):
        if token not in theme:
            fail(f"theme semantic token missing: {token}")

    app_ui = read(APP / mirror_paths[4])
    if 'SETTINGS("Настройки")' not in app_ui or 'Text("Настройки")' not in app_ui:
        fail("Settings user-facing rename missing")
    if "ExtendedEmergencySettingsSection()" not in app_ui:
        fail("expanded settings section not wired into Settings")
    if "Палитра" in app_ui:
        fail("stale user-facing Палитра remains")


def validate_chme3_research_boundary():
    base_registry = load_json(CHME / "source_registry.json")
    addendum = load_json(CHME / "diagnostic_source_registry_addendum.json")
    base_sources = base_registry.get("sources") or []
    add_sources = addendum.get("sources") or []
    base_ids = unique_ids(base_sources, "ChME3 base source registry")
    add_ids = unique_ids(add_sources, "ChME3 diagnostic source addendum")
    if base_ids & add_ids:
        fail(f"source IDs duplicated between registries: {sorted(base_ids & add_ids)}")
    all_source_ids = base_ids | add_ids

    by_id = {row["id"]: row for row in add_sources}
    conflict = by_id.get("CHME3-SRC-TCH11-VYBORG-2007")
    if not conflict:
        fail("legacy recommendations source missing")
    if conflict.get("provenanceStatus") != "CONFLICT":
        fail("legacy TCh recommendations must preserve conflicting provenance")
    if conflict.get("authority") != "RESEARCH_ONLY_PROVENANCE_CONFLICT":
        fail("conflicting legacy source must remain research-only")
    if len(conflict.get("comparisonUrls") or []) < 2:
        fail("conflicting legacy source must keep comparison evidence")

    idx996 = by_id.get("CHME3-SRC-996R-CURRENT-INDEX")
    if not idx996 or idx996.get("authority") != "SOURCE_INDEX_ONLY":
        fail("996/r secondary index must not be promoted to primary action authority")
    note996 = flatten_text(idx996.get("notes") or []).lower()
    if "не доказывает отсутствие" not in note996 or "сверк" not in (idx996.get("knownRevision") or "").lower():
        fail("996/r current-status limitation must remain explicit")

    observations = load_json(CHME / "diagnostic_field_observations.json")
    source = observations.get("source") or {}
    if source.get("kind") != "FIELD_PRACTICE" or source.get("status") != "UNVERIFIED":
        fail("field observations source must remain FIELD_PRACTICE/UNVERIFIED")
    if source.get("actionAuthority") != "NO_UNTIL_CONFLICT_REVIEW":
        fail("field observations source must not authorize actions")
    obs_rows = observations.get("observations") or []
    unique_ids(obs_rows, "ChME3 field observations")
    safe_obs_authorities = {"INFORMATION_ONLY", "REQUIRES_CONFLICT_REVIEW"}
    for row in obs_rows:
        if row.get("evidenceStatus") != "FIELD_PRACTICE":
            fail(f"{row['id']}: field observation evidence status drifted")
        if row.get("actionAuthority") not in safe_obs_authorities:
            fail(f"{row['id']}: field observation leaked action authority {row.get('actionAuthority')}")

    matrix = load_json(CHME / "diagnostic_source_matrix.json")
    matrix_policy = matrix.get("policy") or {}
    unsafe_rule = (matrix_policy.get("unsafeFieldActionRule") or "").upper()
    if "INFORMATION_ONLY" not in unsafe_rule or "PROHIBITED_OR_UNSAFE" not in unsafe_rule:
        fail("source matrix no longer quarantines unsafe field actions")
    candidates = matrix.get("fieldMaterialCandidates") or []
    unique_ids(candidates, "ChME3 field material candidates")
    for row in candidates:
        authority = row.get("actionAuthority") or ""
        if authority not in {"PROHIBITED_OR_UNSAFE_UNTIL_REVIEW", "REQUIRES_CONFLICT_REVIEW", "INFORMATION_ONLY"}:
            fail(f"{row['id']}: unsafe field candidate has non-quarantined authority {authority}")

    clusters = {row.get("id"): row for row in matrix.get("coverageClusters") or []}
    air = clusters.get("CHME3-DIAG-CLUSTER-AIR-BRAKE") or {}
    if not {"NO_BUILD_PRESSURE", "NO_STOP", "PRESSURE_LEAK", "OVERHEAT"}.issubset(set(air.get("failureModesToPreserve") or [])):
        fail("compressor opposite-event semantics collapsed")
    brake = clusters.get("CHME3-DIAG-CLUSTER-BRAKE") or {}
    if not {"NO_BRAKE", "NO_RELEASE", "SPONTANEOUS_BRAKE"}.issubset(set(brake.get("failureModesToPreserve") or [])):
        fail("brake opposite-event semantics collapsed")
    edb = clusters.get("CHME3T-DIAG-CLUSTER-EDB") or {}
    if edb.get("profileGate") != "chme3t-rheostatic":
        fail("ChME3T EDB research cluster lost profile gate")

    diagnostics = load_json(CHME / "diagnostics_extended_pass3.json")
    scenarios = diagnostics.get("scenarios") or []
    unique_ids(scenarios, "ChME3 diagnostics extended pass3")
    if not scenarios:
        fail("ChME3 extended diagnostic research scenarios missing")

    forbidden_authorities = {"SOURCE_BOUND_OK", "AUTHORIZED", "EXECUTABLE", "ALLOWED"}
    risky_operational_tokens = (
        "поставить перемыч",
        "соединить перемыч",
        "зашунт",
        "подпереть",
        "подпор",
        "деревянными изолированными предметами",
        "нажать на якор",
        "придержать во включенном состоянии",
        "ослабить пружину контактора",
        "с помощью веревки",
        "создать дублирующую",
        "запуск произвести напрямую",
        "замкнуть контактор вручную",
    )
    conflict_ref = "CHME3-SRC-TCH11-VYBORG-2007"
    conflict_ref_count = 0
    for row in scenarios:
        refs = set(row.get("evidenceRefs") or [])
        missing = refs - all_source_ids
        if missing:
            fail(f"{row['id']}: unresolved evidence refs {sorted(missing)}")
        authority = row.get("actionAuthority")
        if authority in forbidden_authorities:
            fail(f"{row['id']}: research scenario was promoted to executable authority")
        if not row.get("profiles") or not row.get("riskClass") or not row.get("evidenceStatus"):
            fail(f"{row['id']}: missing profile/risk/evidence metadata")
        decision_text = flatten_text(row.get("decisionTree") or []).lower()
        for token in risky_operational_tokens:
            if token in decision_text:
                fail(f"{row['id']}: risky legacy/field procedure leaked into decisionTree: {token}")
        searchable = " ".join([
            row.get("title") or "",
            flatten_text(row.get("symptoms") or []),
            flatten_text(row.get("queryTerms") or []),
        ]).lower()
        if any(token in searchable for token in ("реостат", "электродинамич", "эдт")):
            profiles = set(row.get("profiles") or [])
            if profiles != {"ЧМЭ3Т"}:
                fail(f"{row['id']}: EDB content leaked outside ChME3T")
        if conflict_ref in refs:
            conflict_ref_count += 1
            if authority not in {"TRIAGE_ONLY_NO_REPAIR", "SAFETY_GATE_REQUIRED", "SOURCE_AND_PROFILE_REQUIRED"}:
                fail(f"{row['id']}: conflicting-provenance source has unsafe authority {authority}")
    if conflict_ref_count == 0:
        fail("provenance-conflict source is no longer exercised by audited research scenarios")

    # Stage 0 intentionally has no new user-executable expanded branches yet.
    # Prevent accidental leakage of this mode into technical JSON runtime before Stage 3.
    runtime_policy_name = "extended_emergency_policy.json"
    for path in (APP / "src/main/assets/technical").rglob("*.json"):
        if path.name == runtime_policy_name:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if '"EXTENDED_EMERGENCY_KNOWLEDGE"' in text:
            fail(f"expanded runtime branch leaked before Stage 3: {path.relative_to(ROOT)}")

    return len(scenarios), len(obs_rows), len(candidates), conflict_ref_count


def main():
    validate_policy_and_ui()
    scenario_count, observation_count, candidate_count, conflict_ref_count = validate_chme3_research_boundary()
    print("EXTENDED_EMERGENCY_STAGE0_AUDIT_PASS")
    print("mode=OFF_BY_DEFAULT warning=FIRST_ENABLE_ONLY repeat_per_scenario=false")
    print("visual=FIXED_TURQUOISE badge=Расширенный_сценарий danger=SEPARATE_RED")
    print("authority=NOT_UPGRADED action_disposition=REQUIRED dangerous_gate=REQUIRED")
    print("profile=REQUIRED unknown_execution=FAIL_CLOSED adjacent_variant=DENY_UNLESS_EVIDENCED")
    print(
        f"chme3_research=scenarios:{scenario_count} field_observations:{observation_count} "
        f"field_candidates:{candidate_count} provenance_conflict_refs:{conflict_ref_count}"
    )
    print("runtime_extended_branches=0 (intentional until Stage 3)")


if __name__ == "__main__":
    main()
