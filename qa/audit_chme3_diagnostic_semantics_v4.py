#!/usr/bin/env python3
"""Semantic audit for ChME3 diagnostics.

The pass-3 decisionTree files are research/content candidates. Final runtime quality is
measured against explicit v2 graph overlays. This audit intentionally separates:
  * legacy-content quality hints (non-blocking during migration), and
  * strict validation of every v2 runtime graph that has been promoted.

Use --require-complete only at the final locomotive checkpoint.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, deque
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "docs" / "locomotives" / "chme3"

ALLOWED_NODE_TYPES = {
    "question", "check", "finding", "source_action", "emergency_action", "terminal"
}
ALLOWED_LEVELS = {"CAB", "SAFE_STOP", "AUTHORIZED_ONLY", "STOP"}
ACTION_NODE_TYPES = {"check", "source_action", "emergency_action"}
POLICY_ACTION_TYPES = {"source_action", "emergency_action"}

GENERIC_FRAGMENTS = (
    "проверить состояние оборудования",
    "проверить соединения",
    "проверить цепь управления",
    "проверить питание",
    "проверить согласно схеме",
    "доложить установленным порядком",
    "обратиться к обслуживающему персоналу",
)

TERMINAL_SIGNAL_WORDS = (
    "локализ", "вероят", "неисправ", "огранич", "прекрат", "останов", "долож",
    "недостаточно", "зафикс", "исключ", "разреш", "запрещ", "состояние"
)


def load(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def load_scenarios() -> dict[str, dict]:
    scenarios: dict[str, dict] = {}
    for path in sorted(PKG.glob("diagnostics_*_pass3.json")):
        for row in load(path).get("scenarios", []):
            sid = row["id"]
            if sid in scenarios:
                raise AssertionError(f"duplicate scenario {sid}")
            scenarios[sid] = row
    if not scenarios:
        raise AssertionError("no ChME3 scenarios found")
    return scenarios


def load_graphs() -> dict[str, dict]:
    graphs: dict[str, dict] = {}
    for path in sorted(PKG.glob("diagnostic_runtime_graphs_v2*.json")):
        data = load(path)
        for row in data.get("scenarios", []):
            sid = row["scenarioId"]
            if sid in graphs:
                raise AssertionError(f"duplicate runtime graph for {sid}")
            graphs[sid] = row["runtimeGraph"]
    return graphs


def legacy_quality(row: dict) -> tuple[int, list[str]]:
    tree = row.get("decisionTree", [])
    score = 0
    reasons: list[str] = []
    if len(tree) >= 4:
        score += 1
    else:
        reasons.append("<4 legacy steps")
    conditional = sum(bool(step.get("when")) for step in tree)
    explicit_split = sum(isinstance(step.get("next"), list) and len(step["next"]) >= 2 for step in tree)
    if conditional or explicit_split:
        score += 1
    else:
        reasons.append("no explicit discriminator/branch")
    text = " ".join(
        str(step.get(key, ""))
        for step in tree
        for key in ("check", "action", "result", "when")
    ).lower().replace("ё", "е")
    if any(token in text for token in (
        "давлен", "оборот", "позици", "реле", "контактор", "дым", "температур",
        "масл", "топлив", "воздух", "ток", "напряж", "сработ", "рейк", "звук",
        "стук", "вибрац", "утеч", "ламп", "сигнал"
    )):
        score += 1
    else:
        reasons.append("weak symptom-specific observation language")
    generic_hits = sum(fragment in text for fragment in GENERIC_FRAGMENTS)
    if generic_hits <= 1:
        score += 1
    else:
        reasons.append(f"generic phrase hits={generic_hits}")
    return score, reasons


def targets(node: dict) -> list[str]:
    out: list[str] = []
    nxt = node.get("nextNodeId")
    if nxt:
        out.append(nxt)
    for choice in node.get("choices", []):
        if choice.get("nextNodeId"):
            out.append(choice["nextNodeId"])
    uncertain = node.get("uncertainNextNodeId")
    if uncertain:
        out.append(uncertain)
    return out


def validate_graph(sid: str, graph: dict) -> list[str]:
    errors: list[str] = []
    nodes_list = graph.get("nodes", [])
    node_ids = [node.get("id") for node in nodes_list]
    if not nodes_list:
        return [f"{sid}: empty runtime graph"]
    if any(not node_id for node_id in node_ids):
        errors.append(f"{sid}: blank node id")
    if len(set(node_ids)) != len(node_ids):
        errors.append(f"{sid}: duplicate node id")
    nodes = {node["id"]: node for node in nodes_list if node.get("id")}
    start = graph.get("startNodeId")
    if start not in nodes:
        errors.append(f"{sid}: invalid startNodeId {start!r}")
        return errors

    for nid, node in nodes.items():
        ntype = node.get("type")
        if ntype not in ALLOWED_NODE_TYPES:
            errors.append(f"{sid}/{nid}: unsupported type {ntype!r}")
            continue
        if not str(node.get("text", "")).strip():
            errors.append(f"{sid}/{nid}: empty text")
        for target in targets(node):
            if target not in nodes:
                errors.append(f"{sid}/{nid}: missing target {target}")
        if ntype == "question":
            choices = node.get("choices", [])
            meaningful = [c for c in choices if c.get("label") and c.get("nextNodeId")]
            if len(meaningful) < 2:
                errors.append(f"{sid}/{nid}: question has <2 choices")
            labels = " ".join(str(c.get("label", "")).lower() for c in choices)
            has_uncertain = bool(node.get("uncertainNextNodeId")) or any(
                token in labels for token in ("не уверен", "не знаю", "неизвест", "нет данных")
            )
            if not has_uncertain:
                errors.append(f"{sid}/{nid}: no uncertainty path")
            unique_targets = {c.get("nextNodeId") for c in meaningful}
            if len(unique_targets) < 2:
                errors.append(f"{sid}/{nid}: cosmetic question; all choices have same target")
        if ntype in ACTION_NODE_TYPES:
            if node.get("actionLevel") not in ALLOWED_LEVELS:
                errors.append(f"{sid}/{nid}: missing/invalid actionLevel")
        if ntype == "check":
            if not str(node.get("expected", "")).strip():
                errors.append(f"{sid}/{nid}: check without expected result")
            if not str(node.get("ifAbnormal", "")).strip():
                errors.append(f"{sid}/{nid}: check without abnormal interpretation")
        if ntype in POLICY_ACTION_TYPES:
            policy = node.get("policy", {})
            if not policy.get("userFacingPolicy"):
                errors.append(f"{sid}/{nid}: policy action without userFacingPolicy")
            if policy.get("sourceBound") is not True:
                errors.append(f"{sid}/{nid}: policy action is not sourceBound")
            if not node.get("sourceRefs"):
                errors.append(f"{sid}/{nid}: policy action without sourceRefs")
        if ntype == "terminal":
            terminal_text = str(node.get("text", "")).lower().replace("ё", "е")
            if not any(word in terminal_text for word in TERMINAL_SIGNAL_WORDS):
                errors.append(f"{sid}/{nid}: terminal does not express a diagnostic/safety result")

    reachable: set[str] = set()
    queue = deque([start])
    distance = {start: 0}
    while queue:
        nid = queue.popleft()
        if nid in reachable or nid not in nodes:
            continue
        reachable.add(nid)
        for target in targets(nodes[nid]):
            if target in nodes and target not in distance:
                distance[target] = distance[nid] + 1
                queue.append(target)
    missing = set(nodes) - reachable
    if missing:
        errors.append(f"{sid}: unreachable nodes: {sorted(missing)}")

    reachable_terminals = [nid for nid in reachable if nodes[nid].get("type") == "terminal"]
    if not reachable_terminals:
        errors.append(f"{sid}: no reachable terminal")

    # Real diagnostic branching must occur early enough to influence the route.
    branch_nodes = [
        nid for nid in reachable
        if nodes[nid].get("type") == "question"
        and len({c.get("nextNodeId") for c in nodes[nid].get("choices", []) if c.get("nextNodeId")}) >= 2
    ]
    if not branch_nodes:
        errors.append(f"{sid}: no real branching question")
    elif min(distance.get(nid, 999) for nid in branch_nodes) > 3:
        errors.append(f"{sid}: first diagnostic branch occurs too late")

    # At least two materially different terminal outcomes or findings are expected when
    # a scenario branches, otherwise the branching is likely decorative.
    outcome_texts = {
        re.sub(r"\s+", " ", str(nodes[nid].get("text", "")).strip().lower())
        for nid in reachable_terminals
    }
    findings = [nid for nid in reachable if nodes[nid].get("type") == "finding"]
    if len(outcome_texts) < 2 and not findings:
        errors.append(f"{sid}: branch does not produce distinct findings/outcomes")

    return errors


def graph_signature(graph: dict) -> str:
    parts = []
    for node in graph.get("nodes", []):
        text = str(node.get("text", "")).lower().replace("ё", "е")
        text = re.sub(r"chme3-eq-[a-z0-9-]+", " equipment ", text)
        text = re.sub(r"\d+(?:[.,]\d+)?", " # ", text)
        words = re.findall(r"[a-zа-я]+", text)
        # Keep technical/semantic words but drop high-frequency instruction glue.
        stop = {
            "проверить", "уточнить", "если", "при", "после", "перед", "состояние",
            "работу", "работы", "действие", "действия", "наличие", "отсутствие", "далее",
            "выполнить", "зафиксировать", "тепловоза", "чмэ"
        }
        parts.extend(word for word in words if len(word) > 3 and word not in stop)
    return " ".join(parts)


def near_duplicate_pairs(graphs: dict[str, dict]) -> list[tuple[str, str, float]]:
    signatures = {sid: graph_signature(graph) for sid, graph in graphs.items()}
    result = []
    ids = sorted(signatures)
    for index, left in enumerate(ids):
        for right in ids[index + 1:]:
            if not signatures[left] or not signatures[right]:
                continue
            ratio = SequenceMatcher(None, signatures[left], signatures[right]).ratio()
            if ratio >= 0.88:
                result.append((left, right, ratio))
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-complete", action="store_true")
    args = parser.parse_args()

    scenarios = load_scenarios()
    graphs = load_graphs()
    unknown = sorted(set(graphs) - set(scenarios))
    if unknown:
        raise AssertionError(f"runtime graphs for unknown scenarios: {unknown}")

    errors: list[str] = []
    for sid, graph in graphs.items():
        errors.extend(validate_graph(sid, graph))

    duplicates = near_duplicate_pairs(graphs)
    for left, right, ratio in duplicates:
        errors.append(f"near-identical runtime graphs {left} / {right}: similarity={ratio:.3f}")

    legacy_scores = {}
    weak = []
    for sid, row in scenarios.items():
        score, reasons = legacy_quality(row)
        legacy_scores[sid] = score
        if score < 3:
            weak.append((sid, score, reasons))

    missing = sorted(set(scenarios) - set(graphs))
    print(
        f"ChME3 semantic audit: {len(scenarios)} source scenarios; "
        f"{len(graphs)} promoted runtime graphs; {len(missing)} awaiting migration; "
        f"legacy weak candidates={len(weak)}"
    )
    if weak:
        print("Legacy candidates requiring the most scrutiny:")
        for sid, score, reasons in sorted(weak)[:30]:
            print(f" - {sid}: score={score}/4; {', '.join(reasons)}")
    if missing:
        print("Awaiting v2 runtime graph promotion:")
        print(" ".join(missing))

    if args.require_complete and missing:
        errors.append(f"runtime migration incomplete: {len(missing)} scenarios missing v2 graphs")

    if errors:
        print("SEMANTIC DIAGNOSTIC CONTRACT FAIL", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("SEMANTIC DIAGNOSTIC CONTRACT PASS for all currently promoted graphs")
    if not args.require_complete:
        print("Migration mode: missing v2 graphs are reported but do not fail CI yet.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError) as exc:
        print(f"ChME3 semantic audit FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
