#!/usr/bin/env python3
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs/locomotives/manifests/locomotive_families.json"
APP_REGISTRY = ROOT / "app/src/main/java/ru/railbrake/calculator/core/LocomotiveProfileContext.kt"
PATCH_REGISTRY = ROOT / "patch/app/src/main/java/ru/railbrake/calculator/core/LocomotiveProfileContext.kt"


def fail(message: str):
    raise SystemExit(f"LOCOMOTIVE_PROFILE_REGISTRY_FAIL: {message}")


def manifest_profiles():
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1:
        fail("unsupported locomotive family manifest schema")
    result = set()
    for family in data.get("families") or []:
        family_id = str(family.get("familyId") or "").strip()
        if not family_id:
            fail("manifest family without familyId")
        for profile_id in family.get("profiles") or []:
            profile_id = str(profile_id).strip()
            if not profile_id:
                fail(f"{family_id}: blank profile ID")
            pair = (profile_id, family_id)
            if pair in result:
                fail(f"duplicate manifest profile mapping: {pair}")
            result.add(pair)
    if not result:
        fail("manifest contains no profiles")
    return result


def runtime_profiles(text: str):
    marker = "internal val registeredProfiles: Map<String, String> = linkedMapOf("
    if marker not in text:
        fail("runtime registeredProfiles map missing")
    body = text.split(marker, 1)[1].split("\n    )", 1)[0]
    pairs = set(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', body))
    if not pairs:
        fail("runtime registeredProfiles map is empty")
    return pairs


def main():
    for path in (MANIFEST, APP_REGISTRY, PATCH_REGISTRY):
        if not path.is_file():
            fail(f"missing {path.relative_to(ROOT)}")

    app_text = APP_REGISTRY.read_text(encoding="utf-8")
    patch_text = PATCH_REGISTRY.read_text(encoding="utf-8")
    if app_text != patch_text:
        fail("app/patch profile registry mirrors differ")

    expected = manifest_profiles()
    actual = runtime_profiles(app_text)
    if actual != expected:
        missing = sorted(expected - actual)
        stale = sorted(actual - expected)
        fail(f"manifest/runtime profile mismatch; missing={missing}, stale={stale}")

    required_contract = (
        'UNKNOWN_PROFILE_ID = "unknown"',
        "UNKNOWN_FAIL_CLOSED",
        "fun resolve(profileId: String): LocomotiveProfileContext",
        "fun resolve(profileId: String, expectedFamilyId: String): LocomotiveProfileContext",
        "fun fromTechnicalFamily(family: TechnicalFamily): LocomotiveProfileContext",
        "fun isRegisteredExact(context: LocomotiveProfileContext): Boolean",
        'TechnicalFamily.CHME3 -> resolve("chme3-base", "chme3-family")',
        'TechnicalFamily.CHME3T -> resolve("chme3t-rheostatic", "chme3-family")',
        'TechnicalFamily.CHME3E -> resolve("chme3e-electronic", "chme3-family")',
        'TechnicalFamily.VL80S -> unknown("vl80s-family")',
        'TechnicalFamily.ERMAK -> unknown("ermak-family")',
        '"tem2-base" to "tem2-family"',
        '"tem2u-improved" to "tem2-family"',
    )
    for token in required_contract:
        if token not in app_text:
            fail(f"non-null profile contract missing: {token}")

    for forbidden in ("LocomotiveProfileContext?", "String?", "return null"):
        if forbidden in app_text:
            fail(f"nullable profile resolution reintroduced: {forbidden}")

    print(
        "LOCOMOTIVE PROFILE REGISTRY PASS: "
        f"{len(actual)} manifest profiles have exact runtime mappings; "
        "unknown/unintegrated profiles resolve explicitly FAIL_CLOSED; no nullable profile fallback"
    )


if __name__ == "__main__":
    main()
