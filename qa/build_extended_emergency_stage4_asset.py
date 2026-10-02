#!/usr/bin/env python3
import argparse
import csv
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGE3_BUILDER = ROOT / "qa/build_extended_emergency_stage3_projection.py"
SOURCES = ROOT / "docs/integration/extended_emergency_stage2_sources.tsv"
DEFAULT_APP = ROOT / "app/src/main/assets/technical/extended_emergency_runtime.json"
DEFAULT_PATCH = ROOT / "patch/app/src/main/assets/technical/extended_emergency_runtime.json"

PROVENANCE_LABELS = {
    "OFFICIAL_FRAMEWORK": "Действующая рамка безопасности",
    "ARCHIVED_OFFICIAL": "Архивный официальный материал",
    "MANUFACTURER_EXTENDED": "Документация изготовителя",
    "HISTORICAL_TRAINING": "Исторический учебный материал",
    "FIELD_PRACTICE": "Полевая практика",
}

STATUS_LABELS = {
    "CURRENT_FRAMEWORK_CONFIRMED": "Действующая рамка безопасности; не разрешение конкретного действия",
    "SECONDARY_REQUIRES_PRIMARY_ITEM_CHECK": "Вторичный источник; требуется проверка первичного пункта",
    "HISTORICAL_REFERENCE": "Историческая справка; текущая применимость не подтверждена",
    "FIELD_PRACTICE_UNVERIFIED": "Полевая практика; не проверено как действующий порядок",
    "FIELD_PRACTICE": "Полевая практика; без автоматического нормативного статуса",
    "TRAINING_REFERENCE": "Учебный или справочный материал",
    "PROFILE_SPECIFIC_TECHNICAL_REFERENCE": "Профильный технический материал",
    "PROVENANCE_CONFLICT": "Происхождение источника конфликтно или неясно",
    "HISTORICAL_FACTORY_REFERENCE": "Историческая заводская документация",
    "MANUFACTURER_CONFIRMED_HISTORICAL": "Подтверждённая историческая документация изготовителя",
    "OFFICIAL_REPAIR_REFERENCE": "Ремонтная инструкция; не автоматическое действие для локомотивной бригады",
    "UNVERIFIED_COPY": "Непроверенная копия; только исследовательское свидетельство",
    "REPOST_UNVERIFIED": "Непроверенная перепубликация; только исследовательское свидетельство",
    "DISCOVERY_ONLY_MIRROR": "Поисковая копия; не самостоятельное подтверждение",
    "DISCOVERY_ONLY": "Поисковый источник; не самостоятельное подтверждение",
    "EXECUTION_SPECIFIC_REFERENCE": "Материал для конкретного исполнения; требуется подтверждение оборудования",
}

DISPOSITION_LABELS = {
    "INFORMATION_ONLY": "Дополнительный информационный материал",
    "PROHIBITED": "Процедура намеренно не показывается",
}


def fail(message: str):
    raise SystemExit(f"EXTENDED_EMERGENCY_STAGE4_BUILD_FAIL: {message}")


def read_tsv(path: Path):
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def load_stage3_builder():
    spec = importlib.util.spec_from_file_location("expanded_stage3", STAGE3_BUILDER)
    if spec is None or spec.loader is None:
        fail("unable to import Stage 3 builder")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def risk_label(value: str) -> str:
    upper = value.strip().upper()
    if any(token in upper for token in ("EMERGENCY", "HIGH", "SAFETY_CRITICAL", "BRAKE_SAFETY")):
        return "Повышенный риск — выполнять только штатную безопасную диагностику"
    if any(token in upper for token in ("ELEVATED", "THERMAL", "ELECTRICAL", "MECHANICAL", "TRACTION")):
        return "Требует осторожной локализации и соблюдения штатных ограничений"
    return "Справочная диагностическая информация"


def first_text(route: dict, node_type: str) -> str:
    for node in (route.get("runtimeGraph") or {}).get("nodes") or []:
        if node.get("type") == node_type and str(node.get("text") or "").strip():
            return str(node["text"]).strip()
    return ""


def terminal_text(route: dict) -> str:
    nodes = (route.get("runtimeGraph") or {}).get("nodes") or []
    for node in nodes:
        if node.get("id") == "terminal" and node.get("type") == "terminal":
            return str(node.get("text") or "").strip()
    return first_text(route, "terminal")


