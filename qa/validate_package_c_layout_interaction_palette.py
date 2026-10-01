#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/assets/technical"
PATCH = ROOT / "patch/app/src/main/assets/technical"
B = APP / "stepwise_scheme_flows.json"
LAYOUT = APP / "scheme_layout_metadata.json"
VIEW = APP / "scheme_view_contract.json"
PALETTE = APP / "scheme_semantic_palette.json"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def fail(message):
    raise SystemExit("PACKAGE_C_VALIDATION_FAIL: " + message)


def edge_keys(seq):
    out = []
    def add(x):
        if x and x not in out:
            out.append(x)
    for step in seq.get("steps", []):
        if seq.get("family") == "ERMAK":
            for x in step.get("activeHotspotIds", []): add(x)
            for e in step.get("edges", []):
                add(e.get("fromAnchor")); add(e.get("toAnchor"))
        else:
            for x in step.get("activeEquipmentIds", []): add(x)
            for e in step.get("edges", []):
                add(e.get("fromEquipmentId")); add(e.get("toEquipmentId"))
    return out


def validate_mirrors():
    for name in ("scheme_layout_metadata.json", "scheme_view_contract.json", "scheme_semantic_palette.json"):
        a = APP / name
        p = PATCH / name
        if not a.exists() or not p.exists():
            fail(f"missing app/patch mirror for {name}")
        if a.read_bytes() != p.read_bytes():
            fail(f"app/patch mismatch for {name}")


def validate_layouts(stepwise, layout):
    seqs = stepwise.get("sequences", [])
    source_by_id = {x.get("id"): x for x in seqs}
    layouts = layout.get("layouts", [])
    layout_by_id = {x.get("sequenceId"): x for x in layouts}
    if len(source_by_id) != len(seqs): fail("duplicate Package B sequence IDs")
    if len(layout_by_id) != len(layouts): fail("duplicate layout sequence IDs")
    if set(source_by_id) != set(layout_by_id):
        fail("layout coverage is not exactly equal to Package B sequence IDs")
    if layout.get("layoutPolicy", {}).get("physicalSpatialClaimAllowed") is not False:
        fail("physical spatial claims must remain disabled")
    if layout.get("layoutPolicy", {}).get("exactWireOrPipeRouteClaimAllowed") is not False:
        fail("wire/pipe routing claims must remain disabled")

    valid_modes = {"SOURCE_HOTSPOT_COORDINATES", "HYBRID_SOURCE_AND_DETERMINISTIC", "DETERMINISTIC_FUNCTIONAL_LAYOUT"}
    source_nodes = fallback_nodes = 0
    for seq_id, src in source_by_id.items():
        item = layout_by_id[seq_id]
        if item.get("family") != src.get("family") or item.get("domain") != src.get("domain"):
            fail(f"family/domain drift for {seq_id}")
        if item.get("sourceSchemeRef") != src.get("sourceSchemeRef"):
            fail(f"sourceSchemeRef drift for {seq_id}")
        if item.get("applicability") != src.get("applicability"):
            fail(f"applicability drift for {seq_id}")
        if item.get("layoutMode") not in valid_modes:
            fail(f"invalid layout mode for {seq_id}")
        if item.get("spatialClaim") != "DISPLAY_COORDINATES_ONLY_NOT_PHYSICAL_EQUIPMENT_LOCATION":
            fail(f"unsafe spatial claim for {seq_id}")
        if item.get("coordinateSpace") != {"width": 1600, "height": 900}:
            fail(f"coordinate space drift for {seq_id}")
        expected = edge_keys(src)
        nodes = item.get("nodes", [])
        actual = [n.get("nodeKey") for n in nodes]
        if len(actual) != len(set(actual)):
            fail(f"duplicate node keys for {seq_id}")
        if actual != expected:
            fail(f"node order/coverage drift for {seq_id}: expected={expected}, actual={actual}")
        for n in nodes:
            pos = n.get("position") or {}
            if not all(isinstance(pos.get(k), int) for k in ("x", "y", "width", "height")):
                fail(f"invalid node geometry {seq_id}/{n.get('nodeKey')}")
            if pos["width"] <= 0 or pos["height"] <= 0:
                fail(f"non-positive node size {seq_id}/{n.get('nodeKey')}")
            if not (0 <= pos["x"] <= 1600 and 0 <= pos["y"] <= 900):
                fail(f"node origin outside coordinate space {seq_id}/{n.get('nodeKey')}")
            evidence = n.get("placementEvidence")
            if evidence == "SOURCE_INTERACTIVE_HOTSPOT": source_nodes += 1
            elif evidence == "DETERMINISTIC_FUNCTIONAL_LAYOUT": fallback_nodes += 1
            else: fail(f"unknown placement evidence {seq_id}/{n.get('nodeKey')}")
        bounds = item.get("contentBounds") or {}
        if not (0 <= bounds.get("left", -1) <= bounds.get("right", 2000) <= 1600):
            fail(f"invalid horizontal bounds for {seq_id}")
        if not (0 <= bounds.get("top", -1) <= bounds.get("bottom", 2000) <= 900):
            fail(f"invalid vertical bounds for {seq_id}")

    stats = layout.get("statistics") or {}
    if stats.get("sequenceCount") != len(seqs): fail("layout sequence statistic mismatch")
    if stats.get("nodeCount") != source_nodes + fallback_nodes: fail("layout node statistic mismatch")
    if len(seqs) != 55: fail(f"Package B baseline changed unexpectedly: {len(seqs)} sequences")
    return len(seqs), source_nodes, fallback_nodes


