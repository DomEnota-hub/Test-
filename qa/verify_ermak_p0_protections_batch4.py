#!/usr/bin/env python3
import gzip
import json
from pathlib import Path
from collections import deque

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {"ER-DIAG-098", "ER-DIAG-099", "ER-DIAG-100", "ER-DIAG-101", "ER-DIAG-102"}
GENERIC = "Отказ локальный (одна секция/узел) или общий?"


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return json.load(fh)


def reachable_nodes(scenario: dict) -> set[str]:
    nodes = {n["id"]: n for n in scenario["graph"]["nodes"]}
    start = scenario["graph"]["startNodeId"]
    seen: set[str] = set()
    queue = deque([start])
    while queue:
        node_id = queue.popleft()
        if node_id in seen:
            continue
        assert node_id in nodes, (scenario["id"], node_id)
        seen.add(node_id)
        node = nodes[node_id]
        next_id = node.get("nextNodeId")
        if next_id:
            queue.append(next_id)
        for choice in node.get("choices", []):
            target = choice.get("nextNodeId")
            if target:
                queue.append(target)
    return seen


app = load(APP)
patch = load(PATCH)
assert app == patch, "app/patch diagnostics differ"
scenarios = app["scenarios"]
assert len(scenarios) == 136, len(scenarios)
by_id = {s["id"]: s for s in scenarios}
assert TARGETS <= by_id.keys(), sorted(TARGETS - by_id.keys())

for sid in sorted(TARGETS):
    scenario = by_id[sid]
    nodes = scenario["graph"]["nodes"]
    ids = {n["id"] for n in nodes}
    assert len(ids) == len(nodes), f"{sid}: duplicate node ids"
    assert scenario["graph"]["startNodeId"] == "start", sid
    assert reachable_nodes(scenario) == ids, (sid, sorted(ids - reachable_nodes(scenario)))

    prompts = [n.get("prompt", "") for n in nodes if n.get("type") == "question"]
    assert GENERIC not in prompts, sid
    assert len(set(prompts)) >= 3, (sid, prompts)
    assert not any(n.get("type") == "source_action" for n in nodes), sid
    assert "profile-required" in ids, sid

    projection = scenario.get("vl80sUiProjection", {})
    assert len(projection.get("probableCauses", [])) >= 5, sid
    assert len(projection.get("checks", [])) >= 3, sid
    assert len(projection.get("prohibited", [])) >= 3, sid

# Силовая земля и земля вспомогательных цепей должны оставаться разными маршрутами.
assert "силов" in by_id["ER-DIAG-098"]["title"].lower()
assert "вспомог" in by_id["ER-DIAG-099"]["title"].lower()
assert by_id["ER-DIAG-098"]["graph"] != by_id["ER-DIAG-099"]["graph"]

# МТЗ ГВ обязана различать момент включения ГВ, запуск вспомогательной нагрузки и тягу.
p100 = " ".join(
    n.get("prompt", "") for n in by_id["ER-DIAG-100"]["graph"]["nodes"] if n.get("type") == "question"
).lower()
assert "включения гв" in p100, p100
assert "вспомогатель" in p100, p100
assert "тяги" in p100 or "тягов" in p100, p100

# Круговой огонь — аварийный маршрут.
flash = by_id["ER-DIAG-101"]
assert flash.get("severity") == "STOP_AND_REPORT", flash.get("severity")
assert flash.get("display", {}).get("emergencyPriority") is True
assert any(n.get("terminalStatus") == "stop_and_report" for n in flash["graph"]["nodes"])

# Противобоксовка: обязательно отделять реальное боксование от канала скорости и требовать профиль.
p102 = " ".join(
    n.get("prompt", "") for n in by_id["ER-DIAG-102"]["graph"]["nodes"] if n.get("type") == "question"
).lower()
assert "фактичес" in p102 and ("боксован" in p102 or "юз" in p102), p102
assert "скорост" in p102, p102
assert "профил" in p102, p102
text102 = " ".join(
    by_id["ER-DIAG-102"].get("vl80sUiProjection", {}).get("immediateActions", [])
    + by_id["ER-DIAG-102"].get("vl80sUiProjection", {}).get("prohibited", [])
).lower()
assert "универсаль" in text102, "ER-DIAG-102 must forbid universal thresholds"

strict_generic = sum(
    any(n.get("prompt") == GENERIC for n in s.get("graph", {}).get("nodes", []))
    for s in scenarios
)
print(f"ERMAK_STRICT_GENERIC_ROUTES={strict_generic}")
assert strict_generic <= 80, strict_generic
print("ERMAK P0 PROTECTIONS BATCH4 CONTRACT PASS")
