#!/usr/bin/env python3
import gzip
import json
from collections import deque
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {f"ER-DIAG-{n:03d}" for n in range(74, 81)}
GENERIC = "Отказ локальный (одна секция/узел) или общий?"


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return json.load(fh)


def reachable(scenario: dict) -> set[str]:
    nodes = {n["id"]: n for n in scenario["graph"]["nodes"]}
    queue = deque([scenario["graph"]["startNodeId"]])
    seen: set[str] = set()
    while queue:
        node_id = queue.popleft()
        if node_id in seen:
            continue
        assert node_id in nodes, (scenario["id"], node_id)
        seen.add(node_id)
        node = nodes[node_id]
        if node.get("nextNodeId"):
            queue.append(node["nextNodeId"])
        for c in node.get("choices", []):
            queue.append(c["nextNodeId"])
    return seen


app = load(APP)
patch = load(PATCH)
assert app == patch, "app/patch diagnostics differ"
scenarios = app["scenarios"]
assert len(scenarios) == 136, len(scenarios)
by_id = {s["id"]: s for s in scenarios}
assert TARGETS <= by_id.keys(), sorted(TARGETS - by_id.keys())

signatures = {}
for sid in sorted(TARGETS):
    scenario = by_id[sid]
    nodes = scenario["graph"]["nodes"]
    ids = {n["id"] for n in nodes}
    assert len(ids) == len(nodes), f"{sid}: duplicate node ids"
    assert scenario["graph"]["startNodeId"] == "start"
    assert reachable(scenario) == ids, (sid, sorted(ids - reachable(scenario)))
    prompts = [n.get("prompt", "") for n in nodes if n.get("type") == "question"]
    assert GENERIC not in prompts, sid
    assert len(set(prompts)) >= 3, (sid, prompts)
    assert not any(n.get("type") == "source_action" for n in nodes), sid
    assert "profile-required" in ids, sid
    projection = scenario.get("vl80sUiProjection", {})
    assert len(projection.get("probableCauses", [])) >= 5, sid
    assert len(projection.get("checks", [])) >= 3, sid
    assert len(projection.get("prohibited", [])) >= 3, sid
    signatures[sid] = tuple(prompts)

assert len(set(signatures.values())) == len(TARGETS), "batch5 contains duplicate question trees"

# 074: production versus leak/backflow/measurement.
p074 = " ".join(signatures["ER-DIAG-074"]).lower()
assert "компрессор" in p074 and "давлен" in p074 and ("утеч" in p074 or "обратн" in p074)

# 075: frequent cycle must separate loss of air from regulator/control.
p075 = " ".join(signatures["ER-DIAG-075"]).lower()
assert "после штатного отключения" in p075 and "корот" in p075

# 076: safety valve route must ask whether compressor actually stops.
p076 = " ".join(signatures["ER-DIAG-076"]).lower()
assert "предохранитель" in p076 and "компрессор отключается" in p076

# 077: check valve requires confirmed reverse flow, not pressure loss alone.
p077 = " ".join(signatures["ER-DIAG-077"]).lower()
assert "обратн" in p077 and "нагнетатель" in p077

# 078: purge separates command, passage and excess condensate.
p078 = " ".join(signatures["ER-DIAG-078"]).lower()
assert "продув" in p078 and "конденсат" in p078 and ("замерз" in p078 or "засор" in p078)

# 079: inter-section leak must use section pressure difference and physical connection.
p079 = " ".join(signatures["ER-DIAG-079"]).lower()
assert "межсекцион" in p079 and "различ" in p079

# 080: distinguish general air reserve, control reservoir and individual consumers.
p080 = " ".join(signatures["ER-DIAG-080"]).lower()
assert "главн" in p080 and "цеп" in p080 and "токопри" in p080 and "гв" in p080

strict_generic = sum(
    any(n.get("prompt") == GENERIC for n in s.get("graph", {}).get("nodes", []))
    for s in scenarios
)
print(f"ERMAK_STRICT_GENERIC_ROUTES={strict_generic}")
assert strict_generic <= 73, strict_generic
print("ERMAK P0 PNEUMATICS BATCH5 CONTRACT PASS")
