#!/usr/bin/env python3
import gzip
import json
from collections import deque
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {f"ER-DIAG-{n:03d}" for n in range(81, 89)}
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
        for choice in node.get("choices", []):
            queue.append(choice["nextNodeId"])
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
    assert scenario["graph"]["startNodeId"] == "start", sid
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

assert len(set(signatures.values())) == len(TARGETS), "batch6 contains duplicate question trees"

p081 = " ".join(signatures["ER-DIAG-081"]).lower()
assert "тормоз" in p081 and "давлен" in p081 and ("цилиндр" in p081 or "тц" in p081)

p082 = " ".join(signatures["ER-DIAG-082"]).lower()
assert "одна тележ" in p082 and "давлен" in p082

p083 = " ".join(signatures["ER-DIAG-083"]).lower()
assert "после" in p083 and "отпуск" in p083 and "давлен" in p083

p084 = " ".join(signatures["ER-DIAG-084"]).lower()
assert "тормоз" in p084 and "магистрал" in p084 and "эпк" in p084 and ("электр" in p084 or "замещ" in p084)

p085 = " ".join(signatures["ER-DIAG-085"]).lower()
assert "прямодейств" in p085 and "автомат" in p085

p086 = " ".join(signatures["ER-DIAG-086"]).lower()
assert "автомат" in p086 and "магистрал" in p086 and "тц" in p086

p087 = " ".join(signatures["ER-DIAG-087"]).lower()
assert "резерв" in p087 and "130" in p087

p088 = " ".join(signatures["ER-DIAG-088"]).lower()
assert "электр" in p088 and "замещ" in p088 and "тц" in p088

strict_generic = sum(
    any(n.get("prompt") == GENERIC for n in s.get("graph", {}).get("nodes", []))
    for s in scenarios
)
print(f"ERMAK_STRICT_GENERIC_ROUTES={strict_generic}")
assert strict_generic <= 65, strict_generic
print("ERMAK P0 BRAKES BATCH6 CONTRACT PASS")
