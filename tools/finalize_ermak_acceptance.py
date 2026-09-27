#!/usr/bin/env python3
"""Apply only defects confirmed by the final 136-scenario acceptance audit."""
from __future__ import annotations

import gzip
import json
from copy import deepcopy
from pathlib import Path

APP = Path("app/src/main/assets/technical/ermak_diagnostics.json.gz")
PATCH = Path("patch/app/src/main/assets/technical/ermak_diagnostics.json.gz")
AUDIT_DATE = "2026-09-26"


def load(path: Path) -> dict:
    with gzip.open(path, "rt", encoding="utf-8") as source:
        return json.load(source)


def dump(path: Path, data: dict) -> None:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with path.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as target:
            target.write(payload)


def version(label: str, revision: str, status: str = "REQUIRES_REVIEW") -> dict:
    return {
        "label": label,
        "revision": revision,
        "verifiedAt": AUDIT_DATE,
        "status": status,
    }


COMMON_SOURCES = {
    "ER-SRC-002": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ1. Электрические схемы", "https://rcit.su/techinfoV51.html", "MANUFACTURER", "ИДМБ.661142.009РЭ1"),
    "ER-SRC-003": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ2. Компоновка, монтаж и вентиляция", "https://rcit.su/techinfoV52.html", "MANUFACTURER", "ИДМБ.661142.009РЭ2"),
    "ER-SRC-004": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ3. Электрические машины", "https://rcit.su/techinfoV53.html", "MANUFACTURER", "ИДМБ.661142.009РЭ3"),
    "ER-SRC-005": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ4. Электрические аппараты и оборудование", "https://rcit.su/techinfoV54.html", "MANUFACTURER", "ИДМБ.661142.009РЭ4"),
    "ER-SRC-006": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ5. Электронное оборудование и преобразователи", "https://rcit.su/techinfoV55.html", "MANUFACTURER", "ИДМБ.661142.009РЭ5"),
    "ER-SRC-007": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ6. Механическая и пневматическая часть", "https://rcit.su/techinfoV56.html", "MANUFACTURER", "ИДМБ.661142.009РЭ6"),
    "ER-SRC-008": ("2ЭС5К/3ЭС5К. ИДМБ.661142.009РЭ8. Техническое обслуживание и текущий ремонт", "https://rcit.su/techinfoV58.html", "MANUFACTURER", "ИДМБ.661142.009РЭ8"),
    "ER-SRC-013": ("Описание пневматической схемы 3ЭС5К", "https://rcit.su/techinfoV8.html", "TRAINING", "RCIT: пневматическая схема 3ЭС5К"),
    "ER-SRC-019": ("ТМХ: «Ермак» с поосным регулированием", "https://tmholding.ru/", "MANUFACTURER", "ТМХ, 19.07.2019"),
    "ER-SRC-022": ("МТЗ ТРАНСМАШ: УКТОЛ / кран №130", "https://www.mtz-transmash.ru/files/presscentr/publikacii/2010/UKTOL.pdf", "MANUFACTURER", "УКТОЛ"),
    "ER-SRC-029": ("АО «Локомотивные электронные системы»: МСУД-015", "https://zaoles.ru/catalog/msud-015/", "MANUFACTURER", "МСУД-015"),
    "ER-SRC-031": ("НПО САУТ: каталог комплексов безопасности", "https://saut.ru/", "MANUFACTURER", "каталог НПО САУТ"),
    "ER-SRC-032": ("ОАО «НЗВА»: ВОВ-25А-10/400", "https://nzva.narod.ru/VOV.htm", "MANUFACTURER", "ВОВ-25А-10/400"),
    "ER-SRC-033": ("Чирчикский трансформаторный завод: ОНДЦЭ-4350/25П-У2", "https://chtz.uz/product/single/transformatory-tiagovye-dlia-elektrovozov/transformator-tiagovyi-ondtse-435025p-u2", "MANUFACTURER", "ОНДЦЭ-4350/25П-У2"),
}


def normalize_date(value):
    if isinstance(value, dict):
        return {key: normalize_date(item) for key, item in value.items()}
    if isinstance(value, list):
        return [normalize_date(item) for item in value]
    if isinstance(value, str):
        return value.replace("2026-09-27", AUDIT_DATE).replace("27.09.2026", "26.09.2026")
    return value


