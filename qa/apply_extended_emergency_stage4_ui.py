#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ROOT / "app/src/main/java/ru/railbrake/calculator/ui/ErmakDiagnosticsScreen.kt",
    ROOT / "patch/app/src/main/java/ru/railbrake/calculator/ui/ErmakDiagnosticsScreen.kt",
]

MARKER = '''                if (scenario.prohibited.isNotEmpty()) {
                    InfoCard("Запрещено", scenario.prohibited, MaterialTheme.colorScheme.errorContainer)
                }
                val savedReport = buildErmakDiagnosticReport(
'''

REPLACEMENT = '''                if (scenario.prohibited.isNotEmpty()) {
                    InfoCard("Запрещено", scenario.prohibited, MaterialTheme.colorScheme.errorContainer)
                }
                if (node?.type == "terminal" && family.isChme3) {
                    ExtendedEmergencyEvidenceSection(
                        standardScenarioId = scenario.id,
                        family = family
                    )
                }
                val savedReport = buildErmakDiagnosticReport(
'''


def main():
    changed = 0
    for path in FILES:
        if not path.is_file():
            raise SystemExit(f"missing {path.relative_to(ROOT)}")
        text = path.read_text(encoding="utf-8")
        if REPLACEMENT in text:
            continue
        count = text.count(MARKER)
        if count != 1:
            raise SystemExit(f"expected one Stage 4 insertion marker in {path.relative_to(ROOT)}, got {count}")
        path.write_text(text.replace(MARKER, REPLACEMENT), encoding="utf-8")
        changed += 1
    print(f"Stage 4 UI integration applied to {changed} file(s); already integrated files were left unchanged")


if __name__ == "__main__":
    main()