def validate_view(view):
    if view.get("contractId") != "fleet-interactive-scheme-view-contract-v1": fail("view contract id mismatch")
    if view.get("coordinateSpace") != {"width": 1600, "height": 900}: fail("view coordinate space mismatch")
    initial = view.get("initialView") or {}
    if initial.get("mode") != "RESET_TO_FIT" or not initial.get("fitContent"):
        fail("initial view must reset to fit")
    zoom = view.get("zoom") or {}
    if zoom.get("unit") != "MULTIPLIER_RELATIVE_TO_FIT": fail("zoom must be relative to fit")
    if zoom.get("fitMultiplier") != 1.0 or zoom.get("minMultiplier") != 1.0:
        fail("fit must be the minimum zoom multiplier")
    if not isinstance(zoom.get("maxMultiplier"), (int, float)) or zoom["maxMultiplier"] < 3.0:
        fail("max zoom is too restrictive")
    if not zoom.get("pinchEnabled") or zoom.get("pinchFocalPoint") != "GESTURE_CENTROID":
        fail("pinch zoom contract incomplete")
    pan = view.get("pan") or {}
    if not pan.get("enabled") or set(pan.get("axes", [])) != {"X", "Y"}:
        fail("two-axis pan required")
    reset = view.get("resetToFit") or {}
    if reset.get("actionId") != "RESET_TO_FIT" or not reset.get("visibleControlRequired"):
        fail("visible reset-to-fit control required")
    if reset.get("doubleTap") != "RESET_TO_FIT": fail("double tap reset compatibility missing")
    acc = view.get("accessibility") or {}
    if not acc.get("colorIsNeverOnlySignal") or not acc.get("flowKindTextLabelRequired") or not acc.get("linePatternRequired"):
        fail("non-color accessibility contract incomplete")
    if acc.get("minimumTouchTargetDp", 0) < 48: fail("touch target below 48dp")
    orient = view.get("orientation") or {}
    if orient.get("portrait") != "SUPPORTED" or orient.get("landscape") != "SUPPORTED":
        fail("both orientations must be supported")
    motion = (view.get("stepPlayback") or {}).get("reducedMotion")
    if motion != "NO_MOVING_FLOW_ANIMATION": fail("reduced-motion rule missing")


def validate_palette(stepwise, palette):
    actual_kinds = {e.get("flowKind") for s in stepwise.get("sequences", []) for st in s.get("steps", []) for e in st.get("edges", []) if e.get("flowKind")}
    tokens = palette.get("flowTokens", [])
    by_kind = {x.get("flowKind"): x for x in tokens}
    if set(by_kind) != actual_kinds:
        fail(f"palette flow kind coverage mismatch missing={sorted(actual_kinds-set(by_kind))} extra={sorted(set(by_kind)-actual_kinds)}")
    for kind, token in by_kind.items():
        if not token.get("lineStyle") or not token.get("textLabelRequired"):
            fail(f"non-color signal missing for {kind}")
        for theme in ("light", "dark"):
            stroke = (token.get(theme) or {}).get("stroke")
            if not isinstance(stroke, str) or not stroke.startswith("#") or len(stroke) != 7:
                fail(f"invalid {theme} stroke for {kind}")
    policy = palette.get("policy") or {}
    if not policy.get("semanticMeaningOverridesUserAccent") or not policy.get("colorIsNeverOnlySignal"):
        fail("semantic palette must override accent and remain non-color-only")
    states = palette.get("contentStates") or {}
    for name in ("purposeNeutral", "normalState", "deviation", "safetyDanger", "information"):
        if name not in states: fail(f"missing semantic content state {name}")
        if not states[name].get("iconOrLabelRequired"): fail(f"semantic state {name} is color-only")
    dev_light = states["deviation"]["light"]["foreground"]
    normal_light = states["normalState"]["light"]["foreground"]
    safety_light = states["safetyDanger"]["light"]["foreground"]
    if len({dev_light, normal_light, safety_light}) != 3:
        fail("normal/deviation/danger foregrounds must be distinct")
    migration = palette.get("accentMigrationTarget") or {}
    if migration.get("legacyName") != "стальная зелёная" or migration.get("newDisplayName") != "яркая зелёная":
        fail("green accent migration target missing")
    if migration.get("runtimeChangeInThisPackage") is not False:
        fail("Package C must not silently modify runtime theme")
    return len(actual_kinds)


def main():
    validate_mirrors()
    stepwise = load(B)
    layout = load(LAYOUT)
    view = load(VIEW)
    palette = load(PALETTE)
    seq_count, source_nodes, fallback_nodes = validate_layouts(stepwise, layout)
    validate_view(view)
    flow_count = validate_palette(stepwise, palette)
    print("PACKAGE_C_VALIDATION_PASS")
    print("sequences", seq_count, "source_nodes", source_nodes, "fallback_nodes", fallback_nodes, "flowKinds", flow_count)


if __name__ == "__main__":
    main()
