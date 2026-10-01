#!/usr/bin/env python3
"""Validate all ChME3 pass-3 diagnostics and search vocabularies."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "docs" / "locomotives" / "chme3"


def load_path(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def load(name: str):
    path = PKG / name
    if not path.exists():
        raise AssertionError(f"missing required file: {path.relative_to(ROOT)}")
    return load_path(path)


def unique(values, label: str):
    seen = set()
    duplicates = set()
    for value in values:
        if value in seen:
            duplicates.add(value)
        seen.add(value)
    if duplicates:
        raise AssertionError(f"duplicate {label}: {sorted(duplicates)}")
    return seen


def equipment_namespace() -> set[str]:
    ids: set[str] = set()
    for path in sorted(PKG.glob("atlas_index*.json")):
        data = load_path(path)
        rows = data.get("equipment", [])
        unique((row["id"] for row in rows), f"equipment IDs inside {path.name}")
        ids.update(row["id"] for row in rows)
    for path in sorted(PKG.glob("atlas_cards_*.json")):
        data = load_path(path)
        rows = data.get("records", []) or data.get("equipment", [])
        unique((row["id"] for row in rows), f"equipment IDs inside {path.name}")
        ids.update(row["id"] for row in rows)
    if not ids:
        raise AssertionError("empty Atlas equipment namespace")
    return ids


def source_namespace():
    rows = []
    meta = {}
    files = sorted(set(PKG.glob("*source_registry*.json")))
    if not files:
        raise AssertionError("no source registries found")
    for path in files:
        data = load_path(path)
        local = data.get("sources", [])
        unique((row["id"] for row in local), f"source IDs inside {path.name}")
        for row in local:
            rows.append(row)
            meta[row["id"]] = row
    ids = unique((row["id"] for row in rows), "source IDs across registries")
    return ids, meta


def main() -> int:
    equipment_ids = equipment_namespace()
    source_ids, source_meta = source_namespace()

    diag_files = sorted(PKG.glob("diagnostics_*_pass3.json"))
    if len(diag_files) < 4:
        raise AssertionError(f"expected at least 4 diagnostic fragments, found {len(diag_files)}")
    scenarios = []
    for path in diag_files:
        rows = load_path(path).get("scenarios", [])
        unique((row["id"] for row in rows), f"scenario IDs inside {path.name}")
        scenarios.extend(rows)
    scenario_ids = unique((row["id"] for row in scenarios), "scenario IDs across pass 3")

    allowed_authority = {
        "TRIAGE_ONLY_NO_REPAIR",
        "SOURCE_AND_PROFILE_REQUIRED",
        "SAFETY_GATE_REQUIRED",
        "EMERGENCY_SOURCE_BOUND",
    }
    id_re = re.compile(r"^CHME3-DIAG-\d{3}$")
    profile_values = {"ЧМЭ3", "ЧМЭ3Т", "ЧМЭ3Э"}

    for row in scenarios:
        sid = row["id"]
        if not id_re.fullmatch(sid):
            raise AssertionError(f"bad scenario ID: {sid}")
        if not row.get("title") or not row.get("riskClass"):
            raise AssertionError(f"incomplete scenario metadata: {sid}")
        profiles = row.get("profiles", [])
        if not profiles or not set(profiles).issubset(profile_values):
            raise AssertionError(f"bad profiles in {sid}: {profiles}")
        if row.get("actionAuthority") not in allowed_authority:
            raise AssertionError(f"bad authority in {sid}: {row.get('actionAuthority')}")
        if len(row.get("symptoms", [])) < 2 or len(row.get("queryTerms", [])) < 3:
            raise AssertionError(f"insufficient search semantics: {sid}")
        if not row.get("decisionTree"):
            raise AssertionError(f"empty decision tree: {sid}")
        for eq in row.get("equipmentIds", []):
            if eq not in equipment_ids:
                raise AssertionError(f"unknown equipment {eq} in {sid}")
        refs = row.get("evidenceRefs", [])
        if not refs:
            raise AssertionError(f"scenario without evidence: {sid}")
        for src in refs:
            if src not in source_ids:
                raise AssertionError(f"unknown source {src} in {sid}")

        if row.get("profileGate") == "chme3t-rheostatic" and profiles != ["ЧМЭ3Т"]:
            raise AssertionError(f"ChME3T EDB gate leaked to other profiles: {sid}")

        source_bound = any(step.get("sourceBound") for step in row.get("decisionTree", []))
        if source_bound and not any("996R" in src for src in refs):
            raise AssertionError(f"sourceBound step without 996R evidence: {sid}")

        statuses = [str(source_meta.get(src, {}).get("provenanceStatus", "")) for src in refs]
        field_only = statuses and all(s in {"FIELD_PRACTICE", "LOCAL_INSTRUCTION", "UNVERIFIED"} for s in statuses)
        if field_only and row.get("actionAuthority") == "EMERGENCY_SOURCE_BOUND":
            raise AssertionError(f"field-only evidence promoted to emergency source-bound action: {sid}")

        # Do not allow a raw jumper/bypass/defeat command to slip into runtime trees.
        runtime_text = json.dumps(row.get("decisionTree", []), ensure_ascii=False).lower()
        forbidden = ["поставить перемычку", "заклинить защит", "обойти защит", "зашунтировать защит"]
        for phrase in forbidden:
            if phrase in runtime_text:
                raise AssertionError(f"unsafe un-gated bypass phrase {phrase!r} in {sid}")

    vocab_files = sorted(PKG.glob("search_vocabulary_diagnostics*_pass3.json"))
    if len(vocab_files) < 2:
        raise AssertionError("extended diagnostic vocabulary is missing")
    vocab_rules = []
    opposite_pairs = []
    for path in vocab_files:
        data = load_path(path)
        vocab_rules.extend(data.get("rules", []))
        opposite_pairs.extend(data.get("negativeDisambiguation", []))
    terms = unique((row["term"].strip().lower() for row in vocab_rules), "diagnostic vocabulary terms")
    for rule in vocab_rules:
        for sid in rule.get("mapsTo", []):
            if sid not in scenario_ids:
                raise AssertionError(f"vocabulary maps to unknown scenario {sid}: {rule['term']}")
        if rule.get("profileGate") == "chme3t-rheostatic":
            if any(sid != "CHME3-DIAG-110" for sid in rule.get("mapsTo", [])):
                raise AssertionError(f"EDB vocabulary gate maps outside ChME3T EDB: {rule['term']}")

    if len(opposite_pairs) < 8:
        raise AssertionError(f"opposite-event corpus too small: {len(opposite_pairs)}")

    print(
        "ChME3 diagnostics v3 OK: "
        f"{len(scenarios)} scenarios in {len(diag_files)} fragments, "
        f"{len(source_ids)} sources, {len(equipment_ids)} equipment IDs, "
        f"{len(terms)} search terms, {len(opposite_pairs)} opposite-event pairs"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError) as exc:
        print(f"ChME3 diagnostic validation v3 FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
