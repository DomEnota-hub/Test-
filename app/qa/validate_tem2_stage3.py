#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from collections import Counter, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "docs/locomotives/diesel/tem2-family"
MODEL = BASE / "common/stage3_model.json"
STAGE2 = BASE / "common/stage2_model.json"
SOURCES = BASE / "common/source_registry.json"

EXPECTED_SCENARIOS = 24
EXPECTED_ROUTE_CASES = 24
EXPECTED_SPOKEN = 24
EXPECTED_OPPOSITE = 9
ALLOWED_PROFILES = {"tem2-base", "tem2u-improved"}
TEM2U_ONLY = {"TEM2-DIAG-022", "TEM2-DIAG-023"}
ADJACENT_TOKENS = ("tem2um", "tem2t", "tem2a", "1pd-4a", "1пд-4а", "rheostatic brake", "реостатный тормоз")
UNSAFE_POSITIVE_PATTERNS = (
    "установить перемыч",
    "поставить перемыч",
    "заклинить муфт",
    "отключить реле заземления",
    "включить контактор принудительно",
    "принудительно включить контактор",
    "подать внешнее питание на",
)


def fail(msg: str) -> None:
    raise AssertionError(msg)


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def graph_reachable(graph: dict) -> set[str]:
    nodes = graph.get("nodes") or []
    by_id = {n.get("id"): n for n in nodes}
    start = graph.get("startNodeId")
    if not start or start not in by_id:
        fail("runtime graph missing/invalid startNodeId")
    seen: set[str] = set()
    queue = deque([start])
    while queue:
        nid = queue.popleft()
        if nid in seen:
            continue
        seen.add(nid)
        node = by_id[nid]
        targets: list[str] = []
        if node.get("nextNodeId"):
            targets.append(node["nextNodeId"])
        if node.get("uncertainNextNodeId"):
            targets.append(node["uncertainNextNodeId"])
        for choice in node.get("choices") or []:
            if choice.get("nextNodeId"):
                targets.append(choice["nextNodeId"])
        for target in targets:
            if target not in by_id:
                fail(f"dangling runtime target {target}")
            queue.append(target)
    return seen


