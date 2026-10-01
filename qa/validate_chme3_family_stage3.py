#!/usr/bin/env python3
from __future__ import annotations

import glob
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
    values = list(values)
    seen = set()
    dup = set()
    for value in values:
        if value in seen:
            dup.add(value)
        seen.add(value)
    if dup:
        raise AssertionError(f"duplicate {label}: {sorted(dup)}")
    return set(values)


def collect_legacy_scenarios():
    rows = []
    paths = sorted(LEGACY.glob("diagnostics_*_pass3.json"))
    if not paths:
        raise AssertionError("legacy diagnostic pass-3 files missing")
    for path in paths:
        doc = load(path)
        rows.extend(doc.get("scenarios", []))
    return paths, rows


def collect_legacy_runtime():
    rows = []
    paths = sorted(LEGACY.glob("diagnostic_runtime_graphs_v2*.json"))
    if not paths:
        raise AssertionError("legacy v2 runtime graph files missing")
    for path in paths:
        doc = load(path)
        if doc.get("schemaVersion") != 2:
            raise AssertionError(f"runtime schema is not v2: {path.name}")
        rows.extend(doc.get("scenarios", []))
    return paths, rows


def validate_graph(scenario_id: str, graph: dict):
    allowed_types = {"question", "check", "finding", "source_action", "emergency_action", "terminal"}
    allowed_levels = {"CAB", "SAFE_STOP", "AUTHORIZED_ONLY", "STOP"}
    nodes = graph.get("nodes", [])
    if not nodes:
        raise AssertionError(f"{scenario_id}: empty runtime graph")
    by_id = {}
    for node in nodes:
        nid = node.get("id")
        if not nid:
            raise AssertionError(f"{scenario_id}: node without id")
        if nid in by_id:
            raise AssertionError(f"{scenario_id}: duplicate node id {nid}")
        by_id[nid] = node
        if node.get("type") not in allowed_types:
            raise AssertionError(f"{scenario_id}/{nid}: unsupported type {node.get('type')}")
        if not str(node.get("text", "")).strip():
            raise AssertionError(f"{scenario_id}/{nid}: empty text")
        if node.get("type") == "question":
            choices = node.get("choices", [])
            if len(choices) < 2:
                raise AssertionError(f"{scenario_id}/{nid}: question has <2 choices")
            if not node.get("uncertainNextNodeId"):
                raise AssertionError(f"{scenario_id}/{nid}: no uncertainty branch")
        if node.get("type") == "check":
            if node.get("actionLevel") not in allowed_levels:
                raise AssertionError(f"{scenario_id}/{nid}: invalid/missing actionLevel")
            for key in ("expected", "ifAbnormal", "nextNodeId"):
                if not node.get(key):
                    raise AssertionError(f"{scenario_id}/{nid}: check has no {key}")
        if node.get("type") in {"source_action", "emergency_action"}:
            if node.get("sourceBound") is not True or not node.get("sourceRefs") or not node.get("userFacingPolicy"):
                raise AssertionError(f"{scenario_id}/{nid}: action node is not strictly source-bound")

    start = graph.get("startNodeId")
    if start not in by_id:
        raise AssertionError(f"{scenario_id}: missing start node {start}")

    terminals = [n for n in nodes if n.get("type") == "terminal"]
    if len(terminals) < 2:
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
                raise AssertionError(f"{scenario_id}/{nid}: dangling edge to {ref}")
            edges[nid].append(ref)

    reached = set()
    stack = [start]
    while stack:
        nid = stack.pop()
        if nid in reached:
            continue
        reached.add(nid)
        stack.extend(edges[nid])
    unreachable = sorted(set(by_id) - reached)
    if unreachable:
        raise AssertionError(f"{scenario_id}: unreachable nodes {unreachable}")

    text = " ".join(str(n.get("text", "")).lower() for n in nodes)
    unsafe_positive = ["поставить перемычку", "установить перемычку", "обойти защиту", "замкнуть провода"]
    for phrase in unsafe_positive:
        if phrase in text:
            raise AssertionError(f"{scenario_id}: unsafe runtime instruction: {phrase}")


