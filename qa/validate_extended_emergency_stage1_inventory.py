#!/usr/bin/env python3
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INV = ROOT / "docs/integration/extended_emergency_stage1_inventory.json"
SRC = ROOT / "docs/integration/extended_emergency_stage1_sources.json"
POLICY = ROOT / "app/src/main/assets/technical/extended_emergency_policy.json"
CHME_BASE = ROOT / "docs/locomotives/chme3/source_registry.json"
CHME_ADD = ROOT / "docs/locomotives/chme3/diagnostic_source_registry_addendum.json"
CHME_MANIFEST = ROOT / "docs/locomotives/diesel/chme3-family/family_manifest.json"
TEM2_SRC = ROOT / "docs/locomotives/diesel/tem2-family/common/source_registry.json"
TEM2_PROFILE = ROOT / "docs/locomotives/diesel/tem2-family/common/profile_contract.json"


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_STAGE1_FAIL: {message}")


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing {path.relative_to(ROOT)}")
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {exc}")


def ids(rows, label):
    result = [row.get("id") for row in rows]
    if any(not x for x in result):
        fail(f"{label}: missing id")
    if len(result) != len(set(result)):
        fail(f"{label}: duplicate id")
    return set(result)


inventory = load(INV)
sources_stage1 = load(SRC)
policy = load(POLICY)
chme_base = load(CHME_BASE)
chme_add = load(CHME_ADD)
chme_manifest = load(CHME_MANIFEST)
tem2_sources = load(TEM2_SRC)
tem2_profile = load(TEM2_PROFILE)

if inventory.get("schemaVersion") != 1 or inventory.get("stage") != 1:
    fail("inventory schema/stage mismatch")
if inventory.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
    fail("wrong modeId")
if inventory.get("state") != "CANDIDATE_AND_EVIDENCE_INVENTORY_RESEARCH_ONLY":
    fail("Stage 1 must remain research-only")

scope = inventory.get("scope") or {}
expected_profiles = {
    "chme3-base", "chme3t-rheostatic", "chme3e-electronic", "tem2-base", "tem2u-improved"
}
if set(scope.get("profiles") or []) != expected_profiles:
    fail(f"profile scope mismatch: {scope.get('profiles')}")
if set(scope.get("families") or []) != {"chme3-family", "tem2-family"}:
    fail("family scope mismatch")
if scope.get("technicalDetailPolicy") != "ABSTRACT_ONLY_NO_PROCEDURE":
    fail("Stage 1 inventory must not store procedural wiring instructions")

inv_policy = inventory.get("policy") or {}
required_policy = {
    "allCandidatesResearchOnly": True,
    "currentPrimaryAuthorityRequiredForExecutableAction": True,
    "fieldPracticeDoesNotAuthorizeAction": True,
    "historicalTrainingDoesNotAuthorizeAction": True,
    "manufacturerBaselineDoesNotAuthorizeCurrentAction": True,
    "secondary996IndexDoesNotAuthorizeItemLevelAction": True,
    "bypassProtectionOrManualPowerApparatusDefault": "PROHIBITED",
    "unknownExecution": "FAIL_CLOSED",
    "adjacentVariantInheritance": "DENY_UNLESS_EXPLICITLY_EVIDENCED",
    "stage1AllowsConditionalAction": False,
}
for key, expected in required_policy.items():
    if inv_policy.get(key) != expected:
        fail(f"policy drift: {key}={inv_policy.get(key)!r}")

runtime_policy = policy.get("runtimeContract") or {}
if runtime_policy.get("extendedModeMayUpgradeAuthority") is not False:
    fail("Stage 0 authority boundary drifted")
if runtime_policy.get("bypassOrProtectionDefeatDispositionDefault") != "PROHIBITED":
    fail("Stage 0 bypass default drifted")
if runtime_policy.get("unknownExecution") != "FAIL_CLOSED":
    fail("Stage 0 unknown-execution rule drifted")

