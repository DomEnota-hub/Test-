#!/usr/bin/env python3
import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTEGRATION = ROOT / "docs/integration"
CANDIDATES = INTEGRATION / "extended_emergency_stage2_candidates.tsv"
DISCOVERY = INTEGRATION / "extended_emergency_stage2_discovery.tsv"

LIGHT_BORDER = "#0F766E"
DARK_BORDER = "#5EEAD4"
MODE_ID = "EXTENDED_EMERGENCY_KNOWLEDGE"
ALL_PROFILES = [
    "chme3-base",
    "chme3t-rheostatic",
    "chme3e-electronic",
    "tem2-base",
    "tem2u-improved",
]


def read_tsv(path: Path):
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def split_pipe(value: str):
    return [item.strip() for item in (value or "").split("|") if item.strip()]


def route_text(row):
    if row["actionDisposition"] == "PROHIBITED":
        return {
            "summary": "В источниках найден нестандартный аварийный приём, связанный с этой неисправностью. Процедура намеренно не отображается: текущая допустимость и профильная применимость действия не подтверждены.",
            "terminal": "Использовать запись только как сведения о существовании исторического/полевого метода. Не превращать её в инструкцию и продолжать безопасную локализацию по стандартному диагностическому графу.",
        }
    return {
        "summary": "Для этой неисправности найден дополнительный исторический, заводской или полевой диагностический материал. Он может расширять гипотезы и локализацию, но не является подтверждённой командой к действию для текущего исполнения.",
        "terminal": "Сопоставить источник, фактический профиль и результаты стандартного диагностического графа. Выполняемые действия не выводятся из этой расширенной записи без отдельного подтверждения authority и safety-gate.",
    }


