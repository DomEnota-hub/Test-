#!/usr/bin/env python3
import csv
import importlib.util
import json
import re
from collections import Counter, defaultdict, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTEGRATION = ROOT / "docs/integration"
BUILDER = ROOT / "qa/build_extended_emergency_stage3_projection.py"
RUNTIME_CONTRACT = INTEGRATION / "extended_emergency_stage3_runtime_contract.json"
ASSISTANT_CONTRACT = INTEGRATION / "extended_emergency_stage3_assistant_contract.json"
CANDIDATES = INTEGRATION / "extended_emergency_stage2_candidates.tsv"
DISCOVERY = INTEGRATION / "extended_emergency_stage2_discovery.tsv"

EXPECTED = {
    "standard": 92,
    "found": 76,
    "no_additional": 16,
    "routes": 77,
    "information": 49,
    "prohibited": 28,
    "conditional": 0,
}
EXPECTED_PROFILE_COUNTS = {
    "chme3-base": 53,
    "chme3t-rheostatic": 1,
    "chme3e-electronic": 0,
    "tem2-base": 22,
    "tem2u-improved": 1,
}
STAGE2_SHA = "6a76fc0ada4526e0b5f6415095b9d492ed986c9c"


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_STAGE3_FAIL: {message}")


def load_json(path: Path):
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def read_tsv(path: Path):
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def split_pipe(value):
    return [item.strip() for item in (value or "").split("|") if item.strip()]


def load_builder():
    spec = importlib.util.spec_from_file_location("extended_stage3_builder", BUILDER)
    if spec is None or spec.loader is None:
        fail("unable to import Stage 3 builder")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def validate_contracts():
    runtime = load_json(RUNTIME_CONTRACT)
    assistant = load_json(ASSISTANT_CONTRACT)

    if runtime.get("schemaVersion") != 1 or runtime.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
        fail("runtime contract identity drifted")
    boundary = runtime.get("runtimeBoundary") or {}
    exact_boundary = {
        "standardDiagnosticsRemainCanonical": True,
        "expandedModeMayReplaceStandardScenarioSelection": False,
        "expandedEvidenceAttachesOnlyAfterStandardScenarioMatch": True,
        "standardGraphProvidesExecutableChecks": True,
        "expandedProjectionProvidesExecutableActions": False,
        "unknownExecution": "FAIL_CLOSED",
        "adjacentVariantInheritance": "DENY",
        "currentAuthorityRequiredBeforeConditionalAction": True,
        "procedureLevelHazardousDetailAllowed": False,
    }
    for key, expected in exact_boundary.items():
        if boundary.get(key) != expected:
            fail(f"runtime boundary drift: {key}={boundary.get(key)!r}, expected {expected!r}")

    presentation = runtime.get("presentation") or {}
    if presentation.get("badge") != "Расширенный сценарий":
        fail("expanded badge drifted")
    if presentation.get("lightBorder") != "#0F766E" or presentation.get("darkBorder") != "#5EEAD4":
        fail("turquoise frame tokens drifted")
    if presentation.get("colorNeverSoleCarrier") is not True:
        fail("color cannot be the sole expanded-mode carrier")
    if presentation.get("dangerSemanticRemainsIndependentRed") is not True:
        fail("danger semantic must remain independently red")
    if presentation.get("repeatGeneralWarningPerScenario") is not False:
        fail("general warning must not repeat for every scenario")

    dispositions = runtime.get("dispositionRules") or {}
    for key in ("INFORMATION_ONLY", "PROHIBITED"):
        if (dispositions.get(key) or {}).get("executable") is not False:
            fail(f"{key} unexpectedly executable")
        if (dispositions.get(key) or {}).get("procedureVisible") is not False:
            fail(f"{key} procedure unexpectedly visible")
    if (dispositions.get("CONDITIONAL_ACTION") or {}).get("allowedAtStage3") is not False:
        fail("Stage 3 must not introduce CONDITIONAL_ACTION")

    if assistant.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
        fail("assistant contract mode mismatch")
    routing = assistant.get("routingRules") or {}
    required_routing = {
        "troubleshootingBeatsAtlasForFaultLanguage": True,
        "standardScenarioSelectionIsCanonical": True,
        "expandedModeCreatesSeparateIntent": False,
        "expandedModeAttachesToMatchedScenario": True,
        "profileMatch": "EXACT_ONLY",
        "unknownExecution": "FAIL_CLOSED",
        "adjacentVariantFallback": False,
        "oppositeEventsRemainSeparatedByStandardSemantics": True,
        "rawInternalIdsVisibleToUser": False,
    }
    for key, expected in required_routing.items():
        if routing.get(key) != expected:
            fail(f"assistant routing drift: {key}={routing.get(key)!r}")

    safety = assistant.get("safetyRules") or {}
    for key in (
        "informationOnlyNeverImperative",
        "prohibitedNeverContainsProcedure",
        "prohibitedNeverExecutable",
        "fieldPracticeNeverUpgradesAuthority",
        "historicalOrManufacturerSourceNeverUpgradesAuthorityByItself",
        "conditionalActionRequiresFutureAuthorityReview",
        "dangerMarkingRemainsIndependentRed",
    ):
        if safety.get(key) is not True:
            fail(f"assistant safety rule missing: {key}")

    return runtime, assistant


