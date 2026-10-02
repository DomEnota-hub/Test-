#!/usr/bin/env python3
import csv
import importlib.util
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_ASSET = ROOT / "app/src/main/assets/technical/extended_emergency_runtime.json"
PATCH_ASSET = ROOT / "patch/app/src/main/assets/technical/extended_emergency_runtime.json"
BUILDER = ROOT / "qa/build_extended_emergency_stage4_asset.py"
SOURCES = ROOT / "docs/integration/extended_emergency_stage2_sources.tsv"
WORKFLOW = ROOT / ".github/workflows/extended-emergency-stage4-materialize.yml"

MIRRORS = [
    (
        ROOT / "app/src/main/java/ru/railbrake/calculator/core/ExtendedEmergencyRuntimeRepository.kt",
        ROOT / "patch/app/src/main/java/ru/railbrake/calculator/core/ExtendedEmergencyRuntimeRepository.kt",
    ),
    (
        ROOT / "app/src/main/java/ru/railbrake/calculator/ui/ExtendedEmergencyEvidenceSection.kt",
        ROOT / "patch/app/src/main/java/ru/railbrake/calculator/ui/ExtendedEmergencyEvidenceSection.kt",
    ),
    (
        ROOT / "app/src/main/java/ru/railbrake/calculator/ui/ErmakDiagnosticsScreen.kt",
        ROOT / "patch/app/src/main/java/ru/railbrake/calculator/ui/ErmakDiagnosticsScreen.kt",
    ),
    (
        ROOT / "app/src/test/java/ru/railbrake/calculator/core/ExtendedEmergencyRuntimePolicyTest.kt",
        ROOT / "patch/app/src/test/java/ru/railbrake/calculator/core/ExtendedEmergencyRuntimePolicyTest.kt",
    ),
]

EXPECTED_PROFILE_COUNTS = {
    "chme3-base": 53,
    "chme3t-rheostatic": 1,
    "chme3e-electronic": 0,
    "tem2-base": 22,
    "tem2u-improved": 1,
}


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_STAGE4_FAIL: {message}")


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def load_builder():
    spec = importlib.util.spec_from_file_location("expanded_stage4_builder", BUILDER)
    if spec is None or spec.loader is None:
        fail("unable to import Stage 4 builder")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def read_sources():
    with SOURCES.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle, delimiter="\t"))
    return {row["id"].strip(): row for row in rows}


