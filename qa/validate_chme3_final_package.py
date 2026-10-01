#!/usr/bin/env python3
"""Final cross-layer validation for the ChME3/ChME3T knowledge foundation.

This validator does not replace the dedicated Atlas, diagnostics, semantic and
acceptance checks. It proves that the layers agree on the same namespaces and
that profile/source/search boundaries survive when the package is viewed as a
whole.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "docs" / "locomotives" / "chme3"


def load_path(path: Path):
    if not path.exists():
        raise AssertionError(f"missing required file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def load(name: str):
    return load_path(PKG / name)


def unique(values, label: str) -> set[str]:
    seen: set[str] = set()
    dup: set[str] = set()
    for value in values:
        if value in seen:
            dup.add(value)
        seen.add(value)
    if dup:
        raise AssertionError(f"duplicate {label}: {sorted(dup)}")
    return seen


def rows_from(document: dict) -> list[dict]:
    for key in ("records", "equipment"):
        rows = document.get(key)
        if isinstance(rows, list):
            return rows
    return []


def source_namespace() -> tuple[set[str], dict[str, dict], int]:
    files = sorted(set(PKG.glob("*source_registry*.json")))
    if not files:
        raise AssertionError("no source registries")
    rows: list[dict] = []
    by_id: dict[str, dict] = {}
    for path in files:
        local = load_path(path).get("sources", [])
        unique((row["id"] for row in local), f"source IDs in {path.name}")
        for row in local:
            sid = row.get("id")
            if not sid or not row.get("title"):
                raise AssertionError(f"source without id/title in {path.name}")
            status = row.get("provenanceStatus") or row.get("status")
            if not status:
                raise AssertionError(f"source {sid} has no provenance status")
            if sid in by_id:
                raise AssertionError(f"source {sid} duplicated across registries")
            by_id[sid] = row
            rows.append(row)
    return set(by_id), by_id, len(files)


def require_source(ref: str, source_ids: set[str], where: str):
    if ref not in source_ids:
        raise AssertionError(f"unknown source {ref!r} in {where}")


def main() -> int:
    source_ids, source_meta, source_file_count = source_namespace()

    profiles = load("profiles.json")
    profile_rows = profiles.get("profiles", [])
    profile_ids = unique((row["id"] for row in profile_rows), "profile IDs")
    profile_series = unique((row["series"] for row in profile_rows), "profile series")
    expected_profiles = {"ЧМЭ3", "ЧМЭ3Т", "ЧМЭ3Э"}
    if profile_series != expected_profiles:
        raise AssertionError(f"unexpected profile series: {sorted(profile_series)}")
    for row in profile_rows:
        for ref in row.get("evidenceRefs", []):
            require_source(ref, source_ids, f"profile {row['id']}")

    atlas = load("atlas_index.json")
    atlas_ext = load("atlas_index_extension.json")
    systems = atlas.get("systems", [])
    system_ids = unique((row["id"] for row in systems), "Atlas system IDs")
    equipment_rows = atlas.get("equipment", []) + atlas_ext.get("equipment", [])
    equipment_ids = unique((row["id"] for row in equipment_rows), "Atlas equipment IDs")
    if len(equipment_ids) != 70:
        raise AssertionError(f"expected 70 canonical Atlas equipment IDs, got {len(equipment_ids)}")
    equipment_by_id = {row["id"]: row for row in equipment_rows}
    for document_name, document in (("atlas_index.json", atlas), ("atlas_index_extension.json", atlas_ext)):
        for ref in document.get("sourceRefs", []) + document.get("evidenceRefs", []):
            require_source(ref, source_ids, document_name)
    for row in equipment_rows:
        if row.get("systemId") not in system_ids:
            raise AssertionError(f"unknown system {row.get('systemId')} for {row['id']}")
        applicability = set(row.get("applicability", []))
        if not applicability or not applicability.issubset(expected_profiles):
            raise AssertionError(f"bad applicability for {row['id']}: {sorted(applicability)}")
        is_edb = row["id"].startswith("CHME3T-") or row.get("systemId") == "CHME3-SYS-EDB"
        if is_edb and applicability != {"ЧМЭ3Т"}:
            raise AssertionError(f"ChME3T-specific Atlas equipment leaks to another profile: {row['id']} {sorted(applicability)}")

    card_paths = sorted(PKG.glob("atlas_cards_*.json"))
    card_rows: list[dict] = []
    card_origin: dict[str, str] = {}
    for path in card_paths:
        for row in rows_from(load_path(path)):
            cid = row["id"]
            if cid in card_origin:
                raise AssertionError(f"card {cid} duplicated in {card_origin[cid]} and {path.name}")
            card_origin[cid] = path.name
            card_rows.append(row)
    card_ids = {row["id"] for row in card_rows}
    if card_ids != equipment_ids:
        raise AssertionError(
            f"Atlas card coverage mismatch; missing={sorted(equipment_ids-card_ids)}, extra={sorted(card_ids-equipment_ids)}"
        )
    for row in card_rows:
        for ref in row.get("relatedIds", []):
            if ref not in equipment_ids:
                raise AssertionError(f"dangling related equipment {ref} in card {row['id']}")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, source_ids, f"card {row['id']}")
        if not row.get("assistantTerms"):
            raise AssertionError(f"card {row['id']} has no assistantTerms")

    scheme_paths = sorted(PKG.glob("atlas_schemes_*.json"))
    scheme_rows: list[dict] = []
    scheme_ids: set[str] = set()
    for path in scheme_paths:
        for row in load_path(path).get("records", []):
            if row["id"] in scheme_ids:
                raise AssertionError(f"duplicate scheme {row['id']}")
            scheme_ids.add(row["id"])
            scheme_rows.append(row)
    if len(scheme_ids) != 12:
        raise AssertionError(f"expected 12 functional schemes, got {len(scheme_ids)}")
    for row in scheme_rows:
        applicability = set(row.get("applicability", []))
        if not applicability or not applicability.issubset(expected_profiles):
            raise AssertionError(f"bad scheme applicability in {row['id']}: {sorted(applicability)}")
        for eq in row.get("nodes", []):
            if eq not in equipment_ids:
                raise AssertionError(f"scheme {row['id']} references unknown equipment {eq}")
            if (eq.startswith("CHME3T-") or equipment_by_id[eq].get("systemId") == "CHME3-SYS-EDB") and "ЧМЭ3Т" not in applicability:
                raise AssertionError(f"scheme {row['id']} exposes ChME3T-only node {eq} outside ChME3T applicability")
        for flow in row.get("flows", []):
            if len(flow) < 2 or flow[0] not in equipment_ids or flow[1] not in equipment_ids:
                raise AssertionError(f"bad scheme flow in {row['id']}: {flow}")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, source_ids, f"scheme {row['id']}")

    diag_paths = sorted(PKG.glob("diagnostics_*_pass3.json"))
    scenarios: list[dict] = []
    for path in diag_paths:
        scenarios.extend(load_path(path).get("scenarios", []))
    scenario_ids = unique((row["id"] for row in scenarios), "diagnostic scenario IDs")
    if len(scenario_ids) != 56:
        raise AssertionError(f"expected 56 diagnostic source scenarios, got {len(scenario_ids)}")
    scenario_by_id = {row["id"]: row for row in scenarios}
    for row in scenarios:
        for eq in row.get("equipmentIds", []):
            if eq not in equipment_ids:
                raise AssertionError(f"diagnostic {row['id']} references unknown equipment {eq}")
        for ref in row.get("evidenceRefs", []):
            require_source(ref, source_ids, f"diagnostic {row['id']}")
        if row.get("profileGate") == "chme3t-rheostatic" and row.get("profiles") != ["ЧМЭ3Т"]:
            raise AssertionError(f"ChME3T profile gate leaked in {row['id']}")
    edb = scenario_by_id.get("CHME3-DIAG-110")
    if not edb or edb.get("profiles") != ["ЧМЭ3Т"] or edb.get("profileGate") != "chme3t-rheostatic":
        raise AssertionError("CHME3-DIAG-110 must remain ChME3T-only and profile-gated")

    graph_paths = sorted(PKG.glob("diagnostic_runtime_graphs_v2*.json"))
    graphs: dict[str, dict] = {}
    for path in graph_paths:
        for row in load_path(path).get("scenarios", []):
            sid = row["scenarioId"]
            if sid in graphs:
                raise AssertionError(f"duplicate runtime graph for {sid}")
            graphs[sid] = row.get("runtimeGraph", {})
    if set(graphs) != scenario_ids:
        raise AssertionError(
            f"runtime/source scenario mismatch; missing={sorted(scenario_ids-set(graphs))}, extra={sorted(set(graphs)-scenario_ids)}"
        )
    for sid, graph in graphs.items():
        if not graph.get("startNodeId") or not graph.get("nodes"):
            raise AssertionError(f"incomplete runtime graph {sid}")
        for node in graph.get("nodes", []):
            for ref in node.get("sourceRefs", []):
                require_source(ref, source_ids, f"runtime graph {sid}/{node.get('id')}")

    acceptance = load("acceptance_contract_pass4.json")
    required = load("acceptance_required_pass4.json")
    acceptance_items = required.get("items", [])
    acceptance_ids = unique((row["id"] for row in acceptance_items), "required acceptance IDs")
    if len(acceptance_ids) != 21:
        raise AssertionError(f"expected 21 required/profile acceptance items, got {len(acceptance_ids)}")
    common_count = sum("profileGate" not in row for row in acceptance_items)
    if common_count != 20:
        raise AssertionError(f"expected 20 common acceptance items, got {common_count}")
    for row in acceptance_items:
        for eq in row.get("equipmentIds", []):
            if eq not in equipment_ids:
                raise AssertionError(f"acceptance {row['id']} references unknown equipment {eq}")
        for sid in row.get("diagnosticIds", []):
            if sid not in scenario_ids or sid not in graphs:
                raise AssertionError(f"acceptance {row['id']} references unpromoted/unknown diagnostic {sid}")
        for ref in row.get("sourceRefs", []):
            require_source(ref, source_ids, f"acceptance {row['id']}")
    for ref in acceptance.get("sourceRefs", []):
        require_source(ref, source_ids, "acceptance contract")

    profile_rules = {row["profile"]: row for row in acceptance.get("profileRules", [])}
    if set(profile_rules) != expected_profiles:
        raise AssertionError(f"acceptance profile rules mismatch: {sorted(profile_rules)}")
    chme3t_rule = profile_rules["ЧМЭ3Т"]
    expected_edb_ids = {"CHME3T-EQ-BRAKE-RESISTORS", "CHME3T-EQ-BRAKE-RESISTOR-FAN", "CHME3T-EQ-EDB-CONTROL"}
    if set(chme3t_rule.get("includeEquipmentIds", [])) != expected_edb_ids:
        raise AssertionError("ChME3T acceptance EDB equipment set changed unexpectedly")
    for base in ("ЧМЭ3", "ЧМЭ3Э"):
        if "CHME3-SYS-EDB" not in profile_rules[base].get("excludeSystems", []):
            raise AssertionError(f"{base} acceptance no longer excludes EDB")

    # Reproduce deterministic expanded acceptance coverage from the Atlas phase rules.
    phase_rules = acceptance.get("phaseRules", {})
    covered: set[str] = set()
    for rule in phase_rules.values():
        systems_for_phase = set(rule.get("systems", []))
        excluded = set(rule.get("excludeEquipmentIds", []))
        phase_ids = {row["id"] for row in equipment_rows if row.get("systemId") in systems_for_phase}
        phase_ids.update(rule.get("alsoEquipmentIds", []))
        phase_ids.difference_update(excluded)
        unknown = phase_ids - equipment_ids
        if unknown:
            raise AssertionError(f"acceptance phase references unknown equipment: {sorted(unknown)}")
        covered.update(phase_ids)
    if covered != equipment_ids:
        raise AssertionError(
            f"expanded acceptance does not cover Atlas exactly; missing={sorted(equipment_ids-covered)}, extra={sorted(covered-equipment_ids)}"
        )

    # Search semantics: component language must exist and diagnostic vocab may only route
    # to scenarios that are actually present and promoted.
    vocab = load("search_vocabulary.json")
    if not vocab.get("seriesAliases") or not vocab.get("componentCandidates"):
        raise AssertionError("base assistant search vocabulary is empty")
    for row in vocab.get("componentCandidates", []):
        if not row.get("canonical") or not row.get("aliases") or not row.get("symptomTerms"):
            raise AssertionError(f"incomplete component search candidate: {row.get('key')}")
    negative_pairs = vocab.get("rules", {}).get("negativePairsMustStayDistinct", [])
    if len(negative_pairs) < 4 or any(len(pair) != 2 or pair[0] == pair[1] for pair in negative_pairs):
        raise AssertionError("base opposite-event search corpus is incomplete")

    addon = load("diagnostic_search_vocabulary_addendum.json")
    if not addon.get("terms") or len(addon.get("regressionPairs", [])) < 5:
        raise AssertionError("diagnostic conversational vocabulary/regression pairs are incomplete")
    for row in addon.get("terms", []):
        if not row.get("canonical") or not row.get("technical") or not row.get("conversational"):
            raise AssertionError(f"incomplete diagnostic vocabulary term: {row.get('canonical')}")

    mapped_terms = 0
    opposite_pairs = 0
    for path in sorted(PKG.glob("search_vocabulary_diagnostics*_pass3.json")):
        data = load_path(path)
        for rule in data.get("rules", []):
            mapped_terms += 1
            for sid in rule.get("mapsTo", []):
                if sid not in scenario_ids or sid not in graphs:
                    raise AssertionError(f"search term {rule.get('term')!r} maps to unpromoted/unknown {sid}")
        opposite_pairs += len(data.get("negativeDisambiguation", []))
    if mapped_terms < 80:
        raise AssertionError(f"diagnostic mapped search corpus unexpectedly small: {mapped_terms}")
    if opposite_pairs < 8:
        raise AssertionError(f"diagnostic opposite-event corpus unexpectedly small: {opposite_pairs}")

    print(
        "ChME3 FINAL PACKAGE OK: "
        f"{len(profile_ids)} profiles, {len(source_ids)} sources/{source_file_count} registries, "
        f"{len(system_ids)} systems, {len(equipment_ids)} equipment cards, {len(scheme_ids)} schemes, "
        f"{len(scenario_ids)}/{len(graphs)} source/runtime diagnostics, "
        f"{len(acceptance_ids)} required acceptance items, 70/70 expanded acceptance coverage, "
        f"{mapped_terms} mapped diagnostic search terms, {opposite_pairs} diagnostic opposite-event pairs"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError) as exc:
        print(f"ChME3 FINAL PACKAGE validation FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
