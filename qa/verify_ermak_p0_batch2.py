#!/usr/bin/env python3
import gzip
import json
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {
    "ER-DIAG-040", "ER-DIAG-051", "ER-DIAG-052", "ER-DIAG-053",
    "ER-DIAG-054", "ER-DIAG-055", "ER-DIAG-056", "ER-DIAG-136"
}
GENERIC = "Отказ локальный (одна секция/узел) или общий?"


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return json.load(fh)


app = load(APP)
patch = load(PATCH)
assert app == patch, "app/patch diagnostics differ"
scenarios = app["scenarios"]
assert len(scenarios) == 136, len(scenarios)
by_id = {scenario["id"]: scenario for scenario in scenarios}
assert TARGETS <= by_id.keys(), sorted(TARGETS - by_id.keys())

for sid in sorted(TARGETS):
    scenario = by_id[sid]
    nodes = scenario["graph"]["nodes"]
    ids = {node["id"] for node in nodes}
    assert scenario["graph"]["startNodeId"] == "start", sid
    assert len(ids) == len(nodes), f"{sid}: duplicate node ids"
    prompts = [node.get("prompt", "") for node in nodes if node.get("type") == "question"]
    assert GENERIC not in prompts, sid
    assert len(set(prompts)) >= 3, (sid, prompts)
    assert "profile-required" in ids, sid
    for node in nodes:
        next_id = node.get("nextNodeId")
        if next_id:
            assert next_id in ids, (sid, node["id"], next_id)
        for choice in node.get("choices", []):
            assert choice["nextNodeId"] in ids, (sid, node["id"], choice)
    projection = scenario.get("vl80sUiProjection", {})
    assert len(projection.get("probableCauses", [])) >= 5, sid
    assert len(projection.get("checks", [])) >= 3, sid

mismatch = by_id["ER-DIAG-136"]
text = " ".join(
    mismatch.get("vl80sUiProjection", {}).get("immediateActions", [])
    + mismatch.get("vl80sUiProjection", {}).get("prohibited", [])
).lower()
assert "универсаль" in text, "ER-DIAG-136 must explicitly forbid a universal current-difference threshold"

generic_count = sum(
    any(node.get("prompt") == GENERIC for node in scenario.get("graph", {}).get("nodes", []))
    for scenario in scenarios
)
print(f"ERMAK_STRICT_GENERIC_ROUTES={generic_count}")
assert generic_count <= 89, generic_count
print("ERMAK P0 BATCH2 CONTRACT PASS")