allowed_provenance = {row.get("id") for row in policy.get("provenanceClasses") or []}
allowed_dispositions = {row.get("id") for row in policy.get("actionDispositions") or []}
if allowed_dispositions != {"INFORMATION_ONLY", "CONDITIONAL_ACTION", "PROHIBITED"}:
    fail("Stage 0 disposition vocabulary drifted")

source_sets = []
for data, key, label in (
    (chme_base, "sources", "ChME3 base sources"),
    (chme_add, "sources", "ChME3 diagnostic sources"),
    (tem2_sources, "sources", "TEM2 sources"),
    (sources_stage1, "sources", "Stage1 sources"),
):
    source_sets.append(ids(data.get(key) or [], label))
all_source_ids = set().union(*source_sets)
if sum(len(s) for s in source_sets) != len(all_source_ids):
    fail("source IDs collide across registries")

s1_policy = sources_stage1.get("policy") or {}
if s1_policy.get("sourceIndexIsNotPrimaryText") is not True:
    fail("Stage1 source register may not treat index as primary")
if s1_policy.get("fieldOrTrainingNeverAutoAuthorizesAction") is not True:
    fail("Stage1 source authority rule missing")
if s1_policy.get("unknownExecution") != "FAIL_CLOSED":
    fail("Stage1 source unknown-execution rule missing")

s1_by_id = {row["id"]: row for row in sources_stage1.get("sources") or []}
idx996 = s1_by_id.get("EXT-STAGE1-996R-2024-INDEX")
if not idx996:
    fail("996/r Stage1 index record missing")
if idx996.get("evidenceLevel") != "SECONDARY_INDEX" or idx996.get("authority") != "SOURCE_INDEX_ONLY":
    fail("996/r index was promoted beyond secondary/index evidence")
if set(idx996.get("explicitSeriesListed") or []) != {"ЧМЭ3", "ТЭМ2"}:
    fail("996/r explicit-series evidence changed")
if not {"chme3t-rheostatic", "chme3e-electronic", "tem2u-improved"}.issubset(
    set(idx996.get("notExplicitlyProvenForProfiles") or [])
):
    fail("996/r variant scope must remain unresolved")

candidates = inventory.get("candidates") or []
candidate_ids = ids(candidates, "Stage1 candidates")
if len(candidates) != 19:
    fail(f"expected 19 evidence-backed inventory candidates, got {len(candidates)}")

family_profiles = {
    "chme3-family": {"chme3-base", "chme3t-rheostatic", "chme3e-electronic"},
    "tem2-family": {"tem2-base", "tem2u-improved"},
}
required_fields = {
    "id", "familyId", "profiles", "topic", "candidateType", "sourceRefs", "sourceStatus",
    "provenanceClass", "evidenceLevel", "riskClass", "actionDisposition",
    "currentAuthorityVerified", "profileApplicabilityVerified", "safetyGateRequired",
    "executionGate", "adjacentVariantInheritance", "researchSummary", "nextResearchNeeded",
}
for row in candidates:
    missing_fields = [key for key in required_fields if key not in row]
    if missing_fields:
        fail(f"{row.get('id')}: missing fields {missing_fields}")
    family = row.get("familyId")
    if family not in family_profiles:
        fail(f"{row['id']}: invalid family {family}")
    profiles = set(row.get("profiles") or [])
    if not profiles or not profiles.issubset(family_profiles[family]):
        fail(f"{row['id']}: invalid profiles {sorted(profiles)} for {family}")
    refs = set(row.get("sourceRefs") or [])
    if not refs:
        fail(f"{row['id']}: missing source refs")
    unresolved = refs - all_source_ids
    if unresolved:
        fail(f"{row['id']}: unresolved source refs {sorted(unresolved)}")
    if row.get("provenanceClass") not in allowed_provenance:
        fail(f"{row['id']}: invalid provenance class {row.get('provenanceClass')}")
    disposition = row.get("actionDisposition")
    if disposition not in allowed_dispositions:
        fail(f"{row['id']}: invalid disposition {disposition}")
    if disposition == "CONDITIONAL_ACTION":
        fail(f"{row['id']}: Stage 1 may not promote a candidate to CONDITIONAL_ACTION")
    if row.get("currentAuthorityVerified") is not False:
        fail(f"{row['id']}: Stage 1 has no item-level current primary authority")
    if row.get("executionGate") != "FAIL_CLOSED":
        fail(f"{row['id']}: execution must fail closed")
    if row.get("adjacentVariantInheritance") != "DENY":
        fail(f"{row['id']}: adjacent variant inheritance must be denied")
    if not row.get("nextResearchNeeded"):
        fail(f"{row['id']}: no research path")
    if row.get("candidateType") == "PROHIBITED_ANTIPATTERN" and disposition != "PROHIBITED":
        fail(f"{row['id']}: prohibited anti-pattern became displayable")
    topic = (row.get("topic") or "").lower()
    if any(token in topic for token in ("обход защит", "цепи эпк", "принудительное включение", "ручное включение пусковых")):
        if disposition != "PROHIBITED":
            fail(f"{row['id']}: bypass/manual power apparatus must default PROHIBITED")

