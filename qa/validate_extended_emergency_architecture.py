#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
PATCH = ROOT / "patch/app"


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_ARCH_FAIL: {message}")


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def main():
    policy_app = APP / "src/main/assets/technical/extended_emergency_policy.json"
    policy_patch = PATCH / "src/main/assets/technical/extended_emergency_policy.json"
    if policy_app.read_bytes() != policy_patch.read_bytes():
        fail("runtime policy app/patch mirror mismatch")
    policy = json.loads(read(policy_app))

    if policy.get("modeId") != "EXTENDED_EMERGENCY_KNOWLEDGE":
        fail("wrong modeId")
    if policy.get("defaultEnabled") is not False:
        fail("expanded mode must default OFF")
    warning = policy.get("warningPolicy") or {}
    if warning.get("acknowledgement") != "FIRST_ENABLE_ONLY":
        fail("first-enable acknowledgement contract missing")
    if warning.get("repeatPerScenario") is not False:
        fail("general warning must not repeat per scenario")
    if warning.get("badge") != "Расширенный сценарий":
        fail("expanded badge drifted")
    if "бирюз" not in warning.get("warningTextMustNameColor", "").lower():
        fail("warning must explicitly name turquoise frame")

    visual = policy.get("visualSemantics") or {}
    if visual.get("themeIndependent") is not True:
        fail("expanded semantic color must be independent from app accent palette")
    if visual.get("lightBorder") != "#0F766E" or visual.get("darkBorder") != "#5EEAD4":
        fail("fixed turquoise tokens drifted")
    if visual.get("colorNeverSoleCarrier") is not True:
        fail("color cannot be the sole status carrier")
    if visual.get("dangerInsideScenarioRemainsRed") is not True:
        fail("danger semantics must stay independently red")

    required_classes = {"ARCHIVED_OFFICIAL", "MANUFACTURER_EXTENDED", "HISTORICAL_TRAINING", "FIELD_PRACTICE"}
    actual_classes = {row.get("id") for row in policy.get("provenanceClasses") or []}
    if actual_classes != required_classes:
        fail(f"provenance classes mismatch: {actual_classes}")

    runtime = policy.get("runtimeContract") or {}
    if runtime.get("standardModeMayEnterExtendedBranch") is not False:
        fail("standard mode must never enter expanded branch")
    if runtime.get("extendedModeMayUpgradeAuthority") is not False:
        fail("expanded mode must never upgrade source authority")
    if runtime.get("profileApplicabilityRequired") is not True:
        fail("profile applicability gate missing")
    if runtime.get("unknownExecution") != "FAIL_CLOSED":
        fail("unknown execution must remain fail-closed")
    if runtime.get("sourceRequired") is not True or runtime.get("sourceStatusRequired") is not True:
        fail("source/provenance metadata required")

    mirror_paths = [
        "src/main/java/ru/railbrake/calculator/data/ExtendedEmergencyModeRepository.kt",
        "src/main/java/ru/railbrake/calculator/ui/ExtendedEmergencySettingsSection.kt",
        "src/main/java/ru/railbrake/calculator/ui/theme/Color.kt",
        "src/main/java/ru/railbrake/calculator/ui/theme/Theme.kt",
        "src/main/java/ru/railbrake/calculator/ui/BrakeCalculatorApp.kt",
    ]
    for rel in mirror_paths:
        a, b = APP / rel, PATCH / rel
        if a.read_bytes() != b.read_bytes():
            fail(f"app/patch mirror mismatch: {rel}")

    repository = read(APP / mirror_paths[0])
    for token in ("EXTENDED_EMERGENCY_KNOWLEDGE", "warning_acknowledged_v1", "Расширенный сценарий"):
        if token not in repository:
            fail(f"repository contract missing {token}")

    settings = read(APP / mirror_paths[1])
    for token in (
        "Расширенные аварийные приёмы",
        "бирюзовой рамкой",
        "Расширенный сценарий",
        "не означает, что описанное действие разрешено",
        "не будет повторяться при открытии каждого расширенного сценария",
    ):
        if token not in settings:
            fail(f"settings warning copy missing: {token}")

    colors = read(APP / mirror_paths[2])
    for token in ("ExtendedEmergencyLight = Color(0xFF0F766E)", "ExtendedEmergencyDark = Color(0xFF5EEAD4)"):
        if token not in colors:
            fail(f"semantic color missing: {token}")

    theme = read(APP / mirror_paths[3])
    for token in ("extendedEmergency", "extendedEmergencyContainer", "extendedEmergencyBorder"):
        if token not in theme:
            fail(f"theme semantic token missing: {token}")

    app_ui = read(APP / mirror_paths[4])
    if 'SETTINGS("Настройки")' not in app_ui or 'Text("Настройки")' not in app_ui:
        fail("Settings user-facing rename missing")
    if "ExtendedEmergencySettingsSection()" not in app_ui:
        fail("expanded settings section not wired into Settings")
    if "Палитра" in app_ui:
        fail("stale user-facing Палитра remains")

    print("EXTENDED_EMERGENCY_ARCH_PASS")
    print("mode=OFF_BY_DEFAULT warning=FIRST_ENABLE_ONLY repeat_per_scenario=false")
    print("visual=FIXED_TURQUOISE badge=Расширенный_сценарий danger=SEPARATE_RED")
    print("authority=NOT_UPGRADED profile=REQUIRED unknown_execution=FAIL_CLOSED")


if __name__ == "__main__":
    main()