def build_asset() -> dict:
    stage3 = load_stage3_builder().build_projection()
    source_rows = read_tsv(SOURCES)
    sources = {row["id"].strip(): row for row in source_rows}

    records = []
    for route in stage3.get("candidateRoutes") or []:
        execution = route.get("execution") or {}
        visibility = route.get("visibility") or {}
        disposition = str(execution.get("actionDisposition") or "").strip()
        if disposition not in DISPOSITION_LABELS:
            fail(f"unsupported disposition {disposition!r}")
        if execution.get("executable") is not False or execution.get("procedureVisible") is not False:
            fail(f"{route.get('candidateId')}: unsafe Stage 3 execution flags")
        if execution.get("currentAuthorityVerified") is not False or execution.get("canBecomeExecutableAtRuntime") is not False:
            fail(f"{route.get('candidateId')}: runtime authority must remain false")
        if visibility.get("profileMatch") != "EXACT_ONLY" or visibility.get("adjacentVariantInheritance") != "DENY":
            fail(f"{route.get('candidateId')}: exact-profile boundary missing")

        presentations = []
        for source_ref in route.get("sourceRefs") or []:
            source = sources.get(source_ref)
            if source is None:
                fail(f"{route.get('candidateId')}: unresolved source {source_ref}")
            provenance = source.get("provenanceClass", "").strip()
            status = source.get("sourceStatus", "").strip()
            if provenance not in PROVENANCE_LABELS:
                fail(f"{source_ref}: no human provenance label for {provenance}")
            if status not in STATUS_LABELS:
                fail(f"{source_ref}: no human source-status label for {status}")
            presentations.append({
                "sourceRef": source_ref,
                "title": source.get("title", "").strip(),
                "url": source.get("url", "").strip(),
                "provenanceLabel": PROVENANCE_LABELS[provenance],
                "statusLabel": STATUS_LABELS[status],
            })

        summary = first_text(route, "notice")
        terminal = terminal_text(route)
        if not summary or not terminal:
            fail(f"{route.get('candidateId')}: safe presentation text missing")

        records.append({
            "candidateId": route.get("candidateId"),
            "standardScenarioId": route.get("standardScenarioId"),
            "profiles": route.get("profiles") or [],
            "actionDisposition": disposition,
            "dispositionLabel": DISPOSITION_LABELS[disposition],
            "riskLabel": risk_label(str(route.get("riskClass") or "")),
            "summary": summary,
            "terminal": terminal,
            "sources": presentations,
            "runtimeSafety": {
                "exactProfileOnly": True,
                "executable": False,
                "procedureVisible": False,
                "currentAuthorityVerified": False,
                "runtimeAuthorityUpgradeAllowed": False,
            },
        })

    routing = []
    for item in stage3.get("scenarioRouting") or []:
        routing.append({
            "standardScenarioId": item.get("standardScenarioId"),
            "discoveryStatus": item.get("discoveryStatus"),
            "hasExpandedEvidence": bool(item.get("extendedCandidateIds")),
            "noAdditionalReason": item.get("noAdditionalReason"),
        })

    return {
        "schemaVersion": 1,
        "modeId": stage3.get("modeId"),
        "generatedFrom": {
            "stage3ProjectionBuilder": "qa/build_extended_emergency_stage3_projection.py",
            "stage2Sources": "docs/integration/extended_emergency_stage2_sources.tsv",
        },
        "policy": {
            "defaultEnabled": False,
            "standardDiagnosticsRemainCanonical": True,
            "attachOnlyAfterStandardScenarioMatch": True,
            "exactProfileOnly": True,
            "unknownExecution": "FAIL_CLOSED",
            "adjacentVariantInheritance": "DENY",
            "expandedEvidenceExecutable": False,
            "procedureLevelHazardousDetailVisible": False,
            "rawInternalIdsVisibleToUser": False,
        },
        "statistics": stage3.get("statistics") or {},
        "scenarioRouting": routing,
        "records": records,
    }


def write_asset(payload: dict, app_output: Path, patch_output: Path):
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    for path in (app_output, patch_output):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-output", type=Path, default=DEFAULT_APP)
    parser.add_argument("--patch-output", type=Path, default=DEFAULT_PATCH)
    args = parser.parse_args()
    payload = build_asset()
    write_asset(payload, args.app_output, args.patch_output)
    print(
        "EXTENDED EMERGENCY STAGE 4 ASSET BUILT: "
        f"{len(payload['scenarioRouting'])} scenario decisions / {len(payload['records'])} safe display records"
    )


if __name__ == "__main__":
    main()