def main() -> int:
    model = load(MODEL)
    stage2 = load(STAGE2)
    sources = load(SOURCES)

    if model.get("stage") != "STAGE_3_DIAGNOSTICS_ASSISTANT_SEMANTICS":
        fail("wrong Stage 3 state")
    if model.get("classification") != "SOURCE_BACKED_TRIAGE_LOCALIZATION":
        fail("Stage 3 classification drift")

    policy = model.get("policy") or {}
    required_false = [
        "bypassProtectionAllowed",
        "temporaryJumpersRuntimeAllowed",
        "forcedContactorOperationRuntimeAllowed",
        "sealedDeviceInterventionRuntimeAllowed",
        "externalPowerConnectionInstructionsAllowed",
        "fieldPracticeAuthorizesActions",
        "historicalManualActionTextIsCurrentAuthority",
    ]
    for key in required_false:
        if policy.get(key) is not False:
            fail(f"unsafe/missing policy flag: {key}")
    if policy.get("unknownExecution") != "FAIL_CLOSED":
        fail("unknown execution must fail closed")

    scenarios = model.get("scenarios") or []
    if len(scenarios) != EXPECTED_SCENARIOS:
        fail(f"unexpected scenario count: {len(scenarios)}")
    scenario_ids = [s.get("id") for s in scenarios]
    if len(set(scenario_ids)) != len(scenario_ids) or any(not x for x in scenario_ids):
        fail("duplicate/blank scenario ID")
    if set(x for x in scenario_ids if x in TEM2U_ONLY) != TEM2U_ONLY:
        fail("TEM2U profile scenarios missing")

    foundation_source_ids = {s.get("id") for s in sources.get("sources") or []}
    research = model.get("researchEvidence") or []
    research_ids = {s.get("id") for s in research}
    if len(research_ids) != len(research) or any(not x for x in research_ids):
        fail("research evidence IDs invalid")
    for src in research:
        if src.get("provenanceStatus") == "FIELD_PRACTICE" and src.get("authorityForAction") != "NO":
            fail(f"field practice gained authority: {src.get('id')}")
    evidence_ids = foundation_source_ids | research_ids

    equipment_rows = stage2.get("equipment") or []
    equipment_by_id = {row[0]: row for row in equipment_rows}
    scheme_by_id = {s.get("id"): s for s in stage2.get("schemes") or []}

    risk_counts = Counter()
    common_count = tem2u_only_count = 0
    all_runtime_text: list[str] = []

    for scenario in scenarios:
        sid = scenario["id"]
        profiles = set(scenario.get("profileIds") or [])
        if not profiles or not profiles <= ALLOWED_PROFILES:
            fail(f"{sid}: invalid profiles {profiles}")
        if sid in TEM2U_ONLY:
            if profiles != {"tem2u-improved"}:
                fail(f"{sid}: TEM2U-only scenario leaked")
            tem2u_only_count += 1
        else:
            if profiles != ALLOWED_PROFILES:
                fail(f"{sid}: common scenario must cover both profiles")
            common_count += 1

        if not scenario.get("category") or not scenario.get("riskClass") or not scenario.get("actionAuthority"):
            fail(f"{sid}: missing metadata")
        risk_counts[scenario["riskClass"]] += 1

        refs = scenario.get("evidenceRefs") or []
        if not refs or any(ref not in evidence_ids for ref in refs):
            fail(f"{sid}: unresolved evidence ref")

        eq_ids = scenario.get("equipmentIds") or []
        if not eq_ids or any(eid not in equipment_by_id for eid in eq_ids):
            fail(f"{sid}: unresolved equipment ref")
        for eid in eq_ids:
            eq_profiles = set(equipment_by_id[eid][3])
            if not eq_profiles & profiles:
                fail(f"{sid}: equipment {eid} has no applicability overlap")
        for profile in profiles:
            if not any(profile in set(equipment_by_id[eid][3]) for eid in eq_ids):
                fail(f"{sid}: no equipment applicable to {profile}")

        scheme_refs = scenario.get("schemeRefs") or []
        if not scheme_refs or any(ref not in scheme_by_id for ref in scheme_refs):
            fail(f"{sid}: unresolved scheme ref")
        for ref in scheme_refs:
            if not set(scheme_by_id[ref].get("app") or []) & profiles:
                fail(f"{sid}: scheme {ref} has no applicability overlap")

        if len(scenario.get("symptoms") or []) < 2 or len(scenario.get("queries") or []) < 2:
            fail(f"{sid}: weak symptom/query vocabulary")

        graph = scenario.get("runtimeGraph") or {}
        nodes = graph.get("nodes") or []
        node_ids = [n.get("id") for n in nodes]
        if len(node_ids) != len(set(node_ids)) or any(not x for x in node_ids):
            fail(f"{sid}: duplicate/blank runtime node")
        reachable = graph_reachable(graph)
        if reachable != set(node_ids):
            fail(f"{sid}: unreachable runtime nodes: {set(node_ids) - reachable}")

        terminal_count = 0
        question_count = 0
        for node in nodes:
            ntype = node.get("type")
            if ntype == "question":
                question_count += 1
                choices = node.get("choices") or []
                if len(choices) < 2:
                    fail(f"{sid}/{node.get('id')}: question lacks discriminator")
                if not node.get("uncertainNextNodeId") and sid not in {"TEM2-DIAG-004", "TEM2-DIAG-005", "TEM2-DIAG-016"}:
                    fail(f"{sid}/{node.get('id')}: uncertainty route missing")
            elif ntype == "check":
                if node.get("actionLevel") not in {"CAB", "SAFE_STOP", "AUTHORIZED_ONLY", "STOP"}:
                    fail(f"{sid}/{node.get('id')}: invalid/missing actionLevel")
                for key in ("text", "expected", "ifAbnormal"):
                    if not node.get(key):
                        fail(f"{sid}/{node.get('id')}: check missing {key}")
            elif ntype == "terminal":
                terminal_count += 1
                if not node.get("text"):
                    fail(f"{sid}/{node.get('id')}: empty terminal")
            else:
                fail(f"{sid}/{node.get('id')}: unsupported node type {ntype}")
        if question_count < 1 or terminal_count < 2:
            fail(f"{sid}: graph not sufficiently discriminating")

        runtime_text = json.dumps(graph, ensure_ascii=False).lower()
        all_runtime_text.append(runtime_text)
        for unsafe in UNSAFE_POSITIVE_PATTERNS:
            if unsafe in runtime_text:
                fail(f"{sid}: unsafe operational instruction leaked: {unsafe}")

    if common_count != 22 or tem2u_only_count != 2:
        fail(f"profile scenario split drift: common={common_count}, tem2u_only={tem2u_only_count}")

    active_text = "\n".join(all_runtime_text).lower()
    for token in ADJACENT_TOKENS:
        # Adjacent variants may appear only in assistant negative-routing metadata, never active runtime graphs.
        if token in active_text:
            fail(f"adjacent variant leaked into runtime graph: {token}")

    semantics = model.get("assistantSemantics") or {}
    routes = semantics.get("routeCases") or []
    spoken = semantics.get("spokenQueries") or []
    opposite = semantics.get("oppositeEventPairs") or []
    if len(routes) != EXPECTED_ROUTE_CASES or len(spoken) != EXPECTED_SPOKEN or len(opposite) != EXPECTED_OPPOSITE:
        fail("assistant corpus count drift")

    scenario_set = set(scenario_ids)
    for label, rows in (("route", routes), ("spoken", spoken)):
        for row in rows:
            if len(row) != 2 or row[1] not in scenario_set or not row[0].strip():
                fail(f"invalid {label} case: {row}")
    if {row[1] for row in routes} != scenario_set:
        fail("route cases do not cover every scenario exactly at least once")
    if {row[1] for row in spoken} != scenario_set:
        fail("spoken corpus does not cover every scenario")

    for pair in opposite:
        if len(pair) != 4 or pair[2] not in scenario_set or pair[3] not in scenario_set:
            fail(f"invalid opposite-event pair: {pair}")
        if pair[0].strip().lower() == pair[1].strip().lower():
            fail("opposite-event pair collapsed")

    negative = semantics.get("negativeRouting") or []
    neg_text = json.dumps(negative, ensure_ascii=False).lower()
    for required in ("tem2t", "1пд-4а", "ask_confirm_actual_execution"):
        if required not in neg_text:
            fail(f"negative routing missing boundary: {required}")

    print(
        "TEM2 STAGE 3 PASS: "
        f"scenarios={len(scenarios)} common={common_count} tem2u_only={tem2u_only_count}; "
        f"routes={len(routes)} spoken={len(spoken)} opposite_pairs={len(opposite)}; "
        f"research_sources={len(research)}; runtime_graphs={len(scenarios)}; profile_isolation=OK"
    )
    print("risk_classes=" + ",".join(f"{k}:{v}" for k, v in sorted(risk_counts.items())))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"TEM2 STAGE 3 FAIL: {exc}", file=sys.stderr)
        raise
