#!/usr/bin/env python3
import gzip
import json
from collections import deque
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {"ER-DIAG-089", "ER-DIAG-093", "ER-DIAG-094", "ER-DIAG-095", "ER-DIAG-096", "ER-DIAG-097"}
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
    assert "profile-required" in ids, sid
    assert not any(n.get("type") == "source_action" for n in nodes), sid
    projection = scenario.get("vl80sUiProjection", {})
    assert len(projection.get("probableCauses", [])) >= 5, sid
    assert len(projection.get("checks", [])) >= 3, sid
    assert len(projection.get("prohibited", [])) >= 3, sid
    signatures[sid] = tuple(prompts)

assert len(set(signatures.values())) == len(TARGETS), "batch7a contains duplicate question trees"

p089 = " ".join(signatures["ER-DIAG-089"]).lower()
assert "эпк" in p089 and "комплекс" in p089 and ("тормож" in p089 or "тормозн" in p089)

p093 = " ".join(signatures["ER-DIAG-093"]).lower()
assert "клуб" in p093 and "индикац" in p093 and ("питан" in p093 or "обмен" in p093)

p094 = " ".join(signatures["ER-DIAG-094"]).lower()
assert "саут" in p094 and "тя" in p094 and ("мсуд" in p094 or "интерфейс" in p094)

p095 = " ".join(signatures["ER-DIAG-095"]).lower()
assert "тскбм" in p095 and ("индикац" in p095 or "комплекс" in p095)

p096 = " ".join(signatures["ER-DIAG-096"]).lower()
assert "код" in p096 and ("принима" in p096 or "приём" in p096) and ("индикац" in p096 or "самоконтрол" in p096)

p097 = " ".join(signatures["ER-DIAG-097"]).lower()
assert "2эс5к" in p097 and "блок" in p097 and ("клуб" in p097 or "саут" in p097 or "тскбм" in p097)
blok = by_id["ER-DIAG-097"]
assert blok["applicability"]["families"] == ["2ES5K"], blok["applicability"]
assert blok["applicability"]["profiles"] == ["safety_blok_confirmed_2es5k"], blok["applicability"]
assert blok["applicability"]["variantSelectionRequired"] is True
assert any(ref.get("sourceId") == "ER-SRC-031" for ref in blok.get("sourceRefs", []))
assert "3ЭС5К" in blok.get("sourceAgeNote", "")

strict_generic = sum(
    any(n.get("prompt") == GENERIC for n in s.get("graph", {}).get("nodes", []))
    for s in scenarios
)
print(f"ERMAK_STRICT_GENERIC_ROUTES={strict_generic}")
assert strict_generic <= 59, strict_generic
print("ERMAK P0 SAFETY BATCH7A CONTRACT PASS")
