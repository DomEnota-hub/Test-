#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "docs" / "locomotives" / "chme3"
FAMILY = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family"
COMMON = FAMILY / "common"
CHME3E = FAMILY / "chme3e"


def load(path: Path):
    if not path.exists():
        raise AssertionError(f"missing file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def unique(values, label: str):
    seen, dup = set(), set()
    for value in values:
        if value in seen:
            dup.add(value)
        seen.add(value)
    if dup:
        raise AssertionError(f"duplicate {label}: {sorted(dup)}")
    return seen


def collect(pattern: str, field: str):
    rows, paths = [], sorted(LEGACY.glob(pattern))
    if not paths:
        raise AssertionError(f"no legacy files for {pattern}")
    for path in paths:
        rows.extend(load(path).get(field, []))
    return paths, rows


def validate_new_graph(scenario_id: str, graph: dict):
    allowed_types = {"question", "check", "finding", "source_action", "emergency_action", "terminal"}
    allowed_levels = {"CAB", "SAFE_STOP", "AUTHORIZED_ONLY", "STOP"}
    nodes = graph.get("nodes", [])
    if not nodes:
        raise AssertionError(f"{scenario_id}: empty runtime graph")
    by_id = {}
    for node in nodes:
        nid = node.get("id")
        if not nid or nid in by_id:
            raise AssertionError(f"{scenario_id}: missing/duplicate node id {nid}")
        by_id[nid] = node
        ntype = node.get("type")
        if ntype not in allowed_types or not str(node.get("text", "")).strip():
            raise AssertionError(f"{scenario_id}/{nid}: bad node type/text")
        if ntype == "question":
            if len(node.get("choices", [])) < 2 or not node.get("uncertainNextNodeId"):
                raise AssertionError(f"{scenario_id}/{nid}: question lacks discrimination/uncertainty")
        if ntype == "check":
            if node.get("actionLevel") not in allowed_levels:
                raise AssertionError(f"{scenario_id}/{nid}: invalid/missing actionLevel")
            for key in ("expected", "ifAbnormal", "nextNodeId"):
                if not node.get(key):
                    raise AssertionError(f"{scenario_id}/{nid}: check has no {key}")
        if ntype in {"source_action", "emergency_action"}:
            if node.get("sourceBound") is not True or not node.get("sourceRefs") or not node.get("userFacingPolicy"):
                raise AssertionError(f"{scenario_id}/{nid}: new action node is not strictly source-bound")

    start = graph.get("startNodeId")
    if start not in by_id:
        raise AssertionError(f"{scenario_id}: invalid start node {start}")
    if sum(n.get("type") == "terminal" for n in nodes) < 2:
        raise AssertionError(f"{scenario_id}: fewer than 2 terminal outcomes")

    edges = {nid: [] for nid in by_id}
    for nid, node in by_id.items():
        refs = []
        if node.get("nextNodeId"):
            refs.append(node["nextNodeId"])
        if node.get("uncertainNextNodeId"):
            refs.append(node["uncertainNextNodeId"])
        for choice in node.get("choices", []):
            if not choice.get("label") or not choice.get("nextNodeId"):
                raise AssertionError(f"{scenario_id}/{nid}: malformed choice")
            refs.append(choice["nextNodeId"])
        for ref in refs:
            if ref not in by_id:
                raise AssertionError(f"{scenario_id}/{nid}: dangling edge {ref}")
            edges[nid].append(ref)

    reached, stack = set(), [start]
    while stack:
        nid = stack.pop()
        if nid in reached:
            continue
        reached.add(nid)
        stack.extend(edges[nid])
    if reached != set(by_id):
        raise AssertionError(f"{scenario_id}: unreachable nodes {sorted(set(by_id)-reached)}")

    runtime_text = " ".join(str(n.get("text", "")).lower() for n in nodes)
    for phrase in ("поставить перемычку", "установить перемычку", "обойти защиту", "замкнуть провода"):
        if phrase in runtime_text:
            raise AssertionError(f"{scenario_id}: unsafe runtime instruction: {phrase}")


def main() -> int:
    manifest = load(COMMON / "diagnostics_manifest.json")
    if manifest.get("stage") != "STAGE_3_DIAGNOSTICS_ASSISTANT_SEMANTICS":
        raise AssertionError("wrong family diagnostic stage")
    if manifest.get("integrationState") != "NOT_INTEGRATION_READY_UNTIL_STAGE_4":
        raise AssertionError("Stage 3 must not be integration-ready")

    scenario_paths, legacy_rows = collect("diagnostics_*_pass3.json", "scenarios")
    legacy_ids = unique((r.get("id") for r in legacy_rows), "legacy scenario IDs")
    expected = manifest["legacySharedCorpus"]["expectedScenarioCount"]
    if len(legacy_ids) != expected or any(not re.fullmatch(r"CHME3-DIAG-\d{3}", sid or "") for sid in legacy_ids):
        raise AssertionError(f"legacy scenario inventory invalid: {len(legacy_ids)} expected {expected}")

    graph_paths, legacy_graph_rows = collect("diagnostic_runtime_graphs_v2*.json", "scenarios")
    legacy_graph_ids = unique((r.get("scenarioId") for r in legacy_graph_rows), "legacy runtime IDs")
    required = manifest["legacySharedCorpus"]["requiredRuntimeCoverage"]
    if len(legacy_graph_ids) != required or legacy_graph_ids != legacy_ids:
        raise AssertionError(
            f"legacy runtime coverage mismatch: scenarios={len(legacy_ids)} graphs={len(legacy_graph_ids)} "
            f"missing={sorted(legacy_ids-legacy_graph_ids)} extra={sorted(legacy_graph_ids-legacy_ids)}"
        )
    # The legacy graph semantics/action-node contract is intentionally owned by
    # audit_chme3_diagnostic_semantics_v4.py --require-complete, executed immediately
    # before this validator in CI. Do not retroactively require new fields from v2 legacy data.

    shared_ids = set()
    for path in (LEGACY / "atlas_index.json", LEGACY / "atlas_index_extension.json"):
        for row in load(path).get("equipment", []):
            if "ЧМЭ3Э" in row.get("applicability", []):
                shared_ids.add(row["id"])
    variant_ids = {r["id"] for r in load(CHME3E / "atlas_variant.json").get("equipment", [])}
    equipment_ids = shared_ids | variant_ids
    scheme_ids = {r["id"] for r in load(CHME3E / "schemes_variant.json").get("records", [])}

    source_ids = set()
    for path in (
        CHME3E / "source_registry.json", CHME3E / "source_registry_stage2.json",
        LEGACY / "source_registry.json", LEGACY / "diagnostic_source_registry_addendum.json",
    ):
        source_ids.update(r["id"] for r in load(path).get("sources", []))

    diag_rows = load(CHME3E / "diagnostics_stage3.json").get("scenarios", [])
    diag_ids = unique((r.get("id") for r in diag_rows), "CHME3E scenario IDs")
    if len(diag_ids) != 12:
        raise AssertionError(f"expected 12 CHME3E scenarios, got {len(diag_ids)}")

    forbidden = {"CHME3-DIAG-110", "CHME3-SYS-EDB", "CHME3T-EQ-EDB-CONTROL", "CHME3T-EQ-BRAKE-RESISTORS", "CHME3T-EQ-BRAKE-RESISTOR-FAN"}
    active_refs = set()
    for row in diag_rows:
        sid = row.get("id")
        if not re.fullmatch(r"CHME3E-DIAG-\d{3}", sid or ""):
            raise AssertionError(f"bad CHME3E scenario id {sid}")
        if row.get("profiles") != ["ЧМЭ3Э"] or row.get("profileId") != "chme3e-electronic":
            raise AssertionError(f"{sid}: profile leakage")
        if row.get("actionAuthority") not in {"TRIAGE_ONLY_NO_REPAIR", "SAFETY_GATE_REQUIRED"}:
            raise AssertionError(f"{sid}: excessive authority")
        if len(row.get("symptoms", [])) < 3 or len(row.get("queryTerms", [])) < 3:
            raise AssertionError(f"{sid}: weak search semantics")
        if not row.get("prohibited") or not row.get("safetyBoundary"):
            raise AssertionError(f"{sid}: missing safety boundary")
        for eq in row.get("equipmentIds", []):
            active_refs.add(eq)
            if eq not in equipment_ids:
                raise AssertionError(f"{sid}: unknown equipmentId {eq}")
        for ref in row.get("schemeRefs", []):
            if ref not in scheme_ids:
                raise AssertionError(f"{sid}: unknown schemeRef {ref}")
        for ref in row.get("evidenceRefs", []):
            if ref not in source_ids:
                raise AssertionError(f"{sid}: unknown evidenceRef {ref}")
        if row.get("provenanceStatus") in {"FIELD_PRACTICE", "UNVERIFIED", "CONFLICT"} and row.get("actionAuthority") != "TRIAGE_ONLY_NO_REPAIR":
            raise AssertionError(f"{sid}: weak evidence grants authority")
    if active_refs & forbidden:
        raise AssertionError(f"CHME3T EDB leaked into CHME3E: {sorted(active_refs & forbidden)}")

    runtime_rows = load(CHME3E / "diagnostic_runtime_graphs_v2.json").get("scenarios", [])
    runtime_ids = unique((r.get("scenarioId") for r in runtime_rows), "CHME3E runtime IDs")
    if runtime_ids != diag_ids:
        raise AssertionError(f"CHME3E runtime mismatch: missing={sorted(diag_ids-runtime_ids)} extra={sorted(runtime_ids-diag_ids)}")
    for row in runtime_rows:
        validate_new_graph(row["scenarioId"], row.get("runtimeGraph", {}))
    for sid in ("CHME3E-DIAG-008", "CHME3E-DIAG-010"):
        graph = next(r["runtimeGraph"] for r in runtime_rows if r["scenarioId"] == sid)
        if not any(n.get("actionLevel") == "STOP" for n in graph.get("nodes", [])):
            raise AssertionError(f"{sid}: missing STOP safety gate")

    semantics = load(CHME3E / "assistant_semantics_stage3.json")
    routes = semantics.get("scenarioRouting", [])
    route_ids = unique((r.get("scenarioId") for r in routes), "assistant route IDs")
    if route_ids != diag_ids:
        raise AssertionError("assistant route coverage does not match CHME3E diagnostics")
    for route in routes:
        if len(route.get("positive", [])) < 3 or not route.get("negative"):
            raise AssertionError(f"{route.get('scenarioId')}: weak positive/negative routing")
    spoken = semantics.get("spokenCorpus", [])
    if len(spoken) < 12 or any(r.get("expectedScenarioId") not in diag_ids for r in spoken):
        raise AssertionError("spoken corpus coverage invalid")
    opposite = semantics.get("oppositeEventPairs", [])
    if len(opposite) < 8:
        raise AssertionError("fewer than 8 opposite-event pairs")
    mismatch = semantics.get("profileMismatchRules", [])
    mismatch_text = json.dumps(mismatch, ensure_ascii=False).lower()
    if not mismatch or "PROFILE_MISMATCH_CLARIFY" not in {r.get("result") for r in mismatch}:
        raise AssertionError("CHME3E EDB mismatch must fail closed")
    for token in ("реостат", "эдт", "chme3-diag-110"):
        if token not in mismatch_text:
            raise AssertionError(f"EDB mismatch rule missing {token}")
    if "CHME3-DIAG-110" in route_ids or "CHME3-DIAG-110" in {r.get("expectedScenarioId") for r in spoken}:
        raise AssertionError("CHME3T EDB is actively routed for CHME3E")

    print(
        "CHME3 FAMILY STAGE 3 OK: "
        f"legacy={len(legacy_ids)}/{len(legacy_graph_ids)} runtime-v2 across {len(scenario_paths)}+{len(graph_paths)} files; "
        f"CHME3E={len(diag_ids)}/{len(runtime_ids)} profile scenarios/runtime-v2; "
        f"assistant routes={len(route_ids)}, spoken={len(spoken)}, oppositePairs={len(opposite)}; EDB isolation PASS"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError, StopIteration) as exc:
        print(f"CHME3 FAMILY STAGE 3 FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
