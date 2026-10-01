#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GLOBAL = ROOT / "docs" / "locomotives" / "manifests" / "locomotive_families.json"
FAMILY = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family" / "family_manifest.json"
PROFILE = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family" / "chme3e" / "profile.json"
SOURCES = ROOT / "docs" / "locomotives" / "diesel" / "chme3-family" / "chme3e" / "source_registry.json"


def load(path: Path):
    if not path.exists():
        raise AssertionError(f"missing file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    global_manifest = load(GLOBAL)
    family = load(FAMILY)
    profile = load(PROFILE)
    source_registry = load(SOURCES)

    families = {row["familyId"]: row for row in global_manifest.get("families", [])}
    if "chme3-family" not in families:
        raise AssertionError("chme3-family missing from global locomotive manifest")
    if families["chme3-family"].get("tractionType") != "diesel":
        raise AssertionError("chme3-family must be classified as diesel")
    if "chme3e-electronic" not in families["chme3-family"].get("profiles", []):
        raise AssertionError("CHME3E profile missing from global manifest")

    family_profiles = {row["profileId"]: row for row in family.get("profiles", [])}
    family_entry = family_profiles.get("chme3e-electronic")
    if not family_entry:
        raise AssertionError("CHME3E missing from family manifest")

    # Stage completion is monotonic. Once the manifest exposes completedStages,
    # later support-state names must not make an earlier completed gate fail.
    completed_stages = set(family_entry.get("completedStages", []))
    if completed_stages:
        if "FOUNDATION" not in completed_stages:
            raise AssertionError(
                f"CHME3E family manifest lost completed FOUNDATION stage: {sorted(completed_stages)}"
            )
    else:
        accepted_legacy_support_states = {
            "STAGE_1_FOUNDATION_COMPLETE",
            "STAGE_2_ATLAS_SCHEMES_COMPLETE",
            "STAGE_2_ATLAS_SCHEMES_INTERACTIVE_COMPLETE",
            "STAGE_3_DIAGNOSTICS_COMPLETE",
            "KNOWLEDGE_FOUNDATION_COMPLETE",
        }
        if family_entry.get("supportState") not in accepted_legacy_support_states:
            raise AssertionError(
                "CHME3E family-manifest state does not include completed stage 1: "
                f"{family_entry.get('supportState')}"
            )

    if profile.get("profileId") != "chme3e-electronic" or profile.get("series") != "ЧМЭ3Э":
        raise AssertionError("bad CHME3E profile identity")
    if profile.get("tractionType") != "diesel":
        raise AssertionError("CHME3E must be diesel")

    traits = set(profile.get("variantTraits", []))
    required_traits = {"additional_electronic_control", "diesel_preheating_device", "no_factory_rheostatic_brake"}
    if not required_traits.issubset(traits):
        raise AssertionError(f"missing CHME3E variant traits: {sorted(required_traits - traits)}")

    exclusions = " ".join(profile.get("explicitExclusions", [])).lower()
    for needle in ("rheostatic", "braking resistor", "edb"):
        if needle not in exclusions:
            raise AssertionError(f"CHME3T-only exclusion missing: {needle}")

    rules = profile.get("profileRules", {})
    if rules.get("inheritChme3tElectronicContent") != "NO_AUTOMATIC_INHERITANCE":
        raise AssertionError("CHME3E must not automatically inherit CHME3T electronics")
    if "excluded" not in str(rules.get("edbPolicy", "")).lower():
        raise AssertionError("CHME3E EDB policy must fail closed")

    sources = source_registry.get("sources", [])
    source_ids = [row["id"] for row in sources]
    if len(source_ids) != len(set(source_ids)):
        raise AssertionError("duplicate CHME3E source IDs")
    source_id_set = set(source_ids)
    for ref in profile.get("evidenceRefs", []):
        if ref not in source_id_set:
            raise AssertionError(f"profile evidence ref missing from CHME3E registry: {ref}")

    ch17 = next((row for row in sources if row["id"] == "CHME3E-SRC-NOTIK-CH17"), None)
    if not ch17 or ch17.get("applicability") != ["ЧМЭ3Э"]:
        raise AssertionError("dedicated CHME3E electrical-scheme evidence is missing")

    for row in sources:
        if row.get("authorityForAction") not in {"NO", "NO_BY_ITSELF"}:
            raise AssertionError(f"stage-1 reference source grants action authority: {row['id']}")

    gaps = source_registry.get("researchGaps", [])
    if len(gaps) < 3:
        raise AssertionError("research gaps must remain explicit before stage 2/3")

    print(
        "CHME3E STAGE 1 OK: canonical family manifest, dedicated profile, "
        f"{len(sources)} evidence records, explicit no-EDB isolation, {len(gaps)} research gaps"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, KeyError, TypeError) as exc:
        print(f"CHME3E STAGE 1 FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