def enrich_reference(ref: dict) -> None:
    source_id = ref.get("sourceId", "")
    if source_id == "ER-SRC-034":
        # ER-SRC-034 means VIP-4000M in the equipment registry. Reusing it for
        # regulation 2580/r made provenance ambiguous across technical assets.
        ref.update({
            "sourceId": "ER-AUDIT-NORM-2580R-2025",
            "document": "Регламент взаимодействия работников при аварийных и нестандартных ситуациях, распоряжение ОАО «РЖД» №2580/р от 12.12.2017",
            "locator": ref.get("locator") or "редакция №387/р от 18.02.2025; применимый раздел аварийного порядка",
            "role": ref.get("role") or "current_emergency_regulation",
            "sourceKind": "NORMATIVE",
            "version": version("№2580/р", "№387/р от 18.02.2025", "CURRENT_CONFIRMED"),
        })
        return

    if source_id == "ER-SRC-010":
        ref["role"] = "legacy_symptom_crosscheck_only"
        ref["sourceKind"] = "TRAINING"
        ref["version"] = version(
            "№671р от 31.03.2010",
            "заменён №996/р; только историческая/симптомная сверка",
            "HISTORICAL",
        )
        return

    if source_id == "ER-SRC-011":
        ref.setdefault("document", "Практические рекомендации по неисправностям 2ЭС5К/3ЭС5К раннего исполнения")
        ref.setdefault("locator", "симптомы МСУД-Н/рекуперации; точная редакция памятки не подтверждена")
        ref.setdefault("sourceKind", "TRAINING")
        ref["version"] = version("памятка раннего исполнения", "не является действующим нормативным разрешением", "HISTORICAL")
        return

    if source_id == "ER-SRC-027":
        ref.setdefault("document", "Профильный диагностический материал ER-SRC-027")
        ref.setdefault("locator", "точное происхождение и редакция не подтверждены; не использовать для разрешения действий")
        ref.setdefault("sourceKind", "UNKNOWN")
        ref["version"] = version("ER-SRC-027", "unresolved profile provenance", "REQUIRES_REVIEW")
        return

    if source_id in COMMON_SOURCES:
        document, url, kind, label = COMMON_SOURCES[source_id]
        ref.setdefault("document", document)
        ref.setdefault("locator", "применимый раздел по узлу сценария; лист/пункт подтверждать по исполнению")
        ref.setdefault("url", url)
        ref.setdefault("sourceKind", kind)
        current = ref.setdefault("version", {})
        current.setdefault("label", label)
        current.setdefault("revision", "публичная копия; редакцию и применимость подтверждать по исполнению")
        current.setdefault("verifiedAt", AUDIT_DATE)
        current.setdefault("status", "REQUIRES_REVIEW")

    # Complete provenance conservatively. Unknown data stays explicitly
    # unresolved instead of being promoted to a current action source.
    ref.setdefault("document", f"Источник {source_id or 'без идентификатора'}")
    ref.setdefault("locator", "точный раздел/редакция требуют дополнительной сверки")
    ref.setdefault("sourceKind", "UNKNOWN")
    current = ref.setdefault("version", {})
    current.setdefault("label", ref["document"])
    current.setdefault("revision", "точная редакция не подтверждена")
    current.setdefault("verifiedAt", AUDIT_DATE)
    current.setdefault("status", "REQUIRES_REVIEW")


def main() -> None:
    data = normalize_date(load(APP))
    mirror = normalize_date(load(PATCH))
    if data != mirror:
        raise SystemExit("app/patch diagnostics differ before final acceptance fixes")
    scenarios = data.get("scenarios", [])
    if len(scenarios) != 136:
        raise SystemExit(f"expected 136 scenarios, got {len(scenarios)}")
    by_id = {scenario["id"]: scenario for scenario in scenarios}

    # Remove the only reachable graph cycle found by the independent DFS.
    common = next(node for node in by_id["ER-DIAG-103"]["graph"]["nodes"] if node["id"] == "common")
    next(choice for choice in common["choices"] if choice["label"] == "Нет")["nextNodeId"] = "reassess"
    next(choice for choice in common["choices"] if choice["label"] == "Нет")["label"] = "Нет, отказ только одного режима"

    # This node is a prohibition/instruction, not an action permission. Keeping
    # it informational prevents an unrecognised pseudo-policy from bypassing the
    # runtime action-policy contract.
    safety_stop = next(node for node in by_id["ER-DIAG-092"]["graph"]["nodes"] if node["id"] == "safety-stop")
    safety_stop["type"] = "info"
    safety_stop.pop("riskClass", None)
    safety_stop.pop("userFacingPolicy", None)
    safety_stop.pop("sourceBound", None)

    for scenario in scenarios:
        for ref in scenario.get("sourceRefs", []):
            enrich_reference(ref)
        scenario.setdefault("sourceAudit", {
            "auditedAt": AUDIT_DATE,
            "scope": "final_acceptance_136",
            "status": "KEEP",
            "method": "graph, safety, profile and source-provenance acceptance review",
        })
        for node in scenario.get("graph", {}).get("nodes", []):
            if node.get("type") == "emergency_action":
                node["sourceBound"] = True
                node.setdefault("riskClass", "emergency")
                node["userFacingPolicy"] = "EMERGENCY_SOURCE_BOUND"

    dump(APP, data)
    dump(PATCH, deepcopy(data))
    if APP.read_bytes() != PATCH.read_bytes():
        raise SystemExit("app/patch diagnostics differ after final acceptance fixes")
    print("ERMAK_FINAL_ACCEPTANCE_FIXES_APPLIED=graph-cycle,runtime-policy,provenance")


if __name__ == "__main__":
    main()