def validate_assets():
    if not APP_ASSET.is_file() or not PATCH_ASSET.is_file():
        fail("runtime asset or patch mirror missing")
    app_text = APP_ASSET.read_text(encoding="utf-8")
    patch_text = PATCH_ASSET.read_text(encoding="utf-8")
    if app_text != patch_text:
        fail("app/patch runtime assets differ")

    asset = load_json(APP_ASSET)
    expected = load_builder().build_asset()
    if asset != expected:
        fail("materialized Android runtime asset differs from deterministic Stage 4 builder output")

    if asset.get("schemaVersion") != 1 or asset.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
        fail("runtime asset identity drifted")
    policy = asset.get("policy") or {}
    exact_policy = {
        "defaultEnabled": False,
        "standardDiagnosticsRemainCanonical": True,
        "attachOnlyAfterStandardScenarioMatch": True,
        "exactProfileOnly": True,
        "unknownExecution": "FAIL_CLOSED",
        "adjacentVariantInheritance": "DENY",
        "expandedEvidenceExecutable": False,
        "procedureLevelHazardousDetailVisible": False,
        "rawInternalIdsVisibleToUser": False,
    }
    for key, value in exact_policy.items():
        if policy.get(key) != value:
            fail(f"runtime policy drift: {key}={policy.get(key)!r}, expected {value!r}")

    stats = asset.get("statistics") or {}
    exact_stats = {
        "standardScenarios": 92,
        "foundScenarios": 76,
        "noAdditionalScenarios": 16,
        "candidateRoutes": 77,
        "informationOnly": 49,
        "prohibited": 28,
        "conditionalAction": 0,
        "profileCandidateCounts": EXPECTED_PROFILE_COUNTS,
    }
    for key, value in exact_stats.items():
        if stats.get(key) != value:
            fail(f"runtime statistics drift: {key}={stats.get(key)!r}, expected {value!r}")

    routing = asset.get("scenarioRouting") or []
    records = asset.get("records") or []
    if len(routing) != 92 or len(records) != 77:
        fail(f"runtime projection count mismatch: routing={len(routing)}, records={len(records)}")
    route_ids = [row.get("standardScenarioId") for row in routing]
    if any(not item for item in route_ids) or len(route_ids) != len(set(route_ids)):
        fail("scenarioRouting IDs missing or duplicated")

    sources = read_sources()
    candidate_ids = []
    disposition_counts = Counter()
    profile_counts = Counter({key: 0 for key in EXPECTED_PROFILE_COUNTS})
    user_text_fields = []
    forbidden_procedure = [
        r"между\s+провод",
        r"замкнуть\s+контактор",
        r"зашунт",
        r"нажать\s+на\s+якор",
        r"подпер",
        r"заклин",
        r"\b\d{2,4}\s*[-–]\s*\d{2,4}\b",
    ]

    for record in records:
        cid = str(record.get("candidateId") or "")
        sid = str(record.get("standardScenarioId") or "")
        if not cid or not sid:
            fail("record without candidate/scenario ID")
        candidate_ids.append(cid)

        disposition = record.get("actionDisposition")
        if disposition not in {"INFORMATION_ONLY", "PROHIBITED"}:
            fail(f"{cid}: unsupported runtime disposition {disposition!r}")
        disposition_counts[disposition] += 1

        safety = record.get("runtimeSafety") or {}
        if safety != {
            "exactProfileOnly": True,
            "executable": False,
            "procedureVisible": False,
            "currentAuthorityVerified": False,
            "runtimeAuthorityUpgradeAllowed": False,
        }:
            fail(f"{cid}: runtime safety flags drifted")

        profiles = record.get("profiles") or []
        if not profiles:
            fail(f"{cid}: no profile")
        for profile in profiles:
            if profile not in EXPECTED_PROFILE_COUNTS:
                fail(f"{cid}: unknown profile {profile}")
            profile_counts[profile] += 1

        display_fields = [
            str(record.get("dispositionLabel") or ""),
            str(record.get("riskLabel") or ""),
            str(record.get("summary") or ""),
            str(record.get("terminal") or ""),
        ]
        if any(not value.strip() for value in display_fields):
            fail(f"{cid}: user-facing evidence text missing")

        source_presentations = record.get("sources") or []
        if not source_presentations:
            fail(f"{cid}: source presentation missing")
        for source in source_presentations:
            ref = str(source.get("sourceRef") or "")
            raw = sources.get(ref)
            if raw is None:
                fail(f"{cid}: unresolved sourceRef {ref}")
            if source.get("title") != raw.get("title"):
                fail(f"{cid}: human source title drifted for {ref}")
            for key in ("title", "provenanceLabel", "statusLabel"):
                value = str(source.get(key) or "").strip()
                if not value:
                    fail(f"{cid}: source display field {key} missing")
                display_fields.append(value)

        visible_text = " ".join(display_fields)
        user_text_fields.append(visible_text)
        if re.search(r"(?:ST2-SRC-|EXT2-|CHME3-DIAG-|TEM2-DIAG-)", visible_text):
            fail(f"{cid}: raw internal ID leaked into user-facing text")
        for pattern in forbidden_procedure:
            if re.search(pattern, visible_text, re.I):
                fail(f"{cid}: procedure-level hazardous detail leaked into display text: {pattern}")

    if len(candidate_ids) != len(set(candidate_ids)):
        fail("duplicate runtime candidate IDs")
    if disposition_counts != Counter({"INFORMATION_ONLY": 49, "PROHIBITED": 28}):
        fail(f"runtime disposition counts drifted: {dict(disposition_counts)}")
    if dict(profile_counts) != EXPECTED_PROFILE_COUNTS:
        fail(f"runtime profile counts drifted: {dict(profile_counts)}")

    if any("chme3e-electronic" in (row.get("profiles") or []) for row in records):
        fail("ChME3E gained expanded evidence despite fail-closed Stage 2 result")
    chme3t = [row for row in records if "chme3t-rheostatic" in (row.get("profiles") or [])]
    if len(chme3t) != 1 or chme3t[0].get("standardScenarioId") != "CHME3-DIAG-110":
        fail("ChME3T expanded evidence must remain isolated to CHME3-DIAG-110")
    tem2u = [row for row in records if "tem2u-improved" in (row.get("profiles") or [])]
    if len(tem2u) != 1 or tem2u[0].get("standardScenarioId") != "TEM2-DIAG-024":
        fail("TEM2U expanded evidence must remain isolated to TEM2-DIAG-024")


