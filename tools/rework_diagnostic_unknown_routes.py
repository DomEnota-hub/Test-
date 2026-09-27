#!/usr/bin/env python3
"""Add explicit, question-specific UNKNOWN routing to the Ermak graph asset.

The transformer does not add operational actions.  It preserves the audited
YES/NO branches and source provenance, and inserts an uncertainty state that:

* records the exact observation that could not be established;
* keeps every non-excluded answer branch in the hypothesis set;
* continues to an existing independent question whenever the graph provides
  one; or
* produces a scenario-specific, source-bound limited result when no further
  user-safe discriminator exists.
"""

from __future__ import annotations

import gzip
import json
import re
from collections import deque
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/assets/technical/ermak_diagnostics.json.gz"
PATCH = ROOT / "patch/app/src/main/assets/technical/ermak_diagnostics.json.gz"

PURE_UNKNOWN_MARKERS = (
    "не уверен",
    "не знаю",
    "не определ",
    "не извест",
    "не могу",
    "не удалось",
    "не установлено",
    "не проверено",
    "не успел",
    "недостаточно данных",
)


def load() -> dict:
    with gzip.open(APP, "rt", encoding="utf-8") as source:
        return json.load(source)


def write(data: dict) -> None:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with APP.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=9) as packed:
            packed.write(payload)
    PATCH.write_bytes(APP.read_bytes())


def normalized(value: str) -> str:
    return " ".join(value.lower().replace("ё", "е").split())


def is_pure_unknown(label: str) -> bool:
    value = normalized(label)
    if value.startswith("нет /"):
        return False
    return any(marker in value for marker in PURE_UNKNOWN_MARKERS)


def split_mixed_unknown(label: str) -> tuple[str, bool]:
    value = normalized(label)
    if not value.startswith("нет /"):
        return label, False
    if any(marker in value for marker in PURE_UNKNOWN_MARKERS):
        return "Нет", True
    return label, False


def node_edges(node: dict) -> list[str]:
    result = []
    if node.get("nextNodeId"):
        result.append(node["nextNodeId"])
    result.extend(choice["nextNodeId"] for choice in node.get("choices", []) if choice.get("nextNodeId"))
    return result


def reachable_questions(start: str, by_id: dict[str, dict]) -> dict[str, int]:
    found: dict[str, int] = {}
    queue = deque([(start, 0)])
    visited: set[str] = set()
    while queue:
        node_id, distance = queue.popleft()
        if node_id in visited or node_id not in by_id:
            continue
        visited.add(node_id)
        node = by_id[node_id]
        if node.get("type") == "question":
            found[node_id] = distance
        for target in node_edges(node):
            queue.append((target, distance + 1))
    return found


def common_downstream_question(targets: list[str], by_id: dict[str, dict]) -> str | None:
    if not targets:
        return None
    reachable = [reachable_questions(target, by_id) for target in targets]
    common = set(reachable[0])
    for item in reachable[1:]:
        common.intersection_update(item)
    if not common:
        return None
    return min(common, key=lambda node_id: (sum(item[node_id] for item in reachable), node_id))


def can_reach(start: str, target: str, by_id: dict[str, dict]) -> bool:
    pending = [start]
    visited: set[str] = set()
    while pending:
        node_id = pending.pop()
        if node_id == target:
            return True
        if node_id in visited or node_id not in by_id:
            continue
        visited.add(node_id)
        pending.extend(node_edges(by_id[node_id]))
    return False