def main() -> int:
    manifest = load(COMMON / "diagnostics_manifest.json")
    if manifest.get("stage") != "STAGE_3_DIAGNOSTICS_ASSISTANT_SEMANTICS":
        raise AssertionError("wrong family diagnostic manifest stage")
    if manifest.get("integrationState") != "NOT_INTEGRATION_READY_UNTIL_STAGE_4":
        raise AssertionError("Stage 3 must not mark package integration-ready")

    legacy_paths, legacy_rows = collect_legacy_scenarios()
    legacy_ids = unique((r.get("id") for r in legacy_rows), "legacy scenario IDs")
    expected_legacy = manifest["legacySharedCorpus"]["expectedScenarioCount"]
    if len(legacy_ids) != expected_legacy:
        raise AssertionError(f"legacy scenario count mismatch: {len(legacy_ids)} != {expected_legacy}")
    for sid in legacy_ids:
        if not re.fullmatch(r"CHME3-DIAG-\d{3}", sid or ""):
            raise AssertionError(f"bad legacy scenario id: {sid}")

    runtime_paths, runtime_rows = collect_legacy_runtime()
    runtime_ids = unique((r.get("scenarioId") for r in runtime_rows), "legacy runtime scenario IDs")
    required_runtime = manifest["legacySharedCorpus"]["requiredRuntimeCoverage"]
    if len(runtime_ids) != required_runtime or runtime_ids != legacy_ids:
        raise AssertionError(
            f"legacy v2 runtime coverage mismatch: scenarios={len(legacy_ids)}, graphs={len(runtime_ids)}, "
            f"missing={sorted(legacy_ids-runtime_ids)}, extra={sorted(runtime_ids-legacy_ids)}"
        )

    for row in runtime_rows:
        validate_graph(row["scenarioId"], row.get("runtimeGraph", {}))

    atlas_shared = load(LEGACY / "atlas_index.json")
    atlas_ext = load(LEGACY / "atlas_index_extension.json")
    atlas_variant = load(CHME3E / "atlas_variant.json")
    shared_equipment = []
    for doc in (atlas_shared, atlas_ext):
        for row in doc.get("equipment", []):
            if "ЧМЭ3Э" in row.get("applicability", []):
                shared_equipment.append(row["id"])
    variant_equipment = [r["id"] for r in atlas_variant.get("equipment", [])]
    equipment_ids = set(shared_equipment) | set(variant_equipment)

    schemes = load(CHME3E / "schemes_variant.json")
    scheme_ids = {r["id"] for r in schemes.get("records", [])}

    sources = []
    for path in (
        CHME3E / "source_registry.json",
        CHME3E / "source_registry_stage2.json",
        LEGACY / "source_registry.json",
        LEGACY / "diagnostic_source_registry_addendum.json",
    ):
        sources.extend(load(path).get("sources", []))
    source_ids = {r["id"] for r in sources}

    diag = load(CHME3E / "diagnostics_stage3.json")
    diag_rows = diag.get("scenarios", [])
    diag_ids = unique((r.get("id") for r in diag_rows), "CHME3E scenario IDs")
    expected_variant_count = 12
    if len(diag_ids) != expected_variant_count:
        raise AssertionError(f"expected {expected_variant_count} CHME3E scenarios, got {len(diag_ids)}")

    allowed_authority = {"TRIAGE_ONLY_NO_REPAIR", "SAFETY_GATE_REQUIRED"}
    forbidden_refs = {
        "CHME3-DIAG-110", "CHME3-SYS-EDB", "CHME3T-EQ-EDB-CONTROL",
        "CHME3T-EQ-BRAKE-RESISTORS", "CHME3T-EQ-BRAKE-RESISTOR-FAN"
    }
    active_refs = set()
    for row in diag_rows:
        sid = row.get("id")
        if not re.fullmatch(r"CHME3E-DIAG-\d{3}", sid or ""):
            raise AssertionError(f"bad CHME3E scenario id: {sid}")
        if row.get("profiles") != ["ЧМЭ3Э"] or row.get("profileId") != "chme3e-electronic":
            raise AssertionError(f"{sid}: not strictly CHME3E-only")
        if row.get("actionAuthority") not in allowed_authority:
            raise AssertionError(f"{sid}: authority exceeds Stage 3 policy")
        if len(row.get("symptoms", [])) < 3 or len(row.get("queryTerms", [])) < 3:
            raise AssertionError(f"{sid}: insufficient search semantics")
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

    leaked = sorted(active_refs & forbidden_refs)
    if leaked:
        raise AssertionError(f"CHME3T EDB leaked into CHME3E diagnostics: {leaked}")

    variant_runtime = load(CHME3E / "diagnostic_runtime_graphs_v2.json")
    vrows = variant_runtime.get("scenarios", [])
    vruntime_ids = unique((r.get("scenarioId") for r in vrows), "CHME3E runtime IDs")
    if vruntime_ids != diag_ids:
        raise AssertionError(f"CHME3E runtime coverage mismatch: missing={sorted(diag_ids-vruntime_ids)}, extra={sorted(vruntime_ids-diag_ids)}")
    for row in vrows:
        validate_graph(row["scenarioId"], row.get("runtimeGraph", {}))

    # High-risk external supply and stuck-start scenarios must force at least one STOP gate.
    for required_stop in ("CHME3E-DIAG-008", "CHME3E-DIAG-010"):
        graph = next(r["runtimeGraph"] for r in vrows if r["scenarioId"] == required_stop)
        if not any(n.get("actionLevel") == "STOP" for n in graph.get("nodes", [])):
            raise AssertionError(f"{required_stop}: missing STOP safety gate")

    semantics = load(CHME3E / "assistant_semantics_stage3.json")
    routes = semantics.get("scenarioRouting", [])
    route_ids = unique((r.get("scenarioId") for r in routes), "assistant route scenario IDs")
    if route_ids != diag_ids:
        raise AssertionError(f"assistant route coverage mismatch: missing={sorted(diag_ids-route_ids)}, extra={sorted(route_ids-diag_ids)}")
    for route in routes:
        if len(route.get("positive", [])) < 3 or not route.get("negative"):
            raise AssertionError(f"{route.get('scenarioId')}: weak positive/negative routing corpus")

    spoken = semantics.get("spokenCorpus", [])
    if len(spoken) < len(diag_ids):
        raise AssertionError("spoken corpus does not cover all CHME3E scenarios")
    for row in spoken:
        if row.get("expectedScenarioId") not in diag_ids:
            raise AssertionError(f"spoken corpus points to unknown scenario: {row}")

    opposite = semantics.get("oppositeEventPairs", [])
    if len(opposite) < 8:
        raise AssertionError("fewer than 8 opposite-event pairs")

    mismatch = semantics.get("profileMismatchRules", [])
    if not mismatch:
        raise AssertionError("no CHME3E profile mismatch rule")
    mismatch_text = json.dumps(mismatch, ensure_ascii=False).lower()
    for token in ("реостат", "эдт", "chme3-diag-110"):
        if token not in mismatch_text:
            raise AssertionError(f"CHME3E EDB mismatch rule missing {token}")
    if "PROFILE_MISMATCH_CLARIFY" not in {r.get("result") for r in mismatch}:
        raise AssertionError("EDB profile mismatch is not fail-closed")

    # No active CHME3E route is allowed to target the CHME3T EDB scenario.
    if "CHME3-DIAG-110" in route_ids or "CHME3-DIAG-110" in {r.get("expectedScenarioId") for r in spoken}:
        raise AssertionError("CHME3T EDB scenario routed as active CHME3E diagnostic")

    print(
        "CHME3 FAMILY STAGE 3 OK: "
        f"legacy={len(legacy_ids)}/{len(runtime_ids)} scenarios/runtime-v2 across {len(legacy_paths)} scenario files and {len(runtime_paths)} graph files; "
        f"CHME3E={len(diag_ids)}/{len(vruntime_ids)} profile scenarios/runtime-v2; "
        f"assistant routes={len(route_ids)}, spoken={len(spoken)}, oppositePairs={len(opposite)}; EDB isolation PASS"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError, StopIteration) as exc:
        print(f"CHME3 FAMILY STAGE 3 FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
