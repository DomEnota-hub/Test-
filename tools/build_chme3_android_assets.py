"""Project the reviewed ChME3 knowledge foundation into Android assets.

The source documents remain authoritative; never edit the generated assets by hand.
"""
import glob
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "docs/locomotives/chme3"
FAMILY = ROOT / "docs/locomotives/diesel/chme3-family"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def unique(items):
    return list(dict.fromkeys(items))


index = read(LEGACY / "atlas_index.json")
extension = read(LEGACY / "atlas_index_extension.json")
variants = read(FAMILY / "chme3e/atlas_variant.json")
cards = {}
for path in sorted(LEGACY.glob("atlas_cards_*.json")) + sorted((FAMILY / "chme3e").glob("atlas_cards_*.json")):
    data = read(path)
    for card in data.get("records", data.get("equipment", [])):
        cards[card["id"]] = {**cards.get(card["id"], {}), **card}

base_equipment = [{**item, **cards.get(item["id"], {})} for item in index["equipment"] + extension["equipment"]]
variant_equipment = [{**item, **cards.get(item["id"], {})} for item in variants["equipment"]]
all_scenarios = {}
for path in sorted(LEGACY.glob("diagnostics_*pass3.json")) + [FAMILY / "chme3e/diagnostics_stage3.json"]:
    for scenario in read(path).get("scenarios", []):
        all_scenarios[scenario["id"]] = scenario

graphs = {}
for path in sorted(LEGACY.glob("diagnostic_runtime_graphs_v2_batch*.json")) + [FAMILY / "chme3e/diagnostic_runtime_graphs_v2.json"]:
    for scenario in read(path)["scenarios"]:
        graphs[scenario["scenarioId"]] = scenario["runtimeGraph"]

shared_views = read(FAMILY / "common/interactive_shared_views.json")["views"]
base_views = read(FAMILY / "chme3/interactive_schemes.json")["profileViews"]
t_views = read(FAMILY / "chme3t/interactive_schemes.json")["profileViews"]
e_views = read(FAMILY / "chme3e/interactive_schemes.json")["profileViews"]
e_scheme_flows = {scheme["id"]: scheme.get("flows", []) for scheme in read(FAMILY / "chme3e/schemes_variant.json")["records"]}
required = read(LEGACY / "acceptance_required_pass4.json")["items"]
routes = read(LEGACY / "acceptance_contract_pass4.json")["routes"]
base_phases = read(LEGACY / "acceptance_contract_pass4.json")["phaseRules"]
e_phases = read(FAMILY / "chme3e/acceptance_stage4.json")["phaseRules"]
e_checks = read(FAMILY / "chme3e/acceptance_stage4.json")["profileExtendedChecks"]
e_semantics = read(FAMILY / "chme3e/assistant_semantics_stage3.json")
e_routes = {item["scenarioId"]: item for item in e_semantics["scenarioRouting"]}
e_spoken = {item["expectedScenarioId"]: item["query"] for item in e_semantics["spokenCorpus"]}


def fields(item, *names):
    return [str(value) for name in names for value in (item.get(name) if isinstance(item.get(name), list) else [item.get(name)]) if value]


def entry(item, section, title, blocks, related=(), sequence=(), hotspots=(), aliases=(), status="REFERENCE"):
    lines = [text for block in blocks for text in block[1]]
    return dict(id=item["id"], section=section, title=title, subtitle=fields(item, "purpose", "check")[0] if fields(item, "purpose", "check") else "",
                status=status, blocks=[dict(title=heading, lines=values) for heading, values in blocks if values],
                relatedIds=unique(list(related)), sequence=list(sequence), hotspots=list(hotspots),
                searchAliases=unique(list(aliases)), searchText=" ".join([title, *lines, *aliases]).lower())


