#!/usr/bin/env python3
"""Independent acceptance contract for all 136 Ermak diagnostic routes."""
from __future__ import annotations

import gzip
import hashlib
import json
import re
from collections import Counter, defaultdict, deque
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
ALLOWED_POLICIES = {
    "TRIAGE_ONLY_NO_REPAIR",
    "SOURCE_AND_PROFILE_REQUIRED",
    "SAFETY_GATE_REQUIRED",
    "EMERGENCY_SOURCE_BOUND",
}
ACTION_TYPES = {"source_action", "emergency_action"}


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as source:
        return json.load(source)


def edges(node: dict) -> list[str]:
    result = []
    if node.get("nextNodeId"):
        result.append(node["nextNodeId"])
    result.extend(choice["nextNodeId"] for choice in node.get("choices", []) if choice.get("nextNodeId"))
    return result


def graph_contract(scenario: dict) -> None:
    graph = scenario.get("graph", {})
    nodes = graph.get("nodes", [])
    ids = [node.get("id") for node in nodes]
    assert ids and len(ids) == len(set(ids)), (scenario["id"], "duplicate/missing node id")
    by_id = {node["id"]: node for node in nodes}
    start = graph.get("startNodeId")
    assert start in by_id, (scenario["id"], "invalid start", start)
    for node in nodes:
        for target in edges(node):
            assert target in by_id, (scenario["id"], node["id"], "invalid edge", target)

    reached = set()
    queue = deque([start])
    while queue:
        current = queue.popleft()
        if current in reached:
            continue
        reached.add(current)
        queue.extend(edges(by_id[current]))
    assert reached == set(ids), (scenario["id"], "unreachable", sorted(set(ids) - reached))

    state = {}
    def visit(node_id: str) -> None:
        assert state.get(node_id) != 1, (scenario["id"], "cycle", node_id)
        if state.get(node_id) == 2:
            return
        state[node_id] = 1
        for target in edges(by_id[node_id]):
            visit(target)
        state[node_id] = 2
    visit(start)

    for node in nodes:
        if node.get("type") == "terminal":
            assert node.get("result") and node.get("terminalStatus"), (scenario["id"], node["id"], "incomplete terminal")
        if node.get("type") == "question":
            assert node.get("prompt") and len(node.get("choices", [])) >= 2, (scenario["id"], node["id"], "weak question")


def normalized_question(text: str) -> str:
    text = text.lower().replace("ё", "е")
    text = re.sub(r"\b(?:да|нет|ли|есть|при|для|или|по|на|в|и|а|не)\b", " ", text)
    return " ".join(re.findall(r"[а-яa-z0-9№]+", text))


def source_contract(scenario: dict) -> None:
    refs = scenario.get("sourceRefs", [])
    assert refs, (scenario["id"], "no sourceRefs")
    for ref in refs:
        for field in ("sourceId", "document", "locator", "role", "sourceKind"):
            assert str(ref.get(field, "")).strip(), (scenario["id"], ref.get("sourceId"), "missing", field)
        version = ref.get("version", {})
        for field in ("label", "revision", "verifiedAt", "status"):
            assert str(version.get(field, "")).strip(), (scenario["id"], ref.get("sourceId"), "missing version", field)
        assert version["verifiedAt"] <= "2026-09-26", (scenario["id"], ref["sourceId"], "future verification date")
        if ref["sourceId"] == "ER-SRC-010":
            assert ref["role"] == "legacy_symptom_crosscheck_only", (scenario["id"], "671r promoted")
            assert version["status"] == "HISTORICAL", (scenario["id"], "671r not historical")
    assert scenario.get("sourceAudit", {}).get("auditedAt"), (scenario["id"], "missing sourceAudit")


app = load(APP)
patch = load(PATCH)
assert app == patch
assert APP.read_bytes() == PATCH.read_bytes()
scenarios = app.get("scenarios", [])
assert len(scenarios) == 136, len(scenarios)
scenario_ids = [scenario.get("id") for scenario in scenarios]
assert len(scenario_ids) == len(set(scenario_ids)) == 136
assert scenario_ids == [f"ER-DIAG-{index:03d}" for index in range(1, 137)]

exact_signatures = defaultdict(list)
semantic_signatures = defaultdict(list)
strict_generic = 0
for scenario in scenarios:
    graph_contract(scenario)
    source_contract(scenario)
    applicability = scenario.get("applicability", {})
    families = set(applicability.get("families", []))
    assert families and families <= {"2ES5K", "3ES5K"}, (scenario["id"], "family gate")
    assert applicability.get("profiles"), (scenario["id"], "missing profile gate")

    questions = [node for node in scenario["graph"]["nodes"] if node.get("type") == "question"]
    assert questions, (scenario["id"], "no distinguishing question")
    exact = tuple((node["prompt"].strip().lower(), tuple(choice.get("label", "").strip().lower() for choice in node.get("choices", []))) for node in questions)
    semantic = tuple(normalized_question(node["prompt"]) for node in questions)
    exact_signatures[exact].append(scenario["id"])
    semantic_signatures[semantic].append(scenario["id"])
    strict_generic += sum(node.get("prompt") == "Отказ локальный (одна секция/узел) или общий?" for node in questions)

    for node in scenario["graph"]["nodes"]:
        policy = node.get("userFacingPolicy")
        if policy:
            assert policy in ALLOWED_POLICIES, (scenario["id"], node["id"], "unknown policy", policy)
        if node.get("type") in ACTION_TYPES:
            assert policy in {"SOURCE_AND_PROFILE_REQUIRED", "SAFETY_GATE_REQUIRED", "EMERGENCY_SOURCE_BOUND"}, (scenario["id"], node["id"], "action bypasses policy")
            assert node.get("sourceBound") is True, (scenario["id"], node["id"], "action not source-bound")

duplicates = [ids for ids in exact_signatures.values() if len(ids) > 1]
semantic_duplicates = [ids for ids in semantic_signatures.values() if len(ids) > 1]
assert not duplicates, ("duplicate graph signatures", duplicates)
assert not semantic_duplicates, ("semantic generic signatures", semantic_duplicates)
assert strict_generic == 0, strict_generic

by_id = {scenario["id"]: scenario for scenario in scenarios}
for sid in ("ER-DIAG-059", "ER-DIAG-060", "ER-DIAG-064", *[f"ER-DIAG-{i:03d}" for i in range(81, 89)], "ER-DIAG-090", "ER-DIAG-091", "ER-DIAG-092", "ER-DIAG-121"):
    refs = by_id[sid]["sourceRefs"]
    current = [ref for ref in refs if ref["sourceId"].startswith("ER-AUDIT-NORM-BRAKES-2026")]
    assert current and current[0]["version"].get("effectiveFrom") == "2026-07-01", (sid, "stale brake basis")
    assert current[0]["version"]["status"] == "CURRENT_CONFIRMED", (sid, "brake basis not current")

all_source_ids = {ref["sourceId"] for scenario in scenarios for ref in scenario["sourceRefs"]}
assert "ER-SRC-034" not in all_source_ids, "ambiguous ER-SRC-034 remains"
assert "ER-AUDIT-NORM-2580R-2025" in all_source_ids

digest = hashlib.sha256(APP.read_bytes()).hexdigest()
print("ERMAK_FINAL_ACCEPTANCE=136/136")
print(f"ERMAK_STRICT_GENERIC={strict_generic}")
print(f"ERMAK_SEMANTIC_GENERIC={len(semantic_duplicates)}")
print(f"ERMAK_APP_PATCH_SHA256={digest}")
print("ERMAK FINAL ACCEPTANCE CONTRACT PASS")