def build_projection():
    candidates = read_tsv(CANDIDATES)
    discovery = read_tsv(DISCOVERY)
    by_scenario = defaultdict(list)
    candidate_routes = []

    for row in candidates:
        cid = row["candidateId"].strip()
        sid = row["standardScenarioId"].strip()
        profiles = split_pipe(row.get("profiles"))
        source_refs = split_pipe(row.get("sourceRefs"))
        disposition = row["actionDisposition"].strip()
        text = route_text(row)
        by_scenario[sid].append(cid)

        if disposition == "PROHIBITED":
            nodes = [
                {
                    "id": "gate",
                    "type": "policy_gate",
                    "condition": "extendedEmergencyMode == true AND exactProfileMatch == true",
                    "onPass": "status",
                    "onFail": "hidden",
                },
                {
                    "id": "status",
                    "type": "notice",
                    "semantic": "EXTENDED_PROHIBITED",
                    "text": text["summary"],
                    "nextNodeId": "terminal",
                },
                {
                    "id": "terminal",
                    "type": "terminal",
                    "text": text["terminal"],
                },
                {
                    "id": "hidden",
                    "type": "terminal",
                    "text": "Расширенная ветвь скрыта: режим выключен либо фактический профиль не совпадает.",
                },
            ]
        else:
            nodes = [
                {
                    "id": "gate",
                    "type": "policy_gate",
                    "condition": "extendedEmergencyMode == true AND exactProfileMatch == true",
                    "onPass": "context",
                    "onFail": "hidden",
                },
                {
                    "id": "context",
                    "type": "notice",
                    "semantic": "EXTENDED_INFORMATION_ONLY",
                    "text": text["summary"],
                    "nextNodeId": "evidence",
                },
                {
                    "id": "evidence",
                    "type": "finding",
                    "text": "Расширенная запись дополняет стандартный маршрут сведениями о возможной локализации или историческом способе. Источник и его статус должны оставаться видимыми пользователю.",
                    "nextNodeId": "terminal",
                },
                {
                    "id": "terminal",
                    "type": "terminal",
                    "text": text["terminal"],
                },
                {
                    "id": "hidden",
                    "type": "terminal",
                    "text": "Расширенная ветвь скрыта: режим выключен либо фактический профиль не совпадает.",
                },
            ]

        candidate_routes.append(
            {
                "routeId": f"EXT3-{cid}",
                "candidateId": cid,
                "standardScenarioId": sid,
                "profiles": profiles,
                "methodClass": row["methodClass"].strip(),
                "provenanceClass": row["provenanceClass"].strip(),
                "sourceStatus": row["sourceStatus"].strip(),
                "riskClass": row["riskClass"].strip(),
                "sourceRefs": source_refs,
                "visibility": {
                    "modeId": MODE_ID,
                    "requiresModeEnabled": True,
                    "profileMatch": "EXACT_ONLY",
                    "adjacentVariantInheritance": "DENY",
                },
                "presentation": {
                    "badge": "Расширенный сценарий",
                    "frameSemantic": "EXPANDED_NON_STANDARD_PROVENANCE",
                    "lightBorder": LIGHT_BORDER,
                    "darkBorder": DARK_BORDER,
                    "dangerSemanticRemainsIndependentRed": True,
                },
                "execution": {
                    "actionDisposition": disposition,
                    "currentAuthorityVerified": False,
                    "executable": False,
                    "procedureVisible": False,
                    "canBecomeExecutableAtRuntime": False,
                    "standardGraphRemainsActionBoundary": True,
                },
                "runtimeGraph": {
                    "schemaVersion": 2,
                    "startNodeId": "gate",
                    "nodes": nodes,
                },
            }
        )

    scenario_routing = []
    for row in discovery:
        sid = row["standardScenarioId"].strip()
        status = row["discoveryStatus"].strip()
        attached = sorted(by_scenario.get(sid, []))
        scenario_routing.append(
            {
                "standardScenarioId": sid,
                "dataset": row["dataset"].strip(),
                "discoveryStatus": status,
                "extendedCandidateIds": attached,
                "standardRouteRequired": True,
                "whenModeOff": "STANDARD_ONLY",
                "whenModeOn": (
                    "STANDARD_PLUS_EXTENDED_EVIDENCE"
                    if attached
                    else "STANDARD_ONLY_NO_EXTENDED_METHOD"
                ),
                "noAdditionalReason": row.get("noAdditionalReason", "").strip() or None,
            }
        )

    dispositions = Counter(row["actionDisposition"].strip() for row in candidates)
    profiles = Counter(profile for row in candidates for profile in split_pipe(row.get("profiles")))
    profile_counts = {profile: profiles[profile] for profile in ALL_PROFILES}
    discovery_status = Counter(row["discoveryStatus"].strip() for row in discovery)

    return {
        "schemaVersion": 2,
        "modeId": MODE_ID,
        "stage": "STAGE_3_RUNTIME_V2_ASSISTANT_PROJECTION",
        "generatedFrom": {
            "candidates": "docs/integration/extended_emergency_stage2_candidates.tsv",
            "discovery": "docs/integration/extended_emergency_stage2_discovery.tsv",
        },
        "policy": {
            "standardDiagnosticsRemainCanonical": True,
            "expandedModeMayReplaceStandardScenarioSelection": False,
            "troubleshootingIntentKeepsPriorityOverAtlas": True,
            "expandedEvidenceAttachesOnlyAfterStandardScenarioMatch": True,
            "profileApplicabilityRequired": True,
            "unknownExecution": "FAIL_CLOSED",
            "adjacentVariantInheritance": "DENY",
            "informationOnlyIsExecutable": False,
            "prohibitedIsExecutable": False,
            "procedureLevelHazardousDetailAllowed": False,
            "currentAuthorityRequiredBeforeConditionalAction": True,
            "repeatWarningPerScenario": False,
        },
        "statistics": {
            "standardScenarios": len(discovery),
            "foundScenarios": discovery_status["FOUND"],
            "noAdditionalScenarios": discovery_status["NO_ADDITIONAL_METHOD_FOUND"],
            "candidateRoutes": len(candidate_routes),
            "informationOnly": dispositions["INFORMATION_ONLY"],
            "prohibited": dispositions["PROHIBITED"],
            "conditionalAction": dispositions["CONDITIONAL_ACTION"],
            "profileCandidateCounts": profile_counts,
        },
        "scenarioRouting": scenario_routing,
        "candidateRoutes": candidate_routes,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    payload = build_projection()
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
