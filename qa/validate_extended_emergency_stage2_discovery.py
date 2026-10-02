#!/usr/bin/env python3
import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTEGRATION = ROOT / "docs/integration"

SOURCES = INTEGRATION / "extended_emergency_stage2_sources.tsv"
DISCOVERY = INTEGRATION / "extended_emergency_stage2_discovery.tsv"
CANDIDATES = INTEGRATION / "extended_emergency_stage2_candidates.tsv"

STAGE1_SHA = "5d522977f9400a17f155f37d94b2c83461ccd0dd"

CHME3_FILES = [
    ROOT / "docs/locomotives/chme3/diagnostics_powerplant_pass3.json",
    ROOT / "docs/locomotives/chme3/diagnostics_electrical_control_pass3.json",
    ROOT / "docs/locomotives/chme3/diagnostics_brake_aux_pass3.json",
    ROOT / "docs/locomotives/chme3/diagnostics_extended_pass3.json",
    ROOT / "docs/locomotives/chme3/diagnostics_996r_detail_pass3.json",
]
CHME3E_FILE = ROOT / "docs/locomotives/diesel/chme3-family/chme3e/diagnostics_stage3.json"
TEM2_FILE = ROOT / "docs/locomotives/diesel/tem2-family/common/stage3_model.json"

ALLOWED_PROFILES = {
    "chme3-base",
    "chme3t-rheostatic",
    "chme3e-electronic",
    "tem2-base",
    "tem2u-improved",
}
ALLOWED_PROVENANCE = {
    "ARCHIVED_OFFICIAL",
    "MANUFACTURER_EXTENDED",
    "HISTORICAL_TRAINING",
    "FIELD_PRACTICE",
}
ALLOWED_DISPOSITIONS = {"INFORMATION_ONLY", "PROHIBITED"}
ALLOWED_DISCOVERY = {"FOUND", "NO_ADDITIONAL_METHOD_FOUND"}

EXPECTED = {
    "chme3": 56,
    "chme3e": 12,
    "tem2": 24,
    "discovery": 92,
    "found": 76,
    "no_additional": 16,
    "candidates": 77,
    "information_only": 49,
    "prohibited": 28,
    "conditional": 0,
    "sources": 29,
}
EXPECTED_PROFILE_COUNTS = {
    "chme3-base": 53,
    "chme3t-rheostatic": 1,
    "chme3e-electronic": 0,
    "tem2-base": 22,
    "tem2u-improved": 1,
}


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_STAGE2_FAIL: {message}")