# The inventory may name the existence of jumpers, but must not encode exact procedure-level wire/terminal pairs.
text = INV.read_text(encoding="utf-8").lower()
if re.search(r"\b(?:провод|пр\.?|клемм\w*)\s*\d+\s*(?:-|–|—|/|на)\s*\d+\b", text):
    fail("procedural wire/terminal addressing leaked into Stage1 inventory")
if "порядок сборки:" in text or "поставить перемычку между" in text:
    fail("stepwise emergency procedure leaked into Stage1 inventory")

# Variant isolation is intentionally strict: direct legacy ChME3 field candidates do not auto-inherit.
for row in candidates:
    topic = (row.get("topic") or "").lower()
    if any(token in topic for token in ("реостат", "эдт", "электродинамич")):
        if set(row.get("profiles") or []) != {"chme3t-rheostatic"}:
            fail(f"{row['id']}: ChME3T EDB content leaked to another profile")

coverage = inventory.get("profileCoverage") or []
coverage_ids = {row.get("profileId") for row in coverage}
if coverage_ids != expected_profiles:
    fail("profile coverage table incomplete")
actual_counts = Counter(profile for row in candidates for profile in row.get("profiles") or [])
for row in coverage:
    profile = row["profileId"]
    if row.get("directCandidateCount") != actual_counts.get(profile, 0):
        fail(f"{profile}: coverage count mismatch")

if actual_counts != Counter({"chme3-base": 11, "tem2-base": 7, "tem2u-improved": 1}):
    fail(f"candidate profile counts drifted: {dict(actual_counts)}")

# Canonical profile definitions must still agree with the inventory.
manifest_profiles = {row.get("profileId") for row in chme_manifest.get("profiles") or []}
if manifest_profiles != {"chme3-base", "chme3t-rheostatic", "chme3e-electronic"}:
    fail("ChME3 canonical profile manifest drifted")
exclusions = tem2_profile.get("explicitAdjacentVariantExclusions") or {}
for required in ("TEM2UM", "TEM2T", "TEM2A"):
    if required not in exclusions:
        fail(f"TEM2 profile contract lost adjacent exclusion {required}")
if (tem2_profile.get("integrationDefaults") or {}).get("failClosedOnUnknownExecution") is not True:
    fail("TEM2 fail-closed profile contract drifted")

# Every profile must have a declared research gap or direct evidence path.
gaps = inventory.get("researchGaps") or []
ids(gaps, "Stage1 research gaps")
gap_profiles = {profile for gap in gaps for profile in gap.get("profiles") or []}
if gap_profiles != expected_profiles:
    fail(f"research gaps do not cover all profiles: {sorted(gap_profiles)}")

print(
    "EXTENDED EMERGENCY STAGE 1 PASS: "
    "19 candidates; ChME3=11, ChME3T=0 direct, ChME3E=0 direct, TEM2=7, TEM2U=1; "
    "0 conditional/executable actions; all current authority=false; source refs resolved; profile isolation OK"
)