def phase(item, variant):
    rules = {**base_phases, **({k: {**base_phases.get(k, {}), **v} for k, v in e_phases.items()} if variant == "CHME3E" else {})}
    for name in ("CAB", "OUTSIDE", "BRAKE_PNEUMATIC", "ENGINE_ROOM"):
        rule = rules[name]
        if item["id"] in rule.get("excludeEquipmentIds", []):
            continue
        if item["id"] in rule.get("alsoEquipmentIds", []) or item["systemId"] in rule.get("systems", []):
            return name
    return "ENGINE_ROOM"


def write(target, value):
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def write_asset(target, value):
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    target.write_bytes(gzip.compress(payload, compresslevel=9, mtime=0))


for variant, name, equipment, views in (
    ("CHME3", "ЧМЭ3", base_equipment, shared_views + base_views),
    ("CHME3T", "ЧМЭ3Т", base_equipment, shared_views + base_views + t_views),
    ("CHME3E", "ЧМЭ3Э", base_equipment + variant_equipment, shared_views + e_views),
):
    equipment = [item for item in equipment if name in item.get("applicability", [])]
    ids = {item["id"] for item in equipment}
    entries = []
    systems = index["systems"] + (variants["systems"] if variant == "CHME3E" else [])
    for system in systems:
        members = [item["id"] for item in equipment if item["systemId"] == system["id"]]
        if members:
            member_names = [next(x["name"] for x in equipment if x["id"] == mid) for mid in members]
            entries.append(entry(system, "SYSTEMS", system["title"], [("Оборудование", member_names)],
                                 related=members, aliases=member_names))
    for item in equipment:
        title = item.get("name", item["id"])
        blocks = [("Назначение", fields(item, "purpose", "principle")), ("Расположение", fields(item, "location")),
                  ("Нормальное состояние", fields(item, "normalState")), ("Признаки отклонения", fields(item, "deviationSigns")),
                  ("Безопасность", fields(item, "safetyNotes")), ("Источники", fields(item, "evidenceRefs"))]
        related = [item["systemId"], *item.get("relatedIds", []), *item.get("schemeRefs", []),
                   *(s["id"] for s in all_scenarios.values() if name in s["profiles"] and item["id"] in s["equipmentIds"])]
        aliases = item.get("aliases", []) + item.get("assistantTerms", [])
        entries.append(entry(item, "EQUIPMENT", title, blocks, related, aliases=aliases))
        article = {**item, "id": item["id"].replace("-EQ-", "-KB-")}
        entries.append(entry(article, "KNOWLEDGE", title, blocks, related=[item["id"], *related], aliases=aliases))
    for view in views:
        hotspots = [dict(equipmentId=h["equipmentId"], label=h["label"], **h["layoutHint"]) for h in view.get("hotspotLayer", []) if h["equipmentId"] in ids]
        if not hotspots:
            continue
        section = "PNEUMATIC" if view.get("schemeType") in ("pneumatic", "fluid", "air", "brake_pneumatic") else "ELECTRICAL"
        flows = [f"{f['from']} → {f['to']}: {f['label']}" for f in view.get("flowLayer", [])]
        if not flows:
            flows = [f"{source} → {target}: {label}" for source, target, label in e_scheme_flows.get(view.get("sourceSchemeRef"), [])]
        entries.append(entry(view, section, view["title"], [("Направления связей", flows), ("Профиль", [name])],
                             related=[h["equipmentId"] for h in hotspots], hotspots=hotspots,
                             sequence=[h["equipmentId"] for h in hotspots],
                             aliases=[view.get("sourceSchemeRef", "")], status="INTERACTIVE_SCHEME"))
    acceptance = []
    req_items = [item for item in required if variant == "CHME3T" or item["id"] != "CHME3T-REQ-21"]
    for item in req_items:
        acceptance.append(entry(item, "ACCEPTANCE", item["title"], [("Проверка", fields(item, "check")),
                            ("Граница действий", fields(item, "actionBoundary")), ("Источники", fields(item, "sourceRefs"))], status="MANDATORY_CHECK"))
    if variant == "CHME3E":
        for item in e_checks:
            acceptance.append(entry(item, "ACCEPTANCE", item["title"], [("Проверка", fields(item, "check")),
                                 ("Граница действий", fields(item, "actionBoundary"))],
                                 related=item.get("equipmentIds", []) + item.get("diagnosticIds", []), status="PROFILE_EXTENDED_CHECK"))
    for item in equipment:
        aid = item["id"].replace("-EQ-", "-ACC-")
        record = {**item, "id": aid}
        acceptance.append(entry(record, "ACCEPTANCE", item.get("name", item["id"]),
                                [("Нормальное состояние", fields(item, "normalState")),
                                 ("Признаки отклонения", fields(item, "deviationSigns")),
                                 ("Граница действий", ["Фиксировать состояние; порядок работ сверять с действующей документацией."])],
                                related=[item["id"]], aliases=item.get("assistantTerms", []), status="CHECK"))
    required_ids = [item["id"] for item in req_items] + ([item["id"] for item in e_checks] if variant == "CHME3E" else [])
    expanded = [(item["id"], phase(item, variant)) for item in equipment]
    for route in routes:
        order = route.get("phaseOrder", [])
        sequence = required_ids if route["mode"] == "required_only" else unique(required_ids + [eid.replace("-EQ-", "-ACC-") for area in order for eid, actual in expanded if actual == area])
        acceptance.append(entry(route, "ACCEPTANCE", route["title"], [("Маршрут", ["Отмечайте состояние и замечания по каждому пункту."])],
                                sequence=sequence, status=route["status"]))
    # Session states are keyed by item ID in the existing UI. Keep them isolated
    # for each of the three independently selected locomotives.
    for item in acceptance:
        item["id"] = f"{variant}-{item['id']}"
        item["sequence"] = [f"{variant}-{source_id}" for source_id in item["sequence"]]
    entries += acceptance
    diagnostics = []
    for scenario in all_scenarios.values():
        if name not in scenario["profiles"]:
            continue
        graph = graphs[scenario["id"]]
        nodes = []
        for node in graph["nodes"]:
            projected = dict(node)
            projected["text"] = node.get("text", node.get("prompt", ""))
            if node.get("uncertainNextNodeId"):
                projected["choices"] = node.get("choices", []) + [dict(label="Не знаю / не удалось проверить", nextNodeId=node["uncertainNextNodeId"], responseKind="UNKNOWN")]
            nodes.append(projected)
        diagnostics.append(dict(id=scenario["id"], title=scenario["title"], symptom="; ".join(scenario.get("symptoms", [])),
                                severity=scenario.get("riskClass", ""), category=scenario.get("category", "Тепловоз"),
                                equipmentRefs=scenario["equipmentIds"],
                                searchTerms=unique(scenario.get("queryTerms", []) + e_routes.get(scenario["id"], {}).get("positive", []) +
                                                   ([e_spoken[scenario["id"]]] if scenario["id"] in e_spoken else [])),
                                graph=dict(startNodeId=graph["startNodeId"], nodes=nodes),
                                sourceAgeNote=scenario.get("evidenceStatus", scenario.get("provenanceStatus", ""))))
        entries.append(entry(scenario, "DIAGNOSTICS", scenario["title"],
                             [("Наблюдаемые признаки", scenario.get("symptoms", [])),
                              ("Граница действий", [scenario.get("actionAuthority", "")])],
                             related=scenario["equipmentIds"], aliases=scenario.get("queryTerms", []), status="DIAGNOSTIC_ROUTE"))
    for base in (ROOT / "app", ROOT / "patch/app"):
        write_asset(base / f"src/main/assets/technical/chme3_{variant.lower()}_catalog.json.gz", dict(entries=entries))
        write_asset(base / f"src/main/assets/technical/chme3_{variant.lower()}_diagnostics.json.gz", dict(scenarios=diagnostics))
    print(variant, len(equipment), len(views), len(acceptance), len(diagnostics))