def load_json(path: Path):
    if not path.is_file():
        fail(f"missing canonical file {path.relative_to(ROOT)}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def read_tsv(path: Path):
    if not path.is_file():
        fail(f"missing Stage 2 file {path.relative_to(ROOT)}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle, delimiter="\t"))
    if not rows:
        fail(f"empty Stage 2 file {path.relative_to(ROOT)}")
    return rows


def split_pipe(value: str):
    return [item.strip() for item in (value or "").split("|") if item.strip()]


def bool_value(value: str, label: str):
    lowered = (value or "").strip().lower()
    if lowered not in {"true", "false"}:
        fail(f"{label}: expected true/false, got {value!r}")
    return lowered == "true"


def unique(rows, key, label):
    values = [row.get(key, "").strip() for row in rows]
    if any(not value for value in values):
        fail(f"{label}: missing {key}")
    if len(values) != len(set(values)):
        dup = sorted(value for value, count in Counter(values).items() if count > 1)
        fail(f"{label}: duplicate {key}: {dup}")
    return set(values)


def canonical_scenarios():
    chme3 = []
    for path in CHME3_FILES:
        data = load_json(path)
        rows = data.get("scenarios") or []
        if not rows:
            fail(f"{path.relative_to(ROOT)} has no scenarios")
        chme3.extend(row.get("id") for row in rows)

    chme3e_data = load_json(CHME3E_FILE)
    chme3e = [row.get("id") for row in (chme3e_data.get("scenarios") or [])]

    tem2_data = load_json(TEM2_FILE)
    tem2 = [row.get("id") for row in (tem2_data.get("scenarios") or [])]

    for label, rows, expected in (
        ("ChME3", chme3, EXPECTED["chme3"]),
        ("ChME3E", chme3e, EXPECTED["chme3e"]),
        ("TEM2 family", tem2, EXPECTED["tem2"]),
    ):
        if len(rows) != expected:
            fail(f"canonical {label} count drifted: {len(rows)} != {expected}")
        if any(not item for item in rows):
            fail(f"canonical {label} has scenario without id")
        if len(rows) != len(set(rows)):
            fail(f"canonical {label} contains duplicate scenario ids")

    all_rows = chme3 + chme3e + tem2
    if len(all_rows) != EXPECTED["discovery"] or len(all_rows) != len(set(all_rows)):
        fail("canonical Stage 2 scenario universe must be exactly 92 unique branches")
    return set(chme3), set(chme3e), set(tem2), set(all_rows)


def validate_sources():
    rows = read_tsv(SOURCES)
    ids = unique(rows, "id", "Stage 2 sources")
    if len(rows) != EXPECTED["sources"]:
        fail(f"source corpus count drifted: {len(rows)} != {EXPECTED['sources']}")

    framework_id = "ST2-SRC-RZD-IOT-2961R-2024"
    for row in rows:
        sid = row["id"].strip()
        for field in ("title", "url", "domain", "sourceType", "provenanceClass", "sourceStatus", "profiles", "mirrorGroup", "authority"):
            if not row.get(field, "").strip():
                fail(f"{sid}: source field {field} is required")
        current = bool_value(row.get("currentAuthorityVerified"), f"{sid}.currentAuthorityVerified")
        if current:
            if sid != framework_id:
                fail(f"{sid}: only the current safety framework may be verified at Stage 2")
            if row.get("authority") != "FRAMEWORK_ONLY_LOCAL_APPROVED_SCHEME_REQUIRED":
                fail("current safety framework must remain framework-only, not item action authority")
            if row.get("provenanceClass") != "OFFICIAL_FRAMEWORK":
                fail("current safety framework provenance must remain OFFICIAL_FRAMEWORK")
        elif sid == framework_id:
            fail("current safety framework record unexpectedly lost its framework verification")

        profiles = split_pipe(row.get("profiles"))
        if not profiles:
            fail(f"{sid}: source profiles missing")
        # General framework/locomotive rows are allowed in source corpus; candidates are stricter.

    return {row["id"].strip(): row for row in rows}


def validate_discovery(source_by_id, canonical_all):
    rows = read_tsv(DISCOVERY)
    scenario_ids = unique(rows, "standardScenarioId", "Stage 2 discovery")
    if scenario_ids != canonical_all:
        missing = sorted(canonical_all - scenario_ids)
        extra = sorted(scenario_ids - canonical_all)
        fail(f"discovery coverage mismatch; missing={missing}, extra={extra}")
    if len(rows) != EXPECTED["discovery"]:
        fail(f"discovery row count drifted: {len(rows)}")

    status_counts = Counter()
    by_scenario = {}
    for row in rows:
        sid = row["standardScenarioId"].strip()
        if not bool_value(row.get("searched"), f"{sid}.searched"):
            fail(f"{sid}: every canonical branch must have targeted search completed")
        status = row.get("discoveryStatus", "").strip()
        if status not in ALLOWED_DISCOVERY:
            fail(f"{sid}: invalid discoveryStatus {status!r}")
        status_counts[status] += 1
        refs = split_pipe(row.get("checkedSourceRefs"))
        if not refs:
            fail(f"{sid}: checkedSourceRefs required even when no method is found")
        unresolved = set(refs) - set(source_by_id)
        if unresolved:
            fail(f"{sid}: unresolved checkedSourceRefs {sorted(unresolved)}")
        if status == "NO_ADDITIONAL_METHOD_FOUND" and not row.get("noAdditionalReason", "").strip():
            fail(f"{sid}: NO_ADDITIONAL_METHOD_FOUND requires explicit reason")
        if status == "FOUND" and row.get("noAdditionalReason", "").strip():
            fail(f"{sid}: FOUND row must not carry noAdditionalReason")
        by_scenario[sid] = row

    if status_counts["FOUND"] != EXPECTED["found"]:
        fail(f"FOUND count drifted: {status_counts['FOUND']} != {EXPECTED['found']}")
    if status_counts["NO_ADDITIONAL_METHOD_FOUND"] != EXPECTED["no_additional"]:
        fail(f"NO_ADDITIONAL count drifted: {status_counts['NO_ADDITIONAL_METHOD_FOUND']} != {EXPECTED['no_additional']}")

    expected_no = {
        "CHME3-DIAG-319", "CHME3-DIAG-320",
        *{f"CHME3E-DIAG-{i:03d}" for i in range(1, 13)},
        "TEM2-DIAG-022", "TEM2-DIAG-023",
    }
    actual_no = {sid for sid, row in by_scenario.items() if row["discoveryStatus"] == "NO_ADDITIONAL_METHOD_FOUND"}
    if actual_no != expected_no:
        fail(f"fail-closed no-additional set drifted; actual={sorted(actual_no)}")

    return by_scenario


def validate_candidates(source_by_id, discovery_by_scenario, chme3_ids, chme3e_ids, tem2_ids):
    rows = read_tsv(CANDIDATES)
    unique(rows, "candidateId", "Stage 2 candidates")
    if len(rows) != EXPECTED["candidates"]:
        fail(f"candidate count drifted: {len(rows)} != {EXPECTED['candidates']}")

    disposition_counts = Counter()
    profile_counts = Counter()
    candidates_by_scenario = defaultdict(list)
    hazardous_method_markers = ("BYPASS", "OVERRIDE", "INTERVENTION", "MANUAL_ACTUATION")
    forbidden_detail_patterns = [
        re.compile(r"между\s+провод", re.I),
        re.compile(r"нажать\s+на\s+якор", re.I),
        re.compile(r"замкнуть\s+контактор", re.I),
        re.compile(r"зашунт", re.I),
        re.compile(r"заклинить", re.I),
        re.compile(r"\b\d{2,4}\s*[-–]\s*\d{2,4}\b"),
    ]

    for row in rows:
        cid = row["candidateId"].strip()
        sid = row.get("standardScenarioId", "").strip()
        if sid not in discovery_by_scenario:
            fail(f"{cid}: unknown standardScenarioId {sid}")
        if discovery_by_scenario[sid]["discoveryStatus"] != "FOUND":
            fail(f"{cid}: candidate attached to NO_ADDITIONAL branch {sid}")

        refs = split_pipe(row.get("sourceRefs"))
        if not refs:
            fail(f"{cid}: sourceRefs required")
        unresolved = set(refs) - set(source_by_id)
        if unresolved:
            fail(f"{cid}: unresolved sourceRefs {sorted(unresolved)}")
        if all(source_by_id[ref].get("authority") == "DISCOVERY_ONLY" for ref in refs):
            fail(f"{cid}: candidate cannot rely only on DISCOVERY_ONLY source indexes")

        profiles = split_pipe(row.get("profiles"))
        if not profiles or not set(profiles).issubset(ALLOWED_PROFILES):
            fail(f"{cid}: invalid profiles {profiles}")
        for profile in profiles:
            profile_counts[profile] += 1

        provenance = row.get("provenanceClass", "").strip()
        if provenance not in ALLOWED_PROVENANCE:
            fail(f"{cid}: candidate provenance must use expanded-mode vocabulary, got {provenance!r}")
        if not row.get("sourceStatus", "").strip() or not row.get("riskClass", "").strip():
            fail(f"{cid}: sourceStatus and riskClass are required")

        disposition = row.get("actionDisposition", "").strip()
        if disposition not in ALLOWED_DISPOSITIONS:
            fail(f"{cid}: Stage 2 cannot promote to {disposition!r}")
        disposition_counts[disposition] += 1
        if bool_value(row.get("currentAuthorityVerified"), f"{cid}.currentAuthorityVerified"):
            fail(f"{cid}: candidate item-level current authority cannot be true in Stage 2")

        method = row.get("methodClass", "").strip()
        if not method:
            fail(f"{cid}: methodClass required")
        if any(marker in method for marker in hazardous_method_markers) and disposition != "PROHIBITED":
            # Localisation-only names that contain no hazardous marker are unaffected.
            fail(f"{cid}: hazardous method class {method} must remain PROHIBITED")
        if not row.get("dedupGroup", "").strip():
            fail(f"{cid}: dedupGroup required so mirrors cannot count as independent confirmation")

        raw = "\t".join(row.values())
        for pattern in forbidden_detail_patterns:
            if pattern.search(raw):
                fail(f"{cid}: procedure-level hazardous detail leaked into research inventory")

        # Profile isolation: no base ChME3 field method can be silently inherited to ChME3E.
        if "chme3e-electronic" in profiles:
            fail(f"{cid}: no ChME3E-specific expanded method was confirmed by Stage 2 discovery")
        if sid == "CHME3-DIAG-110" and profiles != ["chme3t-rheostatic"]:
            fail("CHME3-DIAG-110 EDB candidate must be ChME3T-only")
        if "chme3t-rheostatic" in profiles and sid != "CHME3-DIAG-110":
            fail(f"{cid}: only variant-specific ChME3T EDB evidence was confirmed in this Stage 2 pass")

        if "tem2u-improved" in profiles and cid != "EXT2-TEM2-DIAG-024-TEM2U":
            fail(f"{cid}: TEM2 base emergency methods must not auto-inherit to TEM2U")
        if cid == "EXT2-TEM2-DIAG-024-TEM2U" and profiles != ["tem2u-improved"]:
            fail("TEM2U emergency-fuel candidate must remain an explicit TEM2U-only record")

        if sid.startswith("CHME3E-") and sid not in chme3e_ids:
            fail(f"{cid}: unknown ChME3E scenario")
        if sid.startswith("CHME3-") and sid not in chme3_ids:
            fail(f"{cid}: unknown ChME3 scenario")
        if sid.startswith("TEM2-") and sid not in tem2_ids:
            fail(f"{cid}: unknown TEM2 scenario")

        candidates_by_scenario[sid].append(row)

    for sid, discovery in discovery_by_scenario.items():
        count = len(candidates_by_scenario.get(sid, []))
        if discovery["discoveryStatus"] == "FOUND" and count < 1:
            fail(f"{sid}: FOUND without candidate record")
        if discovery["discoveryStatus"] == "NO_ADDITIONAL_METHOD_FOUND" and count:
            fail(f"{sid}: NO_ADDITIONAL branch unexpectedly has candidates")

    if disposition_counts["INFORMATION_ONLY"] != EXPECTED["information_only"]:
        fail(f"INFORMATION_ONLY count drifted: {disposition_counts['INFORMATION_ONLY']} != {EXPECTED['information_only']}")
    if disposition_counts["PROHIBITED"] != EXPECTED["prohibited"]:
        fail(f"PROHIBITED count drifted: {disposition_counts['PROHIBITED']} != {EXPECTED['prohibited']}")
    if disposition_counts.get("CONDITIONAL_ACTION", 0) != EXPECTED["conditional"]:
        fail("Stage 2 must have zero CONDITIONAL_ACTION candidates")

    for profile, expected in EXPECTED_PROFILE_COUNTS.items():
        if profile_counts[profile] != expected:
            fail(f"candidate profile count drifted for {profile}: {profile_counts[profile]} != {expected}")

    return disposition_counts, profile_counts


def main():
    chme3_ids, chme3e_ids, tem2_ids, canonical_all = canonical_scenarios()
    source_by_id = validate_sources()
    discovery_by_scenario = validate_discovery(source_by_id, canonical_all)
    disposition_counts, profile_counts = validate_candidates(
        source_by_id, discovery_by_scenario, chme3_ids, chme3e_ids, tem2_ids
    )

    print(
        "EXTENDED EMERGENCY STAGE 2 PASS: "
        f"{len(canonical_all)}/92 canonical branches searched; "
        f"76 FOUND / 16 NO_ADDITIONAL_METHOD_FOUND; "
        f"77 candidates = {disposition_counts['INFORMATION_ONLY']} INFORMATION_ONLY + "
        f"{disposition_counts['PROHIBITED']} PROHIBITED; 0 CONDITIONAL_ACTION; "
        f"profile counts {dict(profile_counts)}; source/mirror/profile isolation OK"
    )


if __name__ == "__main__":
    main()