def list_text(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    result = []
    for item in value:
        if isinstance(item, str) and item.strip():
            result.append(item.strip())
        elif isinstance(item, dict):
            text = item.get("text") or item.get("title") or item.get("description")
            if isinstance(text, str) and text.strip():
                result.append(text.strip())
    return result


def projection_values(scenario: dict, key: str, fallback: str) -> list[str]:
    projection = scenario.get("vl80sUiProjection", {})
    values = list_text(projection.get(key))
    return values or list_text(scenario.get(fallback))


def compact(value: str, limit: int = 260) -> str:
    value = re.sub(r"\s+", " ", value).strip()
    return value if len(value) <= limit else value[: limit - 1].rstrip(" ,;:") + "…"


def scenario_support_result(scenario: dict, prompt: str, labels: list[str]) -> str:
    causes = projection_values(scenario, "probableCauses", "possibleCauses")[:3]
    checks = projection_values(scenario, "safeChecks", "observations")[:2]
    report = projection_values(scenario, "reportFields", "notes")[:3]
    parts = [
        f"В сценарии «{compact(str(scenario.get('title', scenario.get('id', ''))), 130)}» не удалось достоверно установить признак «{compact(prompt, 180)}».",
        "Не исключены варианты: " + "; ".join(compact(label, 120) for label in labels) + ".",
    ]
    if causes:
        parts.append("Остаются возможны: " + "; ".join(compact(item, 150) for item in causes) + ".")
    if checks:
        parts.append("Дальнейшее различение возможно по безопасно доступным признакам: " + "; ".join(compact(item, 170) for item in checks) + ".")
    if report:
        parts.append("В докладе зафиксировать: " + "; ".join(compact(item, 100) for item in report) + ".")
    parts.append("Неподтверждённый признак не использовать как основание для расширения действий или выбора исполнения оборудования.")
    return " ".join(parts)


def uncertainty_text(scenario_title: str, prompt: str, labels: list[str], next_prompt: str | None) -> str:
    parts = [
        f"В сценарии «{compact(scenario_title, 130)}» не удалось определить: «{compact(prompt, 190)}».",
        "Поэтому одновременно сохраняются варианты: " + "; ".join(compact(label, 125) for label in labels) + ".",
    ]
    if next_prompt:
        parts.append(f"Продолжите локализацию по независимому признаку: «{compact(next_prompt, 190)}».")
    else:
        parts.append("Доступные выводы и последующие безопасные проверки приведены в ограниченном результате этого сценария.")
    parts.append("Не выполняйте действие, для которого требовалось подтверждение этого признака.")
    return " ".join(parts)


def unique_id(base: str, occupied: set[str]) -> str:
    candidate = base
    suffix = 2
    while candidate in occupied:
        candidate = f"{base}-{suffix}"
        suffix += 1
    occupied.add(candidate)
    return candidate


def should_preserve_terminal(node: dict | None) -> bool:
    if not node or node.get("type") != "terminal":
        return False
    text = normalized(str(node.get("result") or node.get("text") or ""))
    status = normalized(str(node.get("terminalStatus") or ""))
    safety_markers = (
        "авар",
        "опас",
        "пожар",
        "движение запрещ",
        "не возобнов",
        "останов",
        "профил",
        "исполнение не подтвержден",
    )
    return any(marker in text or marker in status for marker in safety_markers)


def rework_scenario(scenario: dict) -> tuple[int, int]:
    graph = scenario.get("graph", {})
    nodes = graph.get("nodes", [])
    original_nodes = list(nodes)
    by_id = {node["id"]: node for node in original_nodes}
    order = {node["id"]: index for index, node in enumerate(original_nodes)}
    occupied = set(by_id)
    added: list[dict] = []
    question_count = 0
    support_count = 0

    for question in [node for node in original_nodes if node.get("type") == "question"]:
        question_count += 1
        original_choices = deepcopy(question.get("choices", []))
        retained: list[dict] = []
        unknown_target: str | None = None
        mixed_unknown_target: str | None = None

        for index, choice in enumerate(original_choices):
            label = str(choice.get("label", "")).strip()
            cleaned, was_mixed = split_mixed_unknown(label)
            if was_mixed:
                choice["label"] = cleaned
                mixed_unknown_target = choice.get("nextNodeId")
            if is_pure_unknown(label):
                unknown_target = choice.get("nextNodeId")
                continue
            choice["responseKind"] = "YES" if index == 0 else "NO" if index == 1 else "OPTION"
            retained.append(choice)

        if unknown_target is None:
            unknown_target = mixed_unknown_target

        labels = [str(choice.get("label", "")).strip() for choice in retained if str(choice.get("label", "")).strip()]
        branch_targets = [choice["nextNodeId"] for choice in retained if choice.get("nextNodeId")]
        continuation: str | None = None

        if unknown_target and unknown_target in by_id:
            if should_preserve_terminal(by_id[unknown_target]):
                continuation = unknown_target
            elif (
                by_id[unknown_target].get("type") != "terminal"
                and order.get(unknown_target, -1) > order[question["id"]]
            ):
                continuation = unknown_target
        if continuation is None:
            continuation = common_downstream_question(branch_targets, by_id)
            if continuation and can_reach(continuation, question["id"], by_id):
                continuation = None
        if continuation is None:
            later_questions = [
                node for node in original_nodes
                if node.get("type") == "question" and order[node["id"]] > order[question["id"]]
            ]
            if later_questions:
                continuation = next(
                    (
                        item["id"] for item in later_questions
                        if not can_reach(item["id"], question["id"], by_id)
                    ),
                    None,
                )
        uncertainty_id = unique_id(f"{question['id']}-unknown", occupied)
        if continuation is None:
            support_id = unique_id(f"{question['id']}-unknown-result", occupied)
            added.append({
                "id": support_id,
                "type": "terminal",
                "result": scenario_support_result(scenario, str(question.get("prompt", "")), labels),
                "terminalStatus": "UNRESOLVED_WITH_GUIDANCE",
            })
            continuation = support_id
            support_count += 1

        next_node = by_id.get(continuation)
        next_prompt = str(next_node.get("prompt", "")).strip() if next_node and next_node.get("type") == "question" else None
        added.append({
            "id": uncertainty_id,
            "type": "uncertainty",
            "text": uncertainty_text(
                str(scenario.get("title", scenario.get("id", ""))),
                str(question.get("prompt", "")),
                labels,
                next_prompt,
            ),
            "unresolvedOptions": labels,
            "nextNodeId": continuation,
        })
        retained.append({"label": "Не знаю", "nextNodeId": uncertainty_id, "responseKind": "UNKNOWN"})
        question["choices"] = retained

    nodes.extend(added)
    return question_count, support_count


def main() -> None:
    data = load()
    existing = data.get("unknownRoutingAudit", {})
    if existing.get("semantics") == "QUESTION_SPECIFIC_THREE_STATE":
        print(
            "ERMAK UNKNOWN REWORK: already applied "
            f"questions={existing.get('questionNodes')} "
            f"explicit_unknown={existing.get('explicitUnknownRoutes')}"
        )
        return
    total_questions = 0
    total_support = 0
    for scenario in data.get("scenarios", []):
        questions, support = rework_scenario(scenario)
        total_questions += questions
        total_support += support

    data["unknownRoutingAudit"] = {
        "version": "1",
        "semantics": "QUESTION_SPECIFIC_THREE_STATE",
        "questionNodes": total_questions,
        "explicitUnknownRoutes": total_questions,
        "limitedResults": total_support,
        "genericFallback": False,
        "safetyRule": "FAIL_CLOSED_WITH_CONTINUED_GUIDANCE",
    }
    write(data)
    print(
        f"ERMAK UNKNOWN REWORK: questions={total_questions} "
        f"explicit_unknown={total_questions} limited_results={total_support}"
    )


if __name__ == "__main__":
    main()
