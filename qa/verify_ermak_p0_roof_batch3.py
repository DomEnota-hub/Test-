#!/usr/bin/env python3
from __future__ import annotations

import gzip
import json
from collections import deque
from pathlib import Path

ASSET = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {
    "ER-DIAG-002",
    "ER-DIAG-030",
    "ER-DIAG-115",
    "ER-DIAG-122",
    "ER-DIAG-129",
    "ER-DIAG-130",
}
LEGACY_PROMPTS = {
    "Симптом действительно соответствует описанию сценария?",
    "Отказ локальный (одна секция/узел) или общий?",
    "Есть ли опасные признаки: дым/огонь/дуга, разрушение, сильный перегрев, крупная утечка, отказ торможения или угроза безопасности движения?",
}
FORBIDDEN_ACTION_FRAGMENTS = (
    "зашунтировать",
    "перемычк",
    "включить вручную",
    "принудительно включ",
)


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return json.load(fh)


def reachable(graph: dict) -> set[str]:
    nodes = {n["id"]: n for n in graph.get("nodes", [])}
    start = graph.get("startNodeId")
    if start not in nodes:
        raise AssertionError(f"missing start node {start}")
    seen: set[str] = set()
    queue = deque([start])
    while queue:
        node_id = queue.popleft()
        if node_id in seen:
            continue
        seen.add(node_id)
        node = nodes[node_id]
        nxt = node.get("nextNodeId")
        if nxt:
            if nxt not in nodes:
                raise AssertionError(f"{node_id}: missing next {nxt}")
            queue.append(nxt)
        for choice in node.get("choices", []):
            target = choice.get("nextNodeId")
            if target not in nodes:
                raise AssertionError(f"{node_id}: missing choice target {target}")
            queue.append(target)
    return seen


def main() -> None:
    if ASSET.read_bytes() != PATCH.read_bytes():
        raise AssertionError("Ermak app/patch assets differ")
    root = load(ASSET)
    scenarios = {s["id"]: s for s in root.get("scenarios", [])}
    if len(scenarios) != 136:
        raise AssertionError(f"expected 136 scenarios, got {len(scenarios)}")
    missing = TARGETS - set(scenarios)
    if missing:
        raise AssertionError(f"missing targets: {sorted(missing)}")

    starts: dict[str, str] = {}
    for scenario_id in sorted(TARGETS):
        scenario = scenarios[scenario_id]
        graph = scenario.get("graph") or {}
        nodes = graph.get("nodes", [])
        node_ids = {n["id"] for n in nodes}
        if len(node_ids) != len(nodes):
            raise AssertionError(f"{scenario_id}: duplicate node ids")
        seen = reachable(graph)
        if seen != node_ids:
            raise AssertionError(f"{scenario_id}: unreachable nodes {sorted(node_ids - seen)}")
        if any(n.get("type") == "source_action" for n in nodes):
            raise AssertionError(f"{scenario_id}: old source_action remains user-reachable")
        prompts = [n.get("prompt", "") for n in nodes if n.get("type") == "question"]
        legacy = LEGACY_PROMPTS.intersection(prompts)
        if legacy:
            raise AssertionError(f"{scenario_id}: legacy generic prompts remain: {sorted(legacy)}")
        if not prompts:
            raise AssertionError(f"{scenario_id}: no diagnostic questions")
        starts[scenario_id] = prompts[0]

        projection = scenario.get("vl80sUiProjection") or {}
        prohibited = " ".join(projection.get("prohibited", [])).lower()
        if "не " not in prohibited:
            raise AssertionError(f"{scenario_id}: missing explicit prohibitions")
        visible_text = " ".join(
            projection.get("immediateActions", [])
            + projection.get("checks", []) if False else []
        )
        # Search graph/projection text for old bypass-style user instructions.
        serialized = json.dumps({"graph": graph, "projection": projection}, ensure_ascii=False).lower()
        for fragment in FORBIDDEN_ACTION_FRAGMENTS:
            if fragment in serialized and "не " + fragment not in serialized:
                raise AssertionError(f"{scenario_id}: unsafe action fragment {fragment!r}")

    if len(set(starts.values())) != len(starts):
        raise AssertionError(f"duplicate start questions: {starts}")

    panto = scenarios["ER-DIAG-002"]
    panto_text = json.dumps(panto["graph"], ensure_ascii=False).lower()
    for token in ("цепей управления", "пневмат", "ввк"):
        if token not in panto_text:
            raise AssertionError(f"ER-DIAG-002 missing discriminator {token}")

    vvk_text = json.dumps(scenarios["ER-DIAG-122"]["graph"], ensure_ascii=False).lower()
    if "фактически" not in vvk_text or "подтверж" not in vvk_text:
        raise AssertionError("ER-DIAG-122 must distinguish physical closure from confirmation")

    meter_text = json.dumps(scenarios["ER-DIAG-129"]["graph"], ensure_ascii=False).lower()
    if "независим" not in meter_text or "физичес" not in meter_text:
        raise AssertionError("ER-DIAG-129 must distinguish indication from physical state")

    for scenario_id in ("ER-DIAG-030", "ER-DIAG-115", "ER-DIAG-130"):
        graph = scenarios[scenario_id]["graph"]
        if not any(n.get("terminalStatus") == "stop_and_report" for n in graph.get("nodes", [])):
            raise AssertionError(f"{scenario_id}: missing stop_and_report terminal")

    print("ERMAK_ROOF_BATCH3_TARGETS=" + ",".join(sorted(TARGETS)))
    print("ERMAK_ROOF_BATCH3_REACHABILITY=PASS")
    print("ERMAK_ROOF_BATCH3_LEGACY_GENERIC=0")
    print("ERMAK_ROOF_BATCH3_UNSAFE_SOURCE_ACTION=0")


if __name__ == "__main__":
    main()
