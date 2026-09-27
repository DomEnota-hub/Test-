#!/usr/bin/env python3
"""Acceptance checks for three-state diagnostic routing in Ermak assets."""

from __future__ import annotations

import gzip
import hashlib
import json
import re
from collections import Counter
from pathlib import Path


APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as source:
        return json.load(source)


def normalized(text: str) -> str:
    return " ".join(re.findall(r"[а-яa-z0-9№]+", text.lower().replace("ё", "е")))


app = load(APP)
patch = load(PATCH)
assert app == patch
assert APP.read_bytes() == PATCH.read_bytes()

questions = 0
unknown_routes = 0
limited_results = 0
uncertainty_to_terminal = 0
uncertainty_texts: Counter[str] = Counter()
support_texts: Counter[str] = Counter()

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

for scenario in app["scenarios"]:
    graph = scenario["graph"]
    nodes = graph["nodes"]
    by_id = {node["id"]: node for node in nodes}
    for node in nodes:
        if node.get("terminalStatus") == "UNRESOLVED_WITH_GUIDANCE":
            limited_results += 1
            result = str(node.get("result", "")).strip()
            assert scenario["title"] in result, (scenario["id"], node["id"], "support not scenario-specific")
            assert "Остаются возможны" in result or "Не исключены варианты" in result
            assert "В докладе" in result
            support_texts[normalized(result)] += 1

        if node.get("type") != "question":
            continue
        questions += 1
        unknown = [choice for choice in node.get("choices", []) if choice.get("responseKind") == "UNKNOWN"]
        assert len(unknown) == 1, (scenario["id"], node["id"], "UNKNOWN choice count", len(unknown))
        assert unknown[0].get("label") == "Не знаю", (scenario["id"], node["id"], "label")
        target = unknown[0].get("nextNodeId")
        assert target in by_id, (scenario["id"], node["id"], "missing UNKNOWN target", target)
        assert target != node["id"], (scenario["id"], node["id"], "UNKNOWN self-loop")
        uncertainty = by_id[target]
        assert uncertainty.get("type") == "uncertainty", (scenario["id"], node["id"], "UNKNOWN bypasses uncertainty")
        assert scenario["title"] in uncertainty.get("text", ""), (scenario["id"], node["id"], "not scenario-specific")
        assert uncertainty.get("unresolvedOptions"), (scenario["id"], node["id"], "lost hypotheses")
        assert len(uncertainty["unresolvedOptions"]) == len(node.get("choices", [])) - 1
        next_target = uncertainty.get("nextNodeId")
        assert next_target in by_id, (scenario["id"], node["id"], "missing continuation", next_target)
        assert next_target not in {node["id"], target}, (scenario["id"], node["id"], "UNKNOWN loop")
        next_node = by_id[next_target]
        if next_node.get("type") == "terminal":
            uncertainty_to_terminal += 1
            if next_node.get("terminalStatus") != "UNRESOLVED_WITH_GUIDANCE":
                terminal_text = normalized(str(next_node.get("result") or next_node.get("text") or ""))
                terminal_status = normalized(str(next_node.get("terminalStatus") or ""))
                assert any(marker in terminal_text or marker in terminal_status for marker in safety_markers), (
                    scenario["id"], node["id"], "unjustified UNKNOWN terminal", next_target
                )
        uncertainty_texts[normalized(uncertainty.get("text", ""))] += 1
        unknown_routes += 1

assert len(app["scenarios"]) == 136
assert questions == 725, questions
assert unknown_routes == questions
assert limited_results > 0
assert not [text for text, count in uncertainty_texts.items() if count > 1], "generic UNKNOWN uncertainty text"
assert not [text for text, count in support_texts.items() if count > 1], "generic UNKNOWN support result"
audit = app.get("unknownRoutingAudit", {})
assert audit.get("questionNodes") == questions
assert audit.get("explicitUnknownRoutes") == questions
assert audit.get("genericFallback") is False

print("ERMAK_UNKNOWN_SCENARIOS=136")
print(f"ERMAK_UNKNOWN_QUESTION_NODES={questions}")
print(f"ERMAK_UNKNOWN_ROUTES={unknown_routes}")
print(f"ERMAK_UNKNOWN_TO_JUSTIFIED_TERMINAL={uncertainty_to_terminal}")
print(f"ERMAK_UNKNOWN_LIMITED_RESULTS={limited_results}")
print("ERMAK_SEMANTIC_GENERIC_UNKNOWN=0")
print(f"ERMAK_UNKNOWN_APP_PATCH_SHA256={hashlib.sha256(APP.read_bytes()).hexdigest()}")
print("ERMAK UNKNOWN ROUTING CONTRACT PASS")
