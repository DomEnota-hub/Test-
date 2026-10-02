#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "docs/locomotives/diesel/tem2-family"
MODEL = BASE / "common/stage2_model.json"
SOURCES = BASE / "common/source_registry.json"
PROFILES = BASE / "common/profile_contract.json"
EXPECTED = {
    "systems": 9,
    "equipment": 36,
    "schemes": 9,
    "flows": 11,
    "flow_steps": 42,
    "views": 17,
    "hotspots": 91,
    "tem2_equipment": 33,
    "tem2u_equipment": 35,
}
PROFILES_ALLOWED = {"tem2-base", "tem2u-improved"}
DENIED_TOKENS = ("rheostatic", "реостат", "1pd-4a", "1пд-4а", "tem2um-eq", "tem2t-eq", "tem2a-eq")


def fail(msg: str) -> None:
    raise AssertionError(msg)


def unique(rows, get_id, label: str):
    ids = [get_id(row) for row in rows]
    if any(not x for x in ids):
        fail(f"{label}: blank id")
    if len(ids) != len(set(ids)):
        fail(f"{label}: duplicate id")
    return set(ids)


def main() -> int:
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    registry = json.loads(SOURCES.read_text(encoding="utf-8"))
    profile_contract = json.loads(PROFILES.read_text(encoding="utf-8"))

    if model.get("classification") != "SOURCE_BACKED_FUNCTIONAL":
        fail("Stage 2 precision must remain SOURCE_BACKED_FUNCTIONAL")
    if not model.get("precisionBoundary"):
        fail("precision boundary missing")
    if model.get("runtimeSafety", {}).get("stage2ProvidesExecutableActions") is not False:
        fail("Stage 2 must not create executable actions")
    if model.get("runtimeSafety", {}).get("fieldPracticeAuthorizesActions") is not False:
        fail("field practice must not authorize actions")
    if model.get("runtimeSafety", {}).get("externalPowerConnectionInstructions") is not False:
        fail("external power connection instructions are forbidden")

    systems = model["systems"]
    equipment = model["equipment"]
    schemes = model["schemes"]
    flows = model["flows"]
    system_ids = unique(systems, lambda x: x[0], "systems")
    equipment_ids = unique(equipment, lambda x: x[0], "equipment")
    scheme_ids = unique(schemes, lambda x: x["id"], "schemes")
    unique(flows, lambda x: x[0], "flows")

    if len(systems) != EXPECTED["systems"] or len(equipment) != EXPECTED["equipment"]:
        fail("unexpected system/equipment count")
    if len(schemes) != EXPECTED["schemes"] or len(flows) != EXPECTED["flows"]:
        fail("unexpected scheme/flow count")

    source_ids = {s["id"] for s in registry.get("sources", [])}
    groups = model.get("sourceGroups", {})
    for group, refs in groups.items():
        if not refs or any(ref not in source_ids for ref in refs):
            fail(f"source group {group}: unresolved source")

    equipment_by_id = {}
    for row in equipment:
        if len(row) != 5:
            fail(f"equipment row must have five fields: {row[0] if row else '?'}")
        eid, _, system_id, apps, source_groups = row
        equipment_by_id[eid] = row
        if system_id not in system_ids:
            fail(f"{eid}: unknown system {system_id}")
        if not apps or not set(apps) <= PROFILES_ALLOWED:
            fail(f"{eid}: invalid applicability")
        if not source_groups or any(g not in groups for g in source_groups):
            fail(f"{eid}: missing/unresolved provenance group")

    scheme_by_id = {s["id"]: s for s in schemes}
    for scheme in schemes:
        apps = set(scheme.get("app", []))
        if not apps or not apps <= PROFILES_ALLOWED:
            fail(f"{scheme['id']}: invalid profile scope")
        if scheme.get("domain") not in {"ELECTRICAL", "PNEUMATIC"}:
            fail(f"{scheme['id']}: unsupported domain")
        if not scheme.get("nodes") or any(n not in equipment_ids for n in scheme["nodes"]):
            fail(f"{scheme['id']}: unresolved equipment")
        if any(g not in groups for g in scheme.get("src", [])):
            fail(f"{scheme['id']}: unresolved source group")
        # A profile can see only nodes applicable to that exact profile; sibling-only nodes are filtered.
        for profile in apps:
            visible = [n for n in scheme["nodes"] if profile in equipment_by_id[n][3]]
            if not visible:
                fail(f"{scheme['id']}/{profile}: empty projected scheme")

    step_count = 0
    for flow in flows:
        fid, semantic, apps, scheme_id, steps = flow
        step_count += len(steps)
        if scheme_id not in scheme_ids:
            fail(f"{fid}: unknown scheme")
        if not set(apps) <= set(scheme_by_id[scheme_id]["app"]):
            fail(f"{fid}: wider applicability than scheme")
        if len(steps) < 2 or any(n not in equipment_ids for n in steps):
            fail(f"{fid}: invalid steps")
        if any(n not in scheme_by_id[scheme_id]["nodes"] for n in steps):
            fail(f"{fid}: step outside scheme")
        for profile in apps:
            if any(profile not in equipment_by_id[n][3] for n in steps):
                fail(f"{fid}: profile leakage in step list for {profile}")
        if not semantic:
            fail(f"{fid}: semantic type missing")
    if step_count != EXPECTED["flow_steps"]:
        fail(f"unexpected flow step count: {step_count}")

    projection = model.get("interactiveProjection", {})
    if projection.get("coordinatePolicy") != "DISPLAY_ONLY_NOT_PHYSICAL_LOCATION":
        fail("interactive coordinates must be display-only")
    camera = projection.get("camera", {})
    for key in ("fit", "pan", "zoom", "resetToFit", "fitActiveStep", "reducedMotion", "portraitLandscape"):
        if camera.get(key) is not True:
            fail(f"camera contract missing {key}")
    if camera.get("autoPanBetweenSteps") is not False:
        fail("camera must not auto-pan between steps")

    view_count = hotspot_count = 0
    coverage = {p: set() for p in PROFILES_ALLOWED}
    for profile in sorted(PROFILES_ALLOWED):
        for scheme in schemes:
            if profile not in scheme["app"]:
                continue
            visible = [n for n in scheme["nodes"] if profile in equipment_by_id[n][3]]
            view_count += 1
            hotspot_count += len(visible)
            coverage[profile].update(visible)
    if view_count != EXPECTED["views"] or hotspot_count != EXPECTED["hotspots"]:
        fail(f"interactive projection drift: {view_count} views / {hotspot_count} hotspots")
    for profile, expected in (("tem2-base", EXPECTED["tem2_equipment"]), ("tem2u-improved", EXPECTED["tem2u_equipment"])):
        applicable = {eid for eid, row in equipment_by_id.items() if profile in row[3]}
        if len(applicable) != expected:
            fail(f"{profile}: unexpected equipment count")
        if coverage[profile] != applicable:
            fail(f"{profile}: Atlas entries not fully reachable from interactive projection: {sorted(applicable - coverage[profile])}")

    # Explicit isolation: adjacent variants must remain metadata-only exclusions.
    isolation = model.get("profileIsolation", {})
    denied = set(isolation.get("deniedAdjacentVariants", []))
    if not {"TEM2UM", "TEM2T", "TEM2A"} <= denied or isolation.get("failClosed") is not True:
        fail("adjacent-variant fail-closed contract missing")
    active_text = json.dumps({"systems": systems, "equipment": equipment, "schemes": schemes, "flows": flows}, ensure_ascii=False).lower()
    for token in DENIED_TOKENS:
        if token in active_text:
            fail(f"adjacent variant leakage: {token}")

    # Stage 1 profile contract must keep the same exclusion boundary.
    contract_text = json.dumps(profile_contract, ensure_ascii=False).lower()
    for required in ("tem2um", "tem2t", "tem2a"):
        if required not in contract_text:
            fail(f"Stage 1 exclusion disappeared: {required}")

    palette = model.get("semanticPalette", {})
    if palette.get("colorNeverSoleCarrier") is not True or palette.get("brandAmberDistinctFromDeviationOrange") is not True:
        fail("semantic palette accessibility contract missing")
    required_styles = {f[1] for f in flows}
    if not required_styles <= set(palette.get("flowStyles", {})):
        fail("flow semantic style missing")

    print(
        "TEM2 STAGE 2 PASS: "
        f"{len(equipment)} atlas entries; {len(systems)} systems; {len(schemes)} functional schemes; "
        f"{len(flows)} flows/{step_count} steps; {view_count} views/{hotspot_count} hotspots; "
        f"TEM2 {EXPECTED['tem2_equipment']}/{EXPECTED['tem2_equipment']} and TEM2U {EXPECTED['tem2u_equipment']}/{EXPECTED['tem2u_equipment']} interactive coverage; profile isolation OK"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"TEM2 STAGE 2 FAIL: {exc}", file=sys.stderr)
        raise
