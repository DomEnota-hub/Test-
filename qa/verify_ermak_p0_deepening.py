#!/usr/bin/env python3
import gzip
import json
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
TARGETS = {
    "ER-DIAG-003", "ER-DIAG-004", "ER-DIAG-005", "ER-DIAG-015",
    "ER-DIAG-041", "ER-DIAG-049", "ER-DIAG-050"
}
LEGACY = {"ER-DIAG-003", "ER-DIAG-004", "ER-DIAG-005", "ER-DIAG-015"}
GENERIC = "Отказ локальный (одна секция/узел) или общий?"

def load(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return json.load(f)

app = load(APP)
patch = load(PATCH)
assert app == patch, "app/patch diagnostics differ"
scenarios = app["scenarios"]
assert len(scenarios) == 136, len(scenarios)
by_id = {s["id"]: s for s in scenarios}
assert TARGETS <= by_id.keys()

for sid in sorted(TARGETS):
    s = by_id[sid]
    nodes = s["graph"]["nodes"]
    ids = {n["id"] for n in nodes}
    assert s["graph"]["startNodeId"] == "start"
    assert len(ids) == len(nodes)
    prompts = [n.get("prompt", "") for n in nodes if n.get("type") == "question"]
    assert GENERIC not in prompts, sid
    assert len(set(prompts)) >= 3, (sid, prompts)
    assert "profile-required" in ids, sid
    for n in nodes:
        if n.get("nextNodeId"):
            assert n["nextNodeId"] in ids, (sid, n)
        for c in n.get("choices", []):
            assert c["nextNodeId"] in ids, (sid, c)
    assert len(s.get("vl80sUiProjection", {}).get("probableCauses", [])) >= 5, sid
    assert len(s.get("vl80sUiProjection", {}).get("checks", [])) >= 3, sid

for sid in LEGACY:
    s = by_id[sid]
    assert "2010" in s.get("sourceAgeNote", ""), sid
    assert not any(n.get("type") == "source_action" for n in s["graph"]["nodes"]), sid

generic_count = sum(
    any(n.get("prompt") == GENERIC for n in s.get("graph", {}).get("nodes", []))
    for s in scenarios
)
print(f"ERMAK_STRICT_GENERIC_ROUTES={generic_count}")
assert generic_count <= 97, generic_count
print("ERMAK P0 BATCH1 CONTRACT PASS")