def validate_android_wiring():
    for app, patch in MIRRORS:
        if not app.is_file() or not patch.is_file():
            fail(f"missing app/patch mirror pair for {app.name}")
        if app.read_text(encoding="utf-8") != patch.read_text(encoding="utf-8"):
            fail(f"app/patch mirror mismatch: {app.name}")

    repo = MIRRORS[0][0].read_text(encoding="utf-8")
    for required in (
        'TechnicalFamily.CHME3 -> "chme3-base"',
        'TechnicalFamily.CHME3T -> "chme3t-rheostatic"',
        'TechnicalFamily.CHME3E -> "chme3e-electronic"',
        'actionDisposition in setOf("INFORMATION_ONLY", "PROHIBITED")',
        '!executable',
        '!procedureVisible',
        '!currentAuthorityVerified',
        '!runtimeAuthorityUpgradeAllowed',
    ):
        if required not in repo:
            fail(f"Android fail-closed runtime guard missing: {required}")

    ui = MIRRORS[1][0].read_text(encoding="utf-8")
    for required in (
        "ExtendedEmergencyModeRepository.BADGE",
        "RailTheme.colors.extendedEmergencyBorder",
        "MaterialTheme.colorScheme.errorContainer",
        "source.title",
        "source.provenanceLabel",
        "source.statusLabel",
    ):
        if required not in ui:
            fail(f"expanded evidence presentation missing: {required}")
    for forbidden in ("candidateId", "sourceRef", "methodClass"):
        if forbidden in ui:
            fail(f"UI references internal field {forbidden}")

    route = MIRRORS[2][0].read_text(encoding="utf-8")
    marker = 'if (node?.type == "terminal" && family.isChme3)'
    call = "ExtendedEmergencyEvidenceSection("
    if route.count(marker) != 1 or route.count(call) != 1:
        fail("expanded evidence must attach exactly once and only at completed ChME terminal")
    if route.index(marker) > route.index(call):
        fail("terminal gate must wrap expanded evidence call")
    prohibited_marker = 'if (scenario.prohibited.isNotEmpty())'
    if prohibited_marker not in route or route.index(prohibited_marker) > route.index(marker):
        fail("expanded evidence must render after standard prohibited/safe localization content")

    mode_repo = (ROOT / "app/src/main/java/ru/railbrake/calculator/data/ExtendedEmergencyModeRepository.kt").read_text(encoding="utf-8")
    if 'getBoolean(KEY_ENABLED, false)' not in mode_repo:
        fail("expanded mode no longer defaults off")
    if 'KEY_WARNING_ACKNOWLEDGED_V1' not in mode_repo or 'if (!hasAcknowledgedWarning()) return' not in mode_repo:
        fail("one-time acknowledgement / fail-closed settings behavior drifted")

    settings = (ROOT / "app/src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt").read_text(encoding="utf-8")
    if "ExtendedEmergencySettingsSection()" not in settings:
        fail("expanded settings switch is no longer wired")

    assistant = (ROOT / "app/src/main/java/ru/railbrake/calculator/ui/AssistantResultActivity.kt").read_text(encoding="utf-8")
    locomotive = (ROOT / "app/src/main/java/ru/railbrake/calculator/ui/LocomotiveDiagnosticsScreen.kt").read_text(encoding="utf-8")
    if "KIND_CHME3_DIAGNOSTIC" not in assistant or "LocomotiveDiagnosticsScreen(initialScenarioId = id" not in assistant:
        fail("assistant no longer routes ChME diagnostics through canonical diagnostic screen")
    if "ErmakDiagnosticsScreen(" not in locomotive or "initialScenarioId = diagnosticScenarioForFamily" not in locomotive:
        fail("canonical diagnostic screen no longer forwards matched ChME scenario")


def validate_final_workflow_is_read_only():
    if not WORKFLOW.is_file():
        fail("Stage 4 workflow missing")
    text = WORKFLOW.read_text(encoding="utf-8")
    if "contents: read" not in text:
        fail("final Stage 4 workflow must be read-only")
    for forbidden in ("contents: write", "git push", "git commit"):
        if forbidden in text:
            fail(f"final Stage 4 workflow remains self-mutating: {forbidden}")


def main():
    validate_assets()
    validate_android_wiring()
    validate_final_workflow_is_read_only()
    print(
        "EXTENDED EMERGENCY STAGE 4 PASS: 92/92 routing decisions; 77 safe Android display records; "
        "49 INFORMATION_ONLY + 28 PROHIBITED; exact-profile fail-closed wiring; human source presentation; "
        "terminal-only diagnostics/assistant integration; app/patch mirrors and read-only CI contract OK"
    )


if __name__ == "__main__":
    main()