def validate_graph(graph, route_id):
    if graph.get("schemaVersion") != 2:
        fail(f"{route_id}: runtimeGraph must be schema v2")
    start = graph.get("startNodeId")
    nodes = graph.get("nodes") or []
    if not start or not nodes:
        fail(f"{route_id}: graph start/nodes missing")
    ids = [node.get("id") for node in nodes]
    if any(not node_id for node_id in ids) or len(ids) != len(set(ids)):
        fail(f"{route_id}: missing or duplicate node id")
    by_id = {node["id"]: node for node in nodes}
    if start not in by_id:
        fail(f"{route_id}: invalid start node")

    forbidden_types = {"action", "check", "instruction", "procedure"}
    for node in nodes:
        if node.get("type") in forbidden_types:
            fail(f"{route_id}: expanded graph contains executable/procedural node {node.get('type')}")

    targets = defaultdict(list)
    for node in nodes:
        for key in ("nextNodeId", "onPass", "onFail"):
            target = node.get(key)
            if target:
                if target not in by_id:
                    fail(f"{route_id}: broken edge {node['id']} -> {target}")
                targets[node["id"]].append(target)
        for choice in node.get("choices") or []:
            target = choice.get("nextNodeId")
            if target:
                if target not in by_id:
                    fail(f"{route_id}: broken choice edge {node['id']} -> {target}")
                targets[node["id"]].append(target)

    seen = set()
    queue = deque([start])
    while queue:
        current = queue.popleft()
        if current in seen:
            continue
        seen.add(current)
        queue.extend(targets[current])
    if seen != set(ids):
        fail(f"{route_id}: unreachable graph nodes {sorted(set(ids) - seen)}")
    if not any(node.get("type") == "terminal" for node in nodes):
        fail(f"{route_id}: graph has no terminal")

    text = " ".join(str(node.get("text") or "") for node in nodes).lower()
    forbidden_detail = [
        r"между\s+провод",
        r"замкнуть\s+контактор",
        r"зашунт",
        r"нажать\s+на\s+якор",
        r"подпер",
        r"заклин",
        r"принудительно\s+включ",
        r"\b\d{2,4}\s*[-–]\s*\d{2,4}\b",
    ]
    for pattern in forbidden_detail:
        if re.search(pattern, text, re.I):
            fail(f"{route_id}: hazardous procedure-level detail leaked: {pattern}")


