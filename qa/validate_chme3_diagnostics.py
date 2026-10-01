#!/usr/bin/env python3
"""Validate the isolated ChME3 diagnostic package before Android integration."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "docs" / "locomotives" / "chme3"
DIAG_FILES = [
    "diagnostics_powerplant_pass3.json",
    "diagnostics_electrical_control_pass3.json",
    "diagnostics_brake_aux_pass3.json",
]


def load(name: str):
    path = PKG / name
    if not path.exists():
        raise AssertionError(f"missing required file: {path.relative_to(ROOT)}")
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def unique(values, label: str):
    values = list(values)
    seen = set()
    duplicates = set()
    for value in values:
        if value in seen:
            duplicates.add(value)
        seen.add(value)
    if duplicates:
        raise AssertionError(f"duplicate {label}: {sorted(duplicates)}")
    return seen


def main() -> int:
    atlas = load("atlas_index.json")
    scheme_cards = load("atlas_cards_scheme_equipment.json")
    equipment_ids = unique(
        [row["id"] for row in atlas.get("equipment", [])]
        + [row["id"] for row in scheme_cards.get("equipment", [])],
        "equipment IDs",
    )

    # Source registry is intentionally additive. Atlas and diagnostics can introduce
    # new source cards without rewriting the foundation registry.
    source_files = sorted(PKG.glob("*source_registry*.json")) + sorted(PKG.glob("source_registry*.json"))
    source_files = list(dict.fromkeys(source_files))
    source_rows = []
    source_meta = {}
    for path in source_files:
        with path.open("r", encoding="utf-8") as fh:
            data = json.load(fh)
        for row in data.get("sources", []):
            source_rows.append(row)
            source_meta[row["id"]] = row
    source_ids = unique((row["id"] for row in source_rows), "source IDs")

    scenarios = []
    for name in DIAG_FILES:
        scenarios.extend(load(name).get("scenarios", []))
    scenario_ids = unique((row["id"] for row in scenarios), "scenario IDs")

    id_re = re.compile(r"^CHME3-DIAG-\d{3}$")
    allowed_authority = {
        "TRIAGE_ONLY_NO_REPAIR",
        "SOURCE_AND_PROFILE_REQUIRED",
        "SAFETY_GATE_REQUIRED",
        "EMERGENCY_SOURCE_BOUND",
    }

    for row in scenarios:
        sid = row["id"]
        if not id_re.match(sid):
            raise AssertionError(f"bad scenario ID format: {sid}")
        if not row.get("title"):
            raise AssertionError(f"missing title: {sid}")
        if not row.get("profiles"):
            raise AssertionError(f"missing profiles: {sid}")
        if row.get("actionAuthority") not in allowed_authority:
            raise AssertionError(f"unsupported actionAuthority in {sid}: {row.get('actionAuthority')}")
        if not row.get("riskClass"):
            raise AssertionError(f"missing riskClass: {sid}")
        if len(row.get("queryTerms", [])) < 3:
            raise AssertionError(f"too few queryTerms in {sid}")
        if len(row.get("symptoms", [])) < 2:
            raise AssertionError(f"too few symptoms in {sid}")
        if not row.get("decisionTree"):
            raise AssertionError(f"empty decisionTree: {sid}")

        for eq in row.get("equipmentIds", []):
            if eq not in equipment_ids:
                raise AssertionError(f"unknown equipment {eq} in {sid}")
        for src in row.get("evidenceRefs", []):
            if src not in source_ids:
                raise AssertionError(f"unknown source {src} in {sid}")

        if row.get("profileGate") == "chme3t-rheostatic" and row.get("profiles") != ["ЧМЭ3Т"]:
            raise AssertionError(f"ChME3T-only gate leaked to other profiles in {sid}")

        # Explicit source-bound actions must actually cite a 996R-derived source.
        source_bound_steps = [step for step in row.get("decisionTree", []) if step.get("sourceBound")]
        if source_bound_steps and not any("996R" in src for src in row.get("evidenceRefs", [])):
            raise AssertionError(f"sourceBound step without 996R evidence in {sid}")

        # Field/local/unverified evidence may be stored freely, but cannot become an
        # ungated action authority. Our current contract has no ALLOW/REPAIR state;
        # this guard protects that invariant if a future editor invents one.
        statuses = [str(source_meta.get(src, {}).get("provenanceStatus", "")) for src in row.get("evidenceRefs", [])]
        if statuses and all(s in {"FIELD_PRACTICE", "LOCAL_INSTRUCTION", "UNVERIFIED"} for s in statuses):
            if row.get("actionAuthority") not in {"TRIAGE_ONLY_NO_REPAIR", "SOURCE_AND_PROFILE_REQUIRED", "SAFETY_GATE_REQUIRED"}:
                raise AssertionError(f"field-only evidence promoted to executable authority in {sid}")

    vocab = load("search_vocabulary_diagnostics_pass3.json")
    vocab_terms = unique((row["term"].strip().lower() for row in vocab.get("rules", [])), "diagnostic vocabulary terms")
    for rule in vocab.get("rules", []):
        for sid in rule.get("mapsTo", []):
            if sid not in scenario_ids:
                raise AssertionError(f"vocabulary maps to unknown scenario {sid}: {rule['term']}")

    # Opposite-event pairs are important for the current assistant architecture:
    # phrases with and without negation must not collapse into one failure mode.
    if len(vocab.get("negativeDisambiguation", [])) < 4:
        raise AssertionError("negativeDisambiguation corpus is unexpectedly small")

    print(
        "ChME3 diagnostics OK: "
        f"{len(scenarios)} scenarios, "
        f"{len(source_ids)} registered sources, "
        f"{len(equipment_ids)} equipment IDs, "
        f"{len(vocab_terms)} diagnostic search terms, "
        f"{len(vocab.get('negativeDisambiguation', []))} opposite-event pairs"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError) as exc:
        print(f"ChME3 diagnostic validation FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
