#!/usr/bin/env python3
import gzip
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
B_ASSET = ROOT / "app/src/main/assets/technical/stepwise_scheme_flows.json"
APP_DIR = ROOT / "app/src/main/assets/technical"
PATCH_DIR = ROOT / "patch/app/src/main/assets/technical"
CHECKPOINT = ROOT / "docs/integration/PACKAGE_C_LAYOUT_INTERACTION_PALETTE_CHECKPOINT.md"

COORDINATE_SPACE = {"width": 1600, "height": 900}
DEFAULT_NODE = {"width": 190, "height": 72}


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_gzip_json(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def dump_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def valid_layout(h):
    layout = h.get("layoutHint") or {}
    return all(isinstance(layout.get(k), (int, float)) for k in ("x", "y"))


def normalized_position(h):
    layout = h.get("layoutHint") or {}
    return {
        "x": int(layout["x"]),
        "y": int(layout["y"]),
        "width": int(layout.get("width", DEFAULT_NODE["width"])),
        "height": int(layout.get("height", DEFAULT_NODE["height"])),
    }


def ermak_layouts():
    data = load_gzip_json(APP_DIR / "ermak_schemes.json.gz")
    out = {}
    for scheme in data.get("schemes", []):
        by_anchor = {}
        by_equipment = {}
        for h in (scheme.get("layers") or {}).get("hotspotLayer") or []:
            if not valid_layout(h):
                continue
            anchor = h.get("id") or h.get("hotspotId") or h.get("nodeId")
            eq = h.get("equipmentId")
            rec = {
                "anchorId": anchor or "",
                "equipmentId": eq or "",
                "label": h.get("label") or h.get("title") or eq or anchor or "Узел",
                "position": normalized_position(h),
            }
            if anchor:
                by_anchor[anchor] = rec
            if eq and eq not in by_equipment:
                by_equipment[eq] = rec
        out[scheme.get("id")] = {"byAnchor": by_anchor, "byEquipment": by_equipment}
    return out


def chme_views():
    paths = [
        ROOT / "docs/locomotives/diesel/chme3-family/common/interactive_shared_views.json",
        ROOT / "docs/locomotives/diesel/chme3-family/chme3/interactive_schemes.json",
        ROOT / "docs/locomotives/diesel/chme3-family/chme3t/interactive_schemes.json",
        ROOT / "docs/locomotives/diesel/chme3-family/chme3e/interactive_schemes.json",
    ]
    result = {}
    for path in paths:
        data = load_json(path)
        views = data.get("views", []) + data.get("profileViews", [])
        for view in views:
            scheme_ref = view.get("sourceSchemeRef")
            if not scheme_ref:
                continue
            by_equipment = result.setdefault(scheme_ref, {})
            for h in view.get("hotspotLayer") or []:
                eq = h.get("equipmentId")
                if not eq or not valid_layout(h):
                    continue
                by_equipment.setdefault(eq, {
                    "equipmentId": eq,
                    "label": h.get("label") or h.get("title") or eq,
                    "position": normalized_position(h),
                })
    return result


def sequence_node_keys(seq):
    order = []
    def add(x):
        if x and x not in order:
            order.append(x)
    for step in seq.get("steps", []):
        if seq.get("family") == "ERMAK":
            for anchor in step.get("activeHotspotIds", []):
                add(anchor)
            for edge in step.get("edges", []):
                add(edge.get("fromAnchor")); add(edge.get("toAnchor"))
        else:
            for eq in step.get("activeEquipmentIds", []):
                add(eq)
            for edge in step.get("edges", []):
                add(edge.get("fromEquipmentId")); add(edge.get("toEquipmentId"))
    return order


def deterministic_positions(keys):
    # Functional reading layout only. It does not represent physical placement.
    positions = {}
    if not keys:
        return positions
    max_cols = 5
    cols = min(max_cols, max(1, len(keys)))
    rows = (len(keys) + cols - 1) // cols
    x_gap = 1350 / max(1, cols - 1) if cols > 1 else 0
    y_gap = 620 / max(1, rows - 1) if rows > 1 else 0
    for i, key in enumerate(keys):
        row, col = divmod(i, cols)
        # Snake rows keep successive functional nodes spatially near each other.
        visual_col = col if row % 2 == 0 else cols - 1 - col
        positions[key] = {
            "x": int(125 + visual_col * x_gap),
            "y": int(120 + row * y_gap),
            "width": DEFAULT_NODE["width"],
            "height": DEFAULT_NODE["height"],
        }
    return positions


def build_layouts(stepwise):
    ermak = ermak_layouts()
    chme = chme_views()
    layouts = []
    modes = Counter()
    for seq in stepwise.get("sequences", []):
        keys = sequence_node_keys(seq)
        fallback = deterministic_positions(keys)
        nodes = []
        source_count = 0
        source_scheme = seq.get("sourceSchemeRef")

        if seq.get("family") == "ERMAK":
            src = ermak.get(source_scheme, {})
            by_anchor = src.get("byAnchor", {})
            for key in keys:
                h = by_anchor.get(key)
                if h:
                    source_count += 1
                    nodes.append({
                        "nodeKey": key,
                        "equipmentId": h.get("equipmentId") or "",
                        "label": h.get("label") or key,
                        "position": h["position"],
                        "placementEvidence": "SOURCE_INTERACTIVE_HOTSPOT",
                    })
                else:
                    nodes.append({
                        "nodeKey": key,
                        "equipmentId": "",
                        "label": key,
                        "position": fallback[key],
                        "placementEvidence": "DETERMINISTIC_FUNCTIONAL_LAYOUT",
                    })
        else:
            by_equipment = chme.get(source_scheme, {})
            for key in keys:
                h = by_equipment.get(key)
                if h:
                    source_count += 1
                    nodes.append({
                        "nodeKey": key,
                        "equipmentId": key,
                        "label": h.get("label") or key,
                        "position": h["position"],
                        "placementEvidence": "SOURCE_INTERACTIVE_HOTSPOT",
                    })
                else:
                    nodes.append({
                        "nodeKey": key,
                        "equipmentId": key,
                        "label": key,
                        "position": fallback[key],
                        "placementEvidence": "DETERMINISTIC_FUNCTIONAL_LAYOUT",
                    })

        if source_count == len(nodes) and nodes:
            mode = "SOURCE_HOTSPOT_COORDINATES"
        elif source_count:
            mode = "HYBRID_SOURCE_AND_DETERMINISTIC"
        else:
            mode = "DETERMINISTIC_FUNCTIONAL_LAYOUT"
        modes[mode] += 1

        xs = [n["position"]["x"] for n in nodes] or [0]
        ys = [n["position"]["y"] for n in nodes] or [0]
        xe = [n["position"]["x"] + n["position"]["width"] for n in nodes] or [1600]
        ye = [n["position"]["y"] + n["position"]["height"] for n in nodes] or [900]
        layouts.append({
            "sequenceId": seq["id"],
            "family": seq.get("family"),
            "domain": seq.get("domain"),
            "sourceSchemeRef": source_scheme,
            "applicability": seq.get("applicability"),
            "layoutMode": mode,
            "spatialClaim": "DISPLAY_COORDINATES_ONLY_NOT_PHYSICAL_EQUIPMENT_LOCATION",
            "coordinateSpace": COORDINATE_SPACE,
            "contentBounds": {
                "left": max(0, min(xs) - 48),
                "top": max(0, min(ys) - 48),
                "right": min(1600, max(xe) + 48),
                "bottom": min(900, max(ye) + 48),
            },
            "fitPaddingDp": 24,
            "nodes": nodes,
        })
    return layouts, modes


def build_view_contract():
    return {
        "schemaVersion": 1,
        "contractId": "fleet-interactive-scheme-view-contract-v1",
        "updatedAt": "2026-10-01",
        "coordinateSpace": COORDINATE_SPACE,
        "initialView": {
            "mode": "RESET_TO_FIT",
            "fitContent": True,
            "fitPaddingDp": 24,
            "centerContent": True,
            "preserveSelection": False,
        },
        "zoom": {
            "unit": "MULTIPLIER_RELATIVE_TO_FIT",
            "fitMultiplier": 1.0,
            "minMultiplier": 1.0,
            "maxMultiplier": 5.0,
            "pinchEnabled": True,
            "pinchFocalPoint": "GESTURE_CENTROID",
            "wheelOrTrackpadEnabledWhenAvailable": True,
        },
        "pan": {
            "enabled": True,
            "axes": ["X", "Y"],
            "enabledAtFit": False,
            "bounds": "CLAMP_CONTENT_TO_VIEWPORT_WITH_16DP_OVERSCROLL",
            "oneFingerPanAfterZoom": True,
        },
        "resetToFit": {
            "actionId": "RESET_TO_FIT",
            "visibleControlRequired": True,
            "doubleTap": "RESET_TO_FIT",
            "result": {"zoomMultiplier": 1.0, "offsetX": 0.0, "offsetY": 0.0},
        },
        "selection": {
            "tapNode": "OPEN_EQUIPMENT_CARD",
            "tapEmptySpace": "KEEP_VIEW_STATE",
            "selectedNodeMustRemainIdentifiableWithoutColor": True,
        },
        "stepPlayback": {
            "default": "MANUAL",
            "animation": "OPTIONAL",
            "animateOnlyOnUserRequest": True,
            "autoPanToActiveStep": False,
            "focusActiveStepAction": "FIT_ACTIVE_STEP_WITH_CONTEXT",
            "reducedMotion": "NO_MOVING_FLOW_ANIMATION",
        },
        "accessibility": {
            "colorIsNeverOnlySignal": True,
            "flowKindTextLabelRequired": True,
            "linePatternRequired": True,
            "resetControlContentDescription": "Показать схему целиком",
            "minimumTouchTargetDp": 48,
        },
        "orientation": {
            "portrait": "SUPPORTED",
            "landscape": "SUPPORTED",
            "retainLogicalSelectionOnRotation": True,
            "recomputeFitOnViewportChange": True,
        },
        "stateModel": {
            "persistentAcrossStepChange": ["zoomMultiplier", "offsetX", "offsetY", "selectedSequenceId"],
            "resetOnSequenceChange": ["zoomMultiplier", "offsetX", "offsetY"],
        },
    }


def build_palette(flow_kinds):
    # Semantic colors are renderer tokens. Accent themes must not recolor safety/state meaning.
    specs = {
        "HIGH_VOLTAGE_POWER": ("Высоковольтное питание", "#6D28D9", "#C4B5FD", "solid-heavy"),
        "TRACTION_CURRENT": ("Тяговый ток", "#B45309", "#FDBA74", "solid-heavy-arrow"),
        "REGENERATIVE_CURRENT": ("Ток рекуперации", "#047857", "#6EE7B7", "solid-heavy-reverse"),
        "BRAKING_CURRENT": ("Тормозной ток / энергия", "#B91C1C", "#FCA5A5", "solid-heavy-brake"),
        "AUXILIARY_POWER": ("Питание собственных нужд", "#7C3AED", "#C4B5FD", "solid-medium"),
        "CONTROL_POWER": ("Питание цепей управления", "#475569", "#CBD5E1", "solid-thin"),
        "COMMAND": ("Команда / разрешение", "#1D4ED8", "#93C5FD", "dashed-arrow"),
        "CONTROL_SIGNAL": ("Управляющий сигнал", "#0369A1", "#7DD3FC", "dashed-arrow"),
        "FEEDBACK_SIGNAL": ("Сигнал обратной связи", "#0F766E", "#5EEAD4", "dotted-arrow"),
        "EXCITATION": ("Возбуждение", "#9333EA", "#D8B4FE", "dash-dot-arrow"),
        "STATUS_SIGNAL": ("Сигнал состояния", "#4F46E5", "#A5B4FC", "dotted-arrow"),
        "SAFETY_COMMAND": ("Команда системы безопасности", "#991B1B", "#F87171", "double-dashed-arrow"),
        "FIRE_SIGNAL": ("Сигнал пожарной системы", "#BE123C", "#FDA4AF", "dotted-alert"),
        "AIR_SUPPLY": ("Подача сжатого воздуха", "#0369A1", "#7DD3FC", "solid-pneumatic"),
        "CONTROL_PRESSURE": ("Управляющее давление", "#0284C7", "#38BDF8", "dashed-pneumatic"),
        "BRAKE_PIPE_PRESSURE_CHANGE": ("Изменение давления ТМ", "#C2410C", "#FB923C", "double-pneumatic"),
        "BRAKE_CYLINDER_CONTROL": ("Тормозные цилиндры", "#B91C1C", "#FCA5A5", "solid-pneumatic-brake"),
        "EXHAUST_RELEASE": ("Разрядка / выпуск", "#64748B", "#CBD5E1", "dotted-pneumatic-out"),
        "COOLANT_FLOW": ("Циркуляция теплоносителя", "#0F766E", "#5EEAD4", "wave-arrow"),
        "STARTER_CURRENT": ("Пусковой ток", "#92400E", "#FBBF24", "solid-heavy-start"),
    }
    missing = sorted(set(flow_kinds) - set(specs))
    if missing:
        raise SystemExit(f"Palette missing Package B flow kinds: {missing}")
    flows = []
    for key in sorted(set(flow_kinds)):
        label, light, dark, style = specs[key]
        flows.append({
            "flowKind": key,
            "label": label,
            "lineStyle": style,
            "light": {"stroke": light},
            "dark": {"stroke": dark},
            "marker": "ARROW_OR_DOMAIN_MARKER",
            "textLabelRequired": True,
        })
    return {
        "schemaVersion": 1,
        "paletteId": "fleet-scheme-semantic-palette-v1",
        "updatedAt": "2026-10-01",
        "policy": {
            "semanticMeaningOverridesUserAccent": True,
            "userAccentMayStyleChromeButNotSafetyOrStateMeaning": True,
            "colorIsNeverOnlySignal": True,
            "lightAndDarkHaveIndependentTokens": True,
        },
        "contentStates": {
            "purposeNeutral": {
                "label": "Назначение / нейтральная информация",
                "light": {"foreground": "#334155", "surface": "#F1F5F9"},
                "dark": {"foreground": "#CBD5E1", "surface": "#1E293B"},
                "iconOrLabelRequired": True,
            },
            "normalState": {
                "label": "Нормальное состояние",
                "light": {"foreground": "#166534", "surface": "#DCFCE7"},
                "dark": {"foreground": "#86EFAC", "surface": "#14532D"},
                "iconOrLabelRequired": True,
            },
            "deviation": {
                "label": "Признак отклонения",
                "light": {"foreground": "#9A3412", "surface": "#FFEDD5"},
                "dark": {"foreground": "#FDBA74", "surface": "#7C2D12"},
                "iconOrLabelRequired": True,
                "note": "Оранжевый semantic token намеренно отделён от янтарного accent token.",
            },
            "safetyDanger": {
                "label": "Опасность / запрет",
                "light": {"foreground": "#991B1B", "surface": "#FEE2E2"},
                "dark": {"foreground": "#FCA5A5", "surface": "#7F1D1D"},
                "iconOrLabelRequired": True,
            },
            "information": {
                "label": "Справочная информация",
                "light": {"foreground": "#1D4ED8", "surface": "#DBEAFE"},
                "dark": {"foreground": "#93C5FD", "surface": "#1E3A8A"},
                "iconOrLabelRequired": True,
            },
        },
        "accentMigrationTarget": {
            "legacyName": "стальная зелёная",
            "newDisplayName": "яркая зелёная",
            "lightPrimary": "#16A34A",
            "darkPrimary": "#4ADE80",
            "scope": "THEME_ACCENT_ONLY_NOT_SEMANTIC_STATE",
            "runtimeChangeInThisPackage": False,
        },
        "flowTokens": flows,
    }


def main():
    stepwise = load_json(B_ASSET)
    sequences = stepwise.get("sequences", [])
    layouts, modes = build_layouts(stepwise)
    flow_kinds = [edge.get("flowKind") for seq in sequences for step in seq.get("steps", []) for edge in step.get("edges", []) if edge.get("flowKind")]

    layout_asset = {
        "schemaVersion": 1,
        "packageId": "package-c-scheme-layout-metadata",
        "generatedAt": "2026-10-01",
        "sourceAsset": "technical/stepwise_scheme_flows.json",
        "purpose": "Renderer-ready display coordinates for Package B flows. Coordinates are presentation metadata, not claims about physical equipment position or wire/pipe routing.",
        "layoutPolicy": {
            "coordinateSpace": COORDINATE_SPACE,
            "defaultNodeSize": DEFAULT_NODE,
            "preferExistingInteractiveHotspots": True,
            "deterministicFallbackAllowed": True,
            "physicalSpatialClaimAllowed": False,
            "exactWireOrPipeRouteClaimAllowed": False,
        },
        "statistics": {
            "sequenceCount": len(layouts),
            "nodeCount": sum(len(x["nodes"]) for x in layouts),
            "layoutModes": dict(sorted(modes.items())),
        },
        "layouts": layouts,
    }
    contract = build_view_contract()
    palette = build_palette(flow_kinds)

    outputs = {
        "scheme_layout_metadata.json": layout_asset,
        "scheme_view_contract.json": contract,
        "scheme_semantic_palette.json": palette,
    }
    for name, data in outputs.items():
        dump_json(APP_DIR / name, data)
        dump_json(PATCH_DIR / name, data)

    source_nodes = sum(1 for x in layouts for n in x["nodes"] if n["placementEvidence"] == "SOURCE_INTERACTIVE_HOTSPOT")
    fallback_nodes = sum(1 for x in layouts for n in x["nodes"] if n["placementEvidence"] == "DETERMINISTIC_FUNCTIONAL_LAYOUT")
    text = f"""# Package C — layout, interaction and semantic palette checkpoint\n\nStatus: **GENERATED / VALIDATION REQUIRED**\n\n## Scope\n\nPackage C prepares renderer contracts only. It does not replace the Android scheme renderer and does not claim physical wire, terminal, pipe, or equipment placement accuracy.\n\n## Generated coverage\n\n- Package B sequences: **{len(layouts)}**\n- Layout nodes: **{sum(len(x['nodes']) for x in layouts)}**\n- Existing interactive hotspot placements reused: **{source_nodes}**\n- Deterministic functional fallback placements: **{fallback_nodes}**\n- Layout modes: `{dict(sorted(modes.items()))}`\n- Semantic flow kinds covered: **{len(set(flow_kinds))}**\n\n## View contract\n\n- open/reset state is `RESET_TO_FIT`;\n- zoom is a multiplier relative to fit (`1.0..5.0`), so narrow screens can always display the complete scheme;\n- two-axis pan is available after zoom;\n- pinch zoom is centred on the gesture centroid;\n- a visible reset-to-fit control is mandatory;\n- portrait and landscape are both supported;\n- reduced-motion mode disables moving-flow animation;\n- step meaning never depends on colour alone.\n\n## Semantic palette\n\nSeparate light/dark tokens exist for every Package B flow kind. Purpose is neutral, normal condition is green, deviation is orange, danger/prohibition is red. Deviation orange is deliberately distinct from the amber application accent.\n\nThe requested theme migration `стальная зелёная → яркая зелёная` is recorded as a renderer/theme integration target (`#16A34A` light / `#4ADE80` dark), but this Package C does not change the current Kotlin theme runtime.\n\n## Spatial truth boundary\n\n`SOURCE_HOTSPOT_COORDINATES` means an existing interactive drawing coordinate was reused. It does **not** mean the location is a verified physical installation coordinate. `DETERMINISTIC_FUNCTIONAL_LAYOUT` is renderer-only placement. Neither may be presented as a wiring/piping route.\n"""
    CHECKPOINT.parent.mkdir(parents=True, exist_ok=True)
    CHECKPOINT.write_text(text, encoding="utf-8")

    print("PACKAGE_C_BUILD_OK", "sequences=", len(layouts), "nodes=", sum(len(x["nodes"]) for x in layouts), "source_nodes=", source_nodes, "fallback_nodes=", fallback_nodes, "modes=", dict(modes), "flowKinds=", len(set(flow_kinds)))


if __name__ == "__main__":
    main()