def validate_projection():
    builder = load_builder()
    first = builder.build_projection()
    second = builder.build_projection()
    if json.dumps(first, ensure_ascii=False, sort_keys=True) != json.dumps(second, ensure_ascii=False, sort_keys=True):
        fail("Stage 3 projection is not deterministic")

    if first.get("schemaVersion") != 2 or first.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
        fail("generated projection identity drifted")
    stats = first.get("statistics") or {}
    exact_stats = {
        "standardScenarios": EXPECTED["standard"],
        "foundScenarios": EXPECTED["found"],
        "noAdditionalScenarios": EXPECTED["no_additional"],
        "candidateRoutes": EXPECTED["routes"],
        "informationOnly": EXPECTED["information"],
        "prohibited": EXPECTED["prohibited"],
        "conditionalAction": EXPECTED["conditional"],
    }
    for key, expected in exact_stats.items():
        if stats.get(key) != expected:
            fail(f"generated stats drift: {key}={stats.get(key)!r}, expected {expected}")
    if stats.get("profileCandidateCounts") != EXPECTED_PROFILE_COUNTS:
        fail(f"generated profile counts drifted: {stats.get('profileCandidateCounts')}")

    stage2_candidates = read_tsv(CANDIDATES)
    stage2_discovery = read_tsv(DISCOVERY)
    candidate_by_id = {row["candidateId"]: row for row in stage2_candidates}
    discovery_by_id = {row["standardScenarioId"]: row for row in stage2_discovery}

    routes = first.get("candidateRoutes") or []
    decisions = first.get("scenarioRouting") or []
    if len(routes) != EXPECTED["routes"] or len(decisions) != EXPECTED["standard"]:
        fail("generated route/decision count mismatch")
    route_candidate_ids = [row.get("candidateId") for row in routes]
    if len(route_candidate_ids) != len(set(route_candidate_ids)):
        fail("duplicate candidate routes")
    if set(route_candidate_ids) != set(candidate_by_id):
        fail("generated candidate set does not exactly match Stage 2 inventory")

    decision_ids = [row.get("standardScenarioId") for row in decisions]
    if len(decision_ids) != len(set(decision_ids)) or set(decision_ids) != set(discovery_by_id):
        fail("generated scenario-routing universe drifted")

    disposition_counts = Counter()
    profile_counts = Counter()
    by_scenario = defaultdict(list)
    for route in routes:
        cid = route["candidateId"]
        raw = candidate_by_id[cid]
        sid = raw["standardScenarioId"]
        if route.get("standardScenarioId") != sid:
            fail(f"{cid}: standardScenarioId drifted")
        raw_profiles = split_pipe(raw.get("profiles"))
        if route.get("profiles") != raw_profiles:
            fail(f"{cid}: profile list drifted")
        for profile in raw_profiles:
            profile_counts[profile] += 1
        if route.get("sourceRefs") != split_pipe(raw.get("sourceRefs")):
            fail(f"{cid}: sourceRefs drifted")
        for field in ("methodClass", "provenanceClass", "sourceStatus", "riskClass"):
            if route.get(field) != raw.get(field):
                fail(f"{cid}: {field} drifted")

        execution = route.get("execution") or {}
        disposition = raw["actionDisposition"]
        disposition_counts[disposition] += 1
        if execution.get("actionDisposition") != disposition:
            fail(f"{cid}: disposition drifted")
        if execution.get("executable") is not False or execution.get("procedureVisible") is not False:
            fail(f"{cid}: expanded route became executable/procedural")
        if execution.get("currentAuthorityVerified") is not False:
            fail(f"{cid}: item-level current authority unexpectedly verified")
        if execution.get("canBecomeExecutableAtRuntime") is not False:
            fail(f"{cid}: runtime must not self-upgrade authority")

        visibility = route.get("visibility") or {}
        if visibility.get("requiresModeEnabled") is not True or visibility.get("profileMatch") != "EXACT_ONLY":
            fail(f"{cid}: visibility/profile gate drifted")
        if visibility.get("adjacentVariantInheritance") != "DENY":
            fail(f"{cid}: adjacent variant inheritance enabled")

        presentation = route.get("presentation") or {}
        if presentation.get("badge") != "Расширенный сценарий":
            fail(f"{cid}: badge drifted")
        if presentation.get("lightBorder") != "#0F766E" or presentation.get("darkBorder") != "#5EEAD4":
            fail(f"{cid}: semantic turquoise border drifted")
        if presentation.get("dangerSemanticRemainsIndependentRed") is not True:
            fail(f"{cid}: danger semantic no longer independent")

        validate_graph(route.get("runtimeGraph") or {}, route["routeId"])
        by_scenario[sid].append(cid)

    if disposition_counts["INFORMATION_ONLY"] != EXPECTED["information"]:
        fail("INFORMATION_ONLY route count drifted")
    if disposition_counts["PROHIBITED"] != EXPECTED["prohibited"]:
        fail("PROHIBITED route count drifted")
    if disposition_counts.get("CONDITIONAL_ACTION", 0) != 0:
        fail("Stage 3 introduced CONDITIONAL_ACTION")
    if dict(profile_counts) != EXPECTED_PROFILE_COUNTS:
        fail(f"profile route counts drifted: {dict(profile_counts)}")

    for decision in decisions:
        sid = decision["standardScenarioId"]
        raw = discovery_by_id[sid]
        attached = sorted(by_scenario.get(sid, []))
        if decision.get("extendedCandidateIds") != attached:
            fail(f"{sid}: candidate attachment drifted")
        if decision.get("standardRouteRequired") is not True:
            fail(f"{sid}: standard route no longer required")
        if decision.get("whenModeOff") != "STANDARD_ONLY":
            fail(f"{sid}: mode-off behavior drifted")
        if raw["discoveryStatus"] == "FOUND":
            if not attached or decision.get("whenModeOn") != "STANDARD_PLUS_EXTENDED_EVIDENCE":
                fail(f"{sid}: FOUND branch lacks expanded attachment")
        else:
            if attached or decision.get("whenModeOn") != "STANDARD_ONLY_NO_EXTENDED_METHOD":
                fail(f"{sid}: NO_ADDITIONAL branch gained a candidate")
            if not decision.get("noAdditionalReason"):
                fail(f"{sid}: fail-closed no-additional reason missing")

    # Explicit profile-isolation guards established by Stage 2.
    if any("chme3e-electronic" in route.get("profiles", []) for route in routes):
        fail("ChME3E gained expanded candidate despite Stage 2 fail-closed result")
    chme3t = [route for route in routes if "chme3t-rheostatic" in route.get("profiles", [])]
    if len(chme3t) != 1 or chme3t[0]["standardScenarioId"] != "CHME3-DIAG-110":
        fail("ChME3T expanded evidence must remain isolated to DIAG-110")
    tem2u = [route for route in routes if "tem2u-improved" in route.get("profiles", [])]
    if len(tem2u) != 1 or tem2u[0]["candidateId"] != "EXT2-TEM2-DIAG-024-TEM2U":
        fail("TEM2U expanded evidence must remain isolated to explicit DIAG-024 record")

    return first


def main():
    runtime, assistant = validate_contracts()
    projection = validate_projection()
    expected = runtime.get("expectedProjection") or {}
    if expected.get("standardScenarioDecisions") != EXPECTED["standard"]:
        fail("runtime contract expected standard scenario count drifted")
    if expected.get("candidateRoutes") != EXPECTED["routes"]:
        fail("runtime contract expected route count drifted")
    print(
        "EXTENDED EMERGENCY STAGE 3 PASS: "
        "92/92 standard scenario decisions; 77 deterministic runtime-v2 expanded routes; "
        "49 INFORMATION_ONLY + 28 PROHIBITED + 0 CONDITIONAL_ACTION; "
        "exact-profile gates, turquoise presentation, assistant attachment semantics and no-procedure boundary OK"
    )


if __name__ == "__main__":
    main()
