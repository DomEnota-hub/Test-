#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / "docs" / "locomotives" / "chme3"
FAMILY = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family"
COMMON = FAMILY / "common"


def load(path: Path):
    if not path.exists():
        raise AssertionError(f"missing file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def walk_ids(value):
    if isinstance(value, dict):
        maybe = value.get("id")
        if isinstance(maybe, str):
            yield maybe
        for child in value.values():
            yield from walk_ids(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_ids(child)


def equipment_ids() -> set[str]:
    ids: set[str] = set()
    paths = list(OLD.glob("atlas*.json")) + list((FAMILY / "chme3e").glob("atlas*.json"))
    for path in paths:
        for item_id in walk_ids(load(path)):
            if item_id.startswith(("CHME3-EQ-", "CHME3T-EQ-", "CHME3E-EQ-")):
                ids.add(item_id)
    return ids


def source_schemes() -> dict[str, dict]:
    result = {}
    for path in [OLD / "atlas_schemes_functional.json", OLD / "atlas_schemes_extension.json", FAMILY / "chme3e" / "schemes_variant.json"]:
        raw = load(path)
        for row in raw.get("records", []):
            sid = row.get("id")
            if sid:
                if sid in result:
                    raise AssertionError(f"duplicate source scheme id: {sid}")
                result[sid] = row
    return result


def profile_allowed(profile: str, equipment_id: str) -> bool:
    if profile == "chme3-base":
        return equipment_id.startswith("CHME3-EQ-")
    if profile == "chme3t-rheostatic":
        return equipment_id.startswith(("CHME3-EQ-", "CHME3T-EQ-"))
    if profile == "chme3e-electronic":
        return equipment_id.startswith(("CHME3-EQ-", "CHME3E-EQ-"))
    return False


def validate_view(view: dict, profile: str, all_equipment: set[str], schemes: dict[str, dict]) -> tuple[int, int]:
    vid = view["id"]
    hotspots = view.get("hotspotLayer", [])
    if not hotspots:
        raise AssertionError(f"{profile}/{vid}: no hotspots")

    hotspot_ids = []
    for spot in hotspots:
        eid = spot.get("equipmentId", "")
        label = spot.get("label", "")
        layout = spot.get("layoutHint", {})
        if not eid or not label:
            raise AssertionError(f"{profile}/{vid}: hotspot without equipmentId/label")
        if eid not in all_equipment:
            raise AssertionError(f"{profile}/{vid}: unknown equipment {eid}")
        if not profile_allowed(profile, eid):
            raise AssertionError(f"{profile}/{vid}: profile leak {eid}")
        x, y = layout.get("x"), layout.get("y")
        w, h = layout.get("width"), layout.get("height")
        if not all(isinstance(v, int) for v in (x, y, w, h)):
            raise AssertionError(f"{profile}/{vid}: bad layout for {eid}")
        if x < 0 or y < 0 or w < 120 or h < 56 or x + w > 1600 or y + h > 900:
            raise AssertionError(f"{profile}/{vid}: hotspot outside/too small for {eid}: {layout}")
        hotspot_ids.append(eid)

    if len(hotspot_ids) != len(set(hotspot_ids)):
        raise AssertionError(f"{profile}/{vid}: duplicate hotspot equipment")

    source_ref = view.get("sourceSchemeRef")
    if not source_ref or source_ref not in schemes:
        raise AssertionError(f"{profile}/{vid}: unresolved sourceSchemeRef {source_ref}")

    if view.get("flowSource") == "SOURCE_SCHEME_REF":
        raw_flows = schemes[source_ref].get("flows", [])
        flows = [{"from": row[0], "to": row[1], "label": row[2] if len(row) > 2 else ""} for row in raw_flows]
    else:
        flows = view.get("flowLayer", [])

    if not flows:
        raise AssertionError(f"{profile}/{vid}: no interactive flow edges")
    hotspot_set = set(hotspot_ids)
    for edge in flows:
        a, b = edge.get("from"), edge.get("to")
        if not a or not b or not edge.get("label"):
            raise AssertionError(f"{profile}/{vid}: malformed flow {edge}")
        if a not in hotspot_set or b not in hotspot_set:
            raise AssertionError(f"{profile}/{vid}: flow endpoint is not clickable: {a} -> {b}")

    return len(hotspots), len(flows)


def main() -> int:
    contract = load(COMMON / "interactive_scheme_contract.json")
    if contract.get("coordinateSystem", {}).get("width") != 1600 or contract.get("coordinateSystem", {}).get("height") != 900:
        raise AssertionError("unexpected interactive coordinate system")
    if contract.get("hotspotContract", {}).get("onTap") != "OPEN_EQUIPMENT_CARD":
        raise AssertionError("tap navigation contract missing")

    shared_raw = load(COMMON / "interactive_shared_views.json")
    shared = {v["id"]: v for v in shared_raw.get("views", [])}
    if len(shared) < 9:
        raise AssertionError("shared interactive coverage is incomplete")

    all_equipment = equipment_ids()
    schemes = source_schemes()
    profile_files = {
        "chme3-base": FAMILY / "chme3" / "interactive_schemes.json",
        "chme3t-rheostatic": FAMILY / "chme3t" / "interactive_schemes.json",
        "chme3e-electronic": FAMILY / "chme3e" / "interactive_schemes.json",
    }
    profile_data = {profile: load(path) for profile, path in profile_files.items()}
    own_views = {profile: {v["id"]: v for v in data.get("profileViews", [])} for profile, data in profile_data.items()}

    total_views = total_hotspots = total_edges = 0
    per_profile = {}
    for profile, data in profile_data.items():
        effective = []
        for ref in data.get("sharedViewRefs", []):
            if ref not in shared:
                raise AssertionError(f"{profile}: unknown shared view {ref}")
            effective.append(shared[ref])

        inherited = data.get("inheritedProfileViews")
        if inherited:
            parent = inherited.get("fromProfile")
            if parent not in own_views:
                raise AssertionError(f"{profile}: unknown inherited profile {parent}")
            for ref in inherited.get("viewIds", []):
                if ref not in own_views[parent]:
                    raise AssertionError(f"{profile}: unknown inherited view {ref}")
                effective.append(own_views[parent][ref])

        effective.extend(own_views[profile].values())
        effective_ids = [v["id"] for v in effective]
        if len(effective_ids) != len(set(effective_ids)):
            raise AssertionError(f"{profile}: duplicate effective interactive views")

        types = {v.get("schemeType", "") for v in effective}
        if not any(t.startswith("electrical") for t in types):
            raise AssertionError(f"{profile}: no electrical interactive view")
        if "pneumatic" not in types:
            raise AssertionError(f"{profile}: no pneumatic interactive view")

        ph = pe = 0
        for view in effective:
            h, e = validate_view(view, profile, all_equipment, schemes)
            ph += h
            pe += e

        if profile == "chme3-base":
            if any(e.startswith(("CHME3T-", "CHME3E-")) for v in effective for e in [s["equipmentId"] for s in v.get("hotspotLayer", [])]):
                raise AssertionError("base CHME3 contains variant-only equipment")
        elif profile == "chme3t-rheostatic":
            if "CHME3T-INT-EDB" not in effective_ids:
                raise AssertionError("CHME3T EDB interactive view missing")
            if any(s["equipmentId"].startswith("CHME3E-") for v in effective for s in v.get("hotspotLayer", [])):
                raise AssertionError("CHME3T contains CHME3E equipment")
        elif profile == "chme3e-electronic":
            if any(s["equipmentId"].startswith("CHME3T-") for v in effective for s in v.get("hotspotLayer", [])):
                raise AssertionError("CHME3E contains CHME3T equipment")
            if any("EDB" in vid or "RHEOST" in vid for vid in effective_ids):
                raise AssertionError("CHME3E exposes rheostatic brake interactive view")
            required = {
                "CHME3E-INT-TRACTION-CONTROL", "CHME3E-INT-FIELD-WEAKENING", "CHME3E-INT-START",
                "CHME3E-INT-PREHEAT-RUNNING", "CHME3E-INT-PREHEAT-EXTERNAL", "CHME3E-INT-REMOTE-CONTROL"
            }
            if not required.issubset(effective_ids):
                raise AssertionError(f"CHME3E interactive views missing: {sorted(required - set(effective_ids))}")

        per_profile[profile] = (len(effective), ph, pe)
        total_views += len(effective)
        total_hotspots += ph
        total_edges += pe

    print("CHME3 INTERACTIVE OK")
    for profile, (views, hotspots, edges) in per_profile.items():
        print(f"  {profile}: {views} effective views, {hotspots} hotspots, {edges} directed edges")
    print(f"  total effective profile views={total_views}, hotspots={total_hotspots}, edges={total_edges}; profile isolation PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, KeyError, TypeError, json.JSONDecodeError) as exc:
        print(f"CHME3 INTERACTIVE FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
