#!/usr/bin/env python3
import gzip
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_OUT = ROOT / "app/src/main/assets/technical/stepwise_scheme_flows.json"
PATCH_OUT = ROOT / "patch/app/src/main/assets/technical/stepwise_scheme_flows.json"
CHECKPOINT = ROOT / "docs/integration/PACKAGE_B_STEPWISE_FLOWS_CHECKPOINT.md"

FLOW_LEGEND = {
    "HIGH_VOLTAGE_POWER": ("Высоковольтное питание", "solid-heavy"),
    "TRACTION_CURRENT": ("Тяговый ток", "solid-heavy-arrow"),
    "REGENERATIVE_CURRENT": ("Ток рекуперации", "solid-heavy-reverse"),
    "BRAKING_CURRENT": ("Тормозной ток / энергия", "solid-heavy-brake"),
    "AUXILIARY_POWER": ("Питание собственных нужд", "solid-medium"),
    "CONTROL_POWER": ("Питание цепей управления", "solid-thin"),
    "COMMAND": ("Команда / разрешение", "dashed-arrow"),
    "CONTROL_SIGNAL": ("Управляющий сигнал", "dashed-arrow"),
    "FEEDBACK_SIGNAL": ("Сигнал обратной связи", "dotted-arrow"),
    "EXCITATION": ("Ток / воздействие возбуждения", "dash-dot-arrow"),
    "STATUS_SIGNAL": ("Сигнал состояния", "dotted-arrow"),
    "SAFETY_COMMAND": ("Команда системы безопасности", "double-dashed-arrow"),
    "FIRE_SIGNAL": ("Сигнал пожарной системы", "dotted-alert"),
    "AIR_SUPPLY": ("Подача сжатого воздуха", "solid-pneumatic"),
    "CONTROL_PRESSURE": ("Управляющее давление", "dashed-pneumatic"),
    "BRAKE_PIPE_PRESSURE_CHANGE": ("Изменение давления тормозной магистрали", "double-pneumatic"),
    "BRAKE_CYLINDER_CONTROL": ("Наполнение / отпуск тормозных цилиндров", "solid-pneumatic-brake"),
    "EXHAUST_RELEASE": ("Разрядка / выпуск в атмосферу", "dotted-pneumatic-out"),
    "COOLANT_FLOW": ("Циркуляция теплоносителя", "wave-arrow"),
    "STARTER_CURRENT": ("Пусковой ток", "solid-heavy-start"),
}

CONTRACT = {
    "representationKinds": ["SOURCE_BACKED_FUNCTIONAL"],
    "domains": ["ELECTRICAL", "PNEUMATIC"],
    "rules": [
        "Каждая последовательность имеет явную применимость и источник.",
        "SOURCE_BACKED_FUNCTIONAL описывает функциональный путь и не выдается за монтажную или принципиальную трассировку проводов/труб.",
        "Силовой ток, питание управления, команда, обратная связь и давление кодируются разными flowKind.",
        "Смысл шага передается подписью и типом линии; цвет не является единственным носителем смысла.",
        "Профильные узлы ЧМЭ3Т и ЧМЭ3Э запрещено переносить на другие исполнения без отдельного подтверждения.",
        "Данные INFORMATION_ONLY не создают права на подключение внешних источников, обход защиты или ремонтное вмешательство."
    ],
    "legend": [
        {"flowKind": key, "label": value[0], "lineStyle": value[1]}
        for key, value in FLOW_LEGEND.items()
    ],
}


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_gzip_json(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def collect_source_presentations():
    result = {}
    roots = [ROOT / "docs/locomotives/chme3", ROOT / "docs/locomotives/diesel/chme3-family"]

    def walk(value):
        if isinstance(value, dict):
            source_id = value.get("id")
            title = value.get("title")
            if isinstance(source_id, str) and isinstance(title, str) and source_id.startswith("CHME3"):
                result.setdefault(source_id, {
                    "sourceId": source_id,
                    "title": title,
                    "documentDetails": ", ".join(str(x) for x in [value.get("author"), value.get("year")] if x),
                    "provenanceStatus": value.get("provenanceStatus") or value.get("evidenceStatus") or value.get("kind") or "REFERENCE",
                    "authorityForAction": value.get("authorityForAction") or "NO_BY_ITSELF",
                    "url": value.get("url") or "",
                })
            for nested in value.values():
                walk(nested)
        elif isinstance(value, list):
            for nested in value:
                walk(nested)

    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*.json"):
            if "source" not in path.name.lower():
                continue
            try:
                walk(load_json(path))
            except Exception:
                continue
    return result


def ermak_flow_kind(scheme, flow):
    domain = "PNEUMATIC" if "pneumatic" in (scheme.get("schemeType") or "").lower() else "ELECTRICAL"
    text = " ".join(str(flow.get(k, "")) for k in ("id", "title")).lower()
    if domain == "PNEUMATIC":
        if "service_brake" in text or "служеб" in text:
            return "BRAKE_PIPE_PRESSURE_CHANGE"
        if "charging" in text or "заряд" in text or "air_supply" in text or "запас воздуха" in text:
            return "AIR_SUPPLY"
        if "remote_brake" in text or "pantograph" in text or "гв" in text or "дистанцион" in text:
            return "CONTROL_PRESSURE"
        if "substitution" in text or "замещ" in text:
            return "BRAKE_CYLINDER_CONTROL"
        return "CONTROL_PRESSURE"
    if "regeneration" in text or "рекуп" in text:
        return "REGENERATIVE_CURRENT"
    if "traction" in text or text.strip().startswith("тяга"):
        return "TRACTION_CURRENT"
    if "hv_path" in text or "высок" in text:
        return "HIGH_VOLTAGE_POWER"
    if any(x in text for x in ("aux_supply", "cooling", "compressor", "собствен", "охлаж", "компресс")):
        return "AUXILIARY_POWER"
    if "control_power" in text or "питание и управление" in text:
        return "CONTROL_POWER"
    if "feedback" in text or "обратн" in text or "ток/скорость" in text:
        return "FEEDBACK_SIGNAL"
    if "safety" in text or "blok_brake" in text or "безопас" in text or "автостоп" in text:
        return "SAFETY_COMMAND"
    if "fire" in text or "пожар" in text:
        return "FIRE_SIGNAL"
    return "COMMAND"


def ermak_sequences():
    data = load_gzip_json(ROOT / "app/src/main/assets/technical/ermak_schemes.json.gz")
    result = []
    for scheme in data.get("schemes", []):
        flows = (scheme.get("layers") or {}).get("flowLayer") or []
        if not flows:
            continue
        scheme_type = (scheme.get("schemeType") or "").lower()
        domain = "PNEUMATIC" if "pneumatic" in scheme_type else "ELECTRICAL"
        hotspots = (scheme.get("layers") or {}).get("hotspotLayer") or []
        by_hotspot = {}
        for h in hotspots:
            key = h.get("id") or h.get("hotspotId") or h.get("nodeId")
            if key:
                by_hotspot[key] = h
        sources = []
        for src in scheme.get("sourceRefs") or []:
            if isinstance(src, str):
                sources.append({"sourceId": src, "title": "Источник схемы", "documentDetails": "", "locator": "", "role": "reference"})
            else:
                sources.append({
                    "sourceId": src.get("sourceId", ""),
                    "title": src.get("title") or src.get("document") or "Источник схемы",
                    "documentDetails": src.get("document") or "",
                    "locator": src.get("locator") or "",
                    "role": src.get("role") or "reference",
                    "url": src.get("url") or "",
                })
        for flow in flows:
            path = flow.get("path") or []
            if len(path) < 2:
                continue
            flow_kind = ermak_flow_kind(scheme, flow)
            steps = []
            for index, (left, right) in enumerate(zip(path, path[1:]), 1):
                lh = by_hotspot.get(left, {})
                rh = by_hotspot.get(right, {})
                ltitle = lh.get("label") or lh.get("title") or f"Узел {index}"
                rtitle = rh.get("label") or rh.get("title") or f"Узел {index + 1}"
                eq_ids = [x for x in [lh.get("equipmentId"), rh.get("equipmentId")] if x]
                steps.append({
                    "step": index,
                    "title": f"{ltitle} → {rtitle}",
                    "activeHotspotIds": [left, right],
                    "activeEquipmentIds": list(dict.fromkeys(eq_ids)),
                    "edges": [{
                        "fromAnchor": left,
                        "toAnchor": right,
                        "flowKind": flow_kind,
                        "label": flow.get("title") or "Переход",
                    }],
                    "explanation": flow.get("title") or "Функциональный переход по схеме.",
                })
            slug = re.sub(r"[^A-Z0-9]+", "-", f"{scheme.get('id','')}-{flow.get('id','FLOW')}".upper()).strip("-")
            result.append({
                "id": f"STEP-{slug}",
                "family": "ERMAK",
                "title": f"{scheme.get('title', 'Ермак')} — {flow.get('title', 'цепь')}",
                "domain": domain,
                "representation": "SOURCE_BACKED_FUNCTIONAL",
                "functionalDisclaimer": flow.get("note") or "Функциональная подсветка; не монтажная трассировка.",
                "applicability": scheme.get("variantRules") or {"models": ["2ES5K", "3ES5K"], "selectionRequired": True},
                "sourceSchemeRef": scheme.get("id"),
                "sourcePresentations": sources,
                "authority": "INFORMATION_ONLY",
                "origin": {"kind": "ERMAK_FLOW_LAYER", "flowRecordId": flow.get("id"), "confidence": flow.get("confidence") or "source_backed_functional"},
                "steps": steps,
            })
    return result


def source_scheme_index():
    common = load_json(ROOT / "docs/locomotives/chme3/atlas_schemes_functional.json")
    variant = load_json(ROOT / "docs/locomotives/diesel/chme3-family/chme3e/schemes_variant.json")
    records = common.get("records", []) + variant.get("records", [])
    return {x["id"]: x for x in records}


def source_views_index():
    shared = load_json(ROOT / "docs/locomotives/diesel/chme3-family/common/interactive_shared_views.json")
    base = load_json(ROOT / "docs/locomotives/diesel/chme3-family/chme3/interactive_schemes.json")
    t = load_json(ROOT / "docs/locomotives/diesel/chme3-family/chme3t/interactive_schemes.json")
    e = load_json(ROOT / "docs/locomotives/diesel/chme3-family/chme3e/interactive_schemes.json")
    views = shared.get("views", []) + base.get("profileViews", []) + t.get("profileViews", []) + e.get("profileViews", [])
    return {x["id"]: x for x in views}


def chme_source_presentations(source_ids, source_map):
    out = []
    for source_id in source_ids:
        item = source_map.get(source_id)
        if not item:
            raise SystemExit(f"Unresolved CHME source reference: {source_id}")
        out.append(item)
    return out


def mk_step(n, title, edges, explanation=None):
    active = []
    for edge in edges:
        active.extend([edge[0], edge[1]])
    return {
        "step": n,
        "title": title,
        "activeEquipmentIds": list(dict.fromkeys(active)),
        "edges": [{"fromEquipmentId": a, "toEquipmentId": b, "flowKind": kind, "label": label} for a,b,kind,label in edges],
        "explanation": explanation or title,
    }


def chme_sequence(seq_id, family, title, domain, profiles, source_scheme_ref, steps, scheme_index, source_map, safety=None, authority="INFORMATION_ONLY", research_notes=None):
    scheme = scheme_index[source_scheme_ref]
    result = {
        "id": seq_id,
        "family": family,
        "title": title,
        "domain": domain,
        "representation": "SOURCE_BACKED_FUNCTIONAL",
        "functionalDisclaimer": "Функциональная последовательность по проверенным связям Атласа; не является монтажной трассировкой проводов, клемм или трубопроводов.",
        "applicability": {"families": [family], "profileIds": profiles, "selectionRequired": family in ("CHME3T", "CHME3E")},
        "sourceSchemeRef": source_scheme_ref,
        "sourcePresentations": chme_source_presentations(scheme.get("evidenceRefs", []), source_map),
        "authority": authority,
        "origin": {"kind": "CHME_ATLAS_FUNCTIONAL_SCHEME", "evidenceStatus": scheme.get("evidenceStatus") or "TRAINING_REFERENCE"},
        "steps": steps,
    }
    if safety:
        result["safetyNote"] = safety
    if research_notes:
        result["researchNotes"] = research_notes
    return result


def chme_sequences():
    schemes = source_scheme_index()
    source_map = collect_source_presentations()
    result = []
    base_profiles = ["chme3-base"]
    t_profiles = ["chme3t-rheostatic"]
    e_profiles = ["chme3e-electronic"]

    # ЧМЭ3 base electrical paths. CHME3T reuses the common traction topology through its own UI inheritance;
    # create explicit copies for each browsing family so profile filtering remains deterministic.
    for family, profiles in (("CHME3", base_profiles), ("CHME3T", t_profiles)):
        prefix = family
        result.append(chme_sequence(
            f"{prefix}-STEP-TRACTION-CURRENT", family, f"{family} — путь тягового тока", "ELECTRICAL", profiles, "CHME3-SCH-TRACTION",
            [
                mk_step(1, "Тяговый генератор → поездные контакторы", [("CHME3-EQ-TRACTION-GENERATOR","CHME3-EQ-TRAIN-CONTACTORS","TRACTION_CURRENT","Силовой ток")]),
                mk_step(2, "Поездные контакторы → реверсор", [("CHME3-EQ-TRAIN-CONTACTORS","CHME3-EQ-REVERSER","TRACTION_CURRENT","Коммутация тяговой цепи")]),
                mk_step(3, "Реверсор → тяговые двигатели", [("CHME3-EQ-REVERSER","CHME3-EQ-TRACTION-MOTORS","TRACTION_CURRENT","Ток к ТЭД")]),
            ], schemes, source_map))
        result.append(chme_sequence(
            f"{prefix}-STEP-TRACTION-EXCITATION", family, f"{family} — возбуждение тягового генератора", "ELECTRICAL", profiles, "CHME3-SCH-TRACTION",
            [mk_step(1, "Возбудитель → тяговый генератор", [("CHME3-EQ-EXCITER","CHME3-EQ-TRACTION-GENERATOR","EXCITATION","Возбуждение")])], schemes, source_map))
        result.append(chme_sequence(
            f"{prefix}-STEP-CONTROL-POWER", family, f"{family} — питание и согласование цепей управления", "ELECTRICAL", profiles, "CHME3-SCH-CONTROL",
            [
                mk_step(1, "АКБ → рубильник аккумуляторной батареи", [("CHME3-EQ-BATTERY","CHME3-EQ-BATTERY-SWITCH","CONTROL_POWER","Низковольтное питание")]),
                mk_step(2, "Сервомотор → регулятор дизеля", [("CHME3-EQ-GOVERNOR-SERVOMOTOR","CHME3-EQ-GOVERNOR","COMMAND","Изменение режима дизеля")]),
                mk_step(3, "Регулятор дизеля → цепи поездных контакторов", [("CHME3-EQ-GOVERNOR","CHME3-EQ-TRAIN-CONTACTORS","COMMAND","Согласование режима мощности")]),
            ], schemes, source_map,
            research_notes=["Фактические блокировки, номера проводов и контактов остаются вариантозависимыми и здесь не домысливаются."]))

    # Common pneumatic behavior is duplicated by family deliberately so UI never infers cross-profile applicability.
    for family, profiles in (("CHME3", base_profiles), ("CHME3T", t_profiles), ("CHME3E", e_profiles)):
        result.append(chme_sequence(
            f"{family}-STEP-PNEUMATIC-AIR-SUPPLY", family, f"{family} — создание запаса и подача воздуха", "PNEUMATIC", profiles, "CHME3-SCH-PNEUMATIC",
            [
                mk_step(1, "Компрессор → холодильник", [("CHME3-EQ-COMPRESSOR","CHME3-EQ-INTERCOOLER","AIR_SUPPLY","Сжатый воздух")]),
                mk_step(2, "Холодильник → главные резервуары", [("CHME3-EQ-INTERCOOLER","CHME3-EQ-MAIN-RESERVOIRS","AIR_SUPPLY","Охлаждённый сжатый воздух")]),
                mk_step(3, "Главные резервуары → кран машиниста", [("CHME3-EQ-MAIN-RESERVOIRS","CHME3-EQ-DRIVER-BRAKE-VALVE","AIR_SUPPLY","Питание тормозной системы")]),
            ], schemes, source_map))
        result.append(chme_sequence(
            f"{family}-STEP-PNEUMATIC-AUTO-BRAKE", family, f"{family} — автоматическое торможение и отпуск", "PNEUMATIC", profiles, "CHME3-SCH-PNEUMATIC",
            [
                mk_step(1, "Кран машиниста изменяет давление тормозной магистрали", [("CHME3-EQ-DRIVER-BRAKE-VALVE","CHME3-EQ-AIR-DISTRIBUTOR","BRAKE_PIPE_PRESSURE_CHANGE","Изменение давления ТМ")]),
                mk_step(2, "Воздухораспределитель управляет тормозными цилиндрами", [("CHME3-EQ-AIR-DISTRIBUTOR","CHME3-EQ-BRAKE-CYLINDERS","BRAKE_CYLINDER_CONTROL","Торможение / отпуск")]),
            ], schemes, source_map,
            research_notes=["Точный маршрут выпуска в атмосферу и внутренняя арматура приборов не показываются, пока соответствующие узлы не заведены в канонический Атлас."]))
        result.append(chme_sequence(
            f"{family}-STEP-PNEUMATIC-INDEPENDENT-BRAKE", family, f"{family} — вспомогательное торможение локомотива", "PNEUMATIC", profiles, "CHME3-SCH-PNEUMATIC",
            [mk_step(1, "Кран вспомогательного тормоза → тормозные цилиндры", [("CHME3-EQ-AUX-BRAKE-VALVE","CHME3-EQ-BRAKE-CYLINDERS","CONTROL_PRESSURE","Управляющее давление")])], schemes, source_map))
        result.append(chme_sequence(
            f"{family}-STEP-PNEUMATIC-COMPRESSOR-CONTROL", family, f"{family} — управление работой компрессора по давлению", "PNEUMATIC", profiles, "CHME3-SCH-PNEUMATIC",
            [mk_step(1, "Регулятор давления → компрессор", [("CHME3-EQ-PRESSURE-GOV","CHME3-EQ-COMPRESSOR","CONTROL_PRESSURE","Управление режимом компрессора")])], schemes, source_map))

    result.append(chme_sequence(
        "CHME3T-STEP-EDB-BRAKING-ENERGY", "CHME3T", "ЧМЭ3Т — путь энергии при реостатном торможении", "ELECTRICAL", t_profiles, "CHME3T-SCH-EDB",
        [
            mk_step(1, "Тяговые двигатели переходят в генераторный режим", [("CHME3-EQ-TRACTION-MOTORS","CHME3T-EQ-EDB-CONTROL","BRAKING_CURRENT","Генераторный режим ТЭД")]),
            mk_step(2, "Тормозная энергия направляется в резисторы", [("CHME3T-EQ-EDB-CONTROL","CHME3T-EQ-BRAKE-RESISTORS","BRAKING_CURRENT","Тормозная энергия")]),
        ], schemes, source_map,
        research_notes=["Охлаждение тормозных резисторов отображается отдельной связью и не выдается за путь тормозного тока."]))

    # CHME3E electronic traction. Separate command, feedback, excitation and power current.
    result.append(chme_sequence(
        "CHME3E-STEP-TRACTION-SETPOINT", "CHME3E", "ЧМЭ3Э — формирование задания мощности", "ELECTRICAL", e_profiles, "CHME3E-SCH-TRACTION-CONTROL",
        [
            mk_step(1, "НН106 → YZJK9 / YIND5", [("CHME3E-EQ-CONTROLLER-HH106","CHME3E-EQ-ER-YZJK9-YIND5","COMMAND","Код позиции")]),
            mk_step(2, "YZJK9 / YIND5 → YZV9", [("CHME3E-EQ-ER-YZJK9-YIND5","CHME3E-EQ-ER-YZV9","CONTROL_SIGNAL","Задание мощности")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-TRACTION-FEEDBACK", "CHME3E", "ЧМЭ3Э — обратные связи электронного регулирования", "ELECTRICAL", e_profiles, "CHME3E-SCH-TRACTION-CONTROL",
        [
            mk_step(1, "Датчик оборотов → YPSM5 → YZV9", [
                ("CHME3E-EQ-TACHOMETER-SENSOR-BLOCK","CHME3E-EQ-ER-YPSM5","FEEDBACK_SIGNAL","Импульсы частоты вращения"),
                ("CHME3E-EQ-ER-YPSM5","CHME3E-EQ-ER-YZV9","FEEDBACK_SIGNAL","Сигнал оборотов")]),
            mk_step(2, "ДТГ → YZV9", [("CHME3E-EQ-CURRENT-SENSOR-DTG","CHME3E-EQ-ER-YZV9","FEEDBACK_SIGNAL","Обратная связь по току")]),
            mk_step(3, "ДНГ → YRUI3", [("CHME3E-EQ-VOLTAGE-SENSOR-DNG","CHME3E-EQ-ER-YRUI3","FEEDBACK_SIGNAL","Обратная связь по напряжению")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-TRACTION-EXCITATION", "CHME3E", "ЧМЭ3Э — управление возбуждением тягового генератора", "ELECTRICAL", e_profiles, "CHME3E-SCH-TRACTION-CONTROL",
        [
            mk_step(1, "YZV9 → YKS5", [("CHME3E-EQ-ER-YZV9","CHME3E-EQ-ER-YKS5","CONTROL_SIGNAL","Команда регулирования возбуждения")]),
            mk_step(2, "YKS5 → возбудитель", [("CHME3E-EQ-ER-YKS5","CHME3-EQ-EXCITER","EXCITATION","Управление независимым возбуждением")]),
            mk_step(3, "Возбудитель → тяговый генератор", [("CHME3-EQ-EXCITER","CHME3-EQ-TRACTION-GENERATOR","EXCITATION","Возбуждение генератора")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-TRACTION-CURRENT", "CHME3E", "ЧМЭ3Э — путь тягового тока", "ELECTRICAL", e_profiles, "CHME3E-SCH-TRACTION-CONTROL",
        [
            mk_step(1, "Тяговый генератор → поездные контакторы", [("CHME3-EQ-TRACTION-GENERATOR","CHME3-EQ-TRAIN-CONTACTORS","TRACTION_CURRENT","Силовой ток")]),
            mk_step(2, "Поездные контакторы → тяговые двигатели", [("CHME3-EQ-TRAIN-CONTACTORS","CHME3-EQ-TRACTION-MOTORS","TRACTION_CURRENT","Питание тяговых ветвей")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-FIELD-WEAKENING", "CHME3E", "ЧМЭ3Э — автоматическое ослабление возбуждения ТЭД", "ELECTRICAL", e_profiles, "CHME3E-SCH-FIELD-WEAKENING",
        [
            mk_step(1, "Сигналы условий поступают в YSH11", [
                ("CHME3E-EQ-CURRENT-SENSOR-DTG","CHME3E-EQ-ER-YSH11","FEEDBACK_SIGNAL","Сигнал тока"),
                ("CHME3E-EQ-VOLTAGE-SENSOR-DNG","CHME3E-EQ-ER-YSH11","FEEDBACK_SIGNAL","Сигнал напряжения"),
                ("CHME3E-EQ-RELAY-RE","CHME3E-EQ-ER-YSH11","CONTROL_SIGNAL","Разрешение тягового режима")]),
            mk_step(2, "YSH11 → YOUT8", [("CHME3E-EQ-ER-YSH11","CHME3E-EQ-ER-YOUT8","COMMAND","Команда ступени ослабления")]),
            mk_step(3, "YOUT8 → контакторы КШ", [("CHME3E-EQ-ER-YOUT8","CHME3-EQ-CONTACTORS","COMMAND","Управление КШ1—КШ6")]),
            mk_step(4, "Контакторы КШ изменяют возбуждение ТЭД", [("CHME3-EQ-CONTACTORS","CHME3-EQ-TRACTION-MOTORS","EXCITATION","Шунтирование обмоток возбуждения")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-START", "CHME3E", "ЧМЭ3Э — электронная последовательность пуска", "ELECTRICAL", e_profiles, "CHME3E-SCH-START-ELECTRONIC",
        [
            mk_step(1, "НН106 → YCRA1", [("CHME3E-EQ-CONTROLLER-HH106","CHME3E-EQ-ER-YCRA1","COMMAND","Команда пуска")]),
            mk_step(2, "YCRA1 запускает предварительную маслопрокачку", [("CHME3E-EQ-ER-YCRA1","CHME3-EQ-OIL-PRIME-PUMP","COMMAND","Предварительная прокачка")]),
            mk_step(3, "YCRA1 управляет пусковыми аппаратами", [("CHME3E-EQ-ER-YCRA1","CHME3-EQ-CONTACTORS","COMMAND","Временная последовательность пуска")]),
            mk_step(4, "АКБ питает тяговый генератор в стартерном режиме", [("CHME3-EQ-BATTERY","CHME3-EQ-TRACTION-GENERATOR","STARTER_CURRENT","Пусковой ток")]),
            mk_step(5, "После запуска формируется признак работающего дизеля", [("CHME3-EQ-TRACTION-GENERATOR","CHME3E-EQ-RELAY-RD","STATUS_SIGNAL","Признак работающего дизеля")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-PREHEAT-RUNNING", "CHME3E", "ЧМЭ3Э — обогрев водяной системы от тягового генератора", "ELECTRICAL", e_profiles, "CHME3E-SCH-PREHEAT-RUNNING",
        [
            mk_step(1, "ПО задаёт режим обогрева", [
                ("CHME3E-EQ-MODE-SWITCH-PO","CHME3E-EQ-CONTACTOR-KOG1","COMMAND","Команда КОГ1"),
                ("CHME3E-EQ-MODE-SWITCH-PO","CHME3E-EQ-CONTACTOR-KOG2","COMMAND","Команда КОГ2")]),
            mk_step(2, "Тяговый генератор подаёт питание в цепь обогрева", [("CHME3-EQ-TRACTION-GENERATOR","CHME3E-EQ-CONTACTOR-KOG1","AUXILIARY_POWER","Силовое питание обогрева")]),
            mk_step(3, "КОГ1 / КОГ2 собирают цепь нагревателей", [
                ("CHME3E-EQ-CONTACTOR-KOG1","CHME3E-EQ-CONTACTOR-KOG2","AUXILIARY_POWER","Сборка цепи"),
                ("CHME3E-EQ-CONTACTOR-KOG2","CHME3E-EQ-HEATER-ELEMENTS-R85-R88","AUXILIARY_POWER","Питание R85—R88")]),
            mk_step(4, "Водяной насос обеспечивает циркуляцию", [("CHME3-EQ-WATER-PUMP","CHME3E-EQ-HEATER-ELEMENTS-R85-R88","COOLANT_FLOW","Циркуляция теплоносителя")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-PREHEAT-EXTERNAL", "CHME3E", "ЧМЭ3Э — функциональный путь внешнего прогрева", "ELECTRICAL", e_profiles, "CHME3E-SCH-PREHEAT-EXTERNAL",
        [
            mk_step(1, "ПО → КОП", [("CHME3E-EQ-MODE-SWITCH-PO","CHME3E-EQ-CONTACTOR-KOP","COMMAND","Разрешение внешнего прогрева")]),
            mk_step(2, "КОП распределяет питание на нагрев и насосы", [
                ("CHME3E-EQ-CONTACTOR-KOP","CHME3E-EQ-HEATER-ELEMENTS-R85-R88","AUXILIARY_POWER","Питание нагревателей"),
                ("CHME3E-EQ-CONTACTOR-KOP","CHME3E-EQ-WATER-PUMP-MOTORS-DOV","AUXILIARY_POWER","Питание электроприводов насосов")]),
            mk_step(3, "ДОВ1—ДОВ3 приводят электронасосы", [("CHME3E-EQ-WATER-PUMP-MOTORS-DOV","CHME3E-EQ-ELECTRIC-WATER-PUMPS","AUXILIARY_POWER","Привод насосов")]),
            mk_step(4, "Насосы обеспечивают циркуляцию через нагреваемые контуры", [("CHME3E-EQ-ELECTRIC-WATER-PUMPS","CHME3E-EQ-HEATER-ELEMENTS-R85-R88","COOLANT_FLOW","Принудительная циркуляция")]),
        ], schemes, source_map,
        safety="Только информационная функциональная схема. Не является инструкцией по подключению внешней трёхфазной сети и не разрешает работы под напряжением.",
        authority="INFORMATION_ONLY"))
    result.append(chme_sequence(
        "CHME3E-STEP-REMOTE-CONTROL-ELECTRICAL", "CHME3E", "ЧМЭ3Э — команды переносного пульта", "ELECTRICAL", e_profiles, "CHME3E-SCH-REMOTE-CONTROL",
        [
            mk_step(1, "Переносной пульт → НН106", [("CHME3E-EQ-PORTABLE-CONTROL-PANELS","CHME3E-EQ-CONTROLLER-HH106","COMMAND","Команда изменения режима / мощности")]),
            mk_step(2, "НН106 → GC40P", [("CHME3E-EQ-CONTROLLER-HH106","CHME3E-EQ-ELECTRONIC-REGULATOR-GC40P","CONTROL_SIGNAL","Электронная логика команды")]),
            mk_step(3, "Пульт → сигнальные лампы", [("CHME3E-EQ-PORTABLE-CONTROL-PANELS","CHME3E-EQ-ROOF-SIGNAL-LAMPS","STATUS_SIGNAL","Сигнализация режима")]),
        ], schemes, source_map))
    result.append(chme_sequence(
        "CHME3E-STEP-REMOTE-CONTROL-PNEUMATIC", "CHME3E", "ЧМЭ3Э — пневматическое питание переносного управления", "PNEUMATIC", e_profiles, "CHME3E-SCH-REMOTE-CONTROL",
        [mk_step(1, "Резервуар управления → переносной пульт", [("CHME3-EQ-PNEUMATIC-CONTROL-RESERVOIR","CHME3E-EQ-PORTABLE-CONTROL-PANELS","CONTROL_PRESSURE","Пневматическая энергия исполнительных команд")])], schemes, source_map,
        research_notes=["Внутренний маршрут через электропневматические и выпускные клапаны не изображается до добавления этих приборов как канонических узлов Атласа."]))
    return result


def main():
    sequences = ermak_sequences() + chme_sequences()
    payload = {
        "schemaVersion": 1,
        "packageId": "test-stepwise-scheme-flows-v1",
        "updatedAt": "2026-10-01",
        "state": "PACKAGE_B_DATA_READY_FOR_RENDERER_INTEGRATION",
        "contract": CONTRACT,
        "sequences": sequences,
        "researchGaps": [
            "ЧМЭ: точный маршрут разрядки/выпуска через внутренние клапаны не показывается, пока приборы не заведены в канонический Атлас с подтвержденным источником.",
            "Ермак: существующие flowLayer помечены как source-backed functional и поэтому не переименовываются в монтажную трассировку.",
            "ЧМЭ3Э: внешний трехфазный прогрев остается INFORMATION_ONLY; порядок подключения внешней сети не моделируется.",
        ],
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    APP_OUT.parent.mkdir(parents=True, exist_ok=True)
    PATCH_OUT.parent.mkdir(parents=True, exist_ok=True)
    APP_OUT.write_text(text, encoding="utf-8")
    PATCH_OUT.write_text(text, encoding="utf-8")

    counts = {}
    steps = 0
    domains = {}
    for seq in sequences:
        counts[seq["family"]] = counts.get(seq["family"], 0) + 1
        domains[seq["domain"]] = domains.get(seq["domain"], 0) + 1
        steps += len(seq["steps"])
    checkpoint = f"""# Package B — пошаговые электрические и пневматические цепи\n\nСтатус: **DATA READY FOR RENDERER INTEGRATION**. UI/zoom/pan/palette сюда не входят.\n\n- Всего последовательностей: **{len(sequences)}**\n- Всего шагов: **{steps}**\n- По сериям: {', '.join(f'{k}: {v}' for k,v in sorted(counts.items()))}\n- По доменам: {', '.join(f'{k}: {v}' for k,v in sorted(domains.items()))}\n\n## Граница точности\nВсе записи текущего пакета имеют `SOURCE_BACKED_FUNCTIONAL`. Они основаны на существующих flow-слоях и источниках, но **не выдаются за монтажную трассировку**. Номера проводов, клемм, труб и внутренняя арматура добавляются только при отдельном подтверждении конкретного исполнения.\n\n## Смысл потока\nТяговый ток, рекуперативный/тормозной ток, питание управления, команда, обратная связь, возбуждение, подача воздуха, управляющее давление, изменение давления ТМ и управление тормозными цилиндрами — отдельные `flowKind`. UI не должен определять их по цвету или названию локомотива.\n\n## Профильная изоляция\nЧМЭ3Т ЭДТ доступен только `chme3t-rheostatic`; ЧМЭ3Э использует собственные электронные последовательности. Внешний прогрев ЧМЭ3Э — только информационная функциональная схема без процедуры подключения внешней сети.\n\n## Нерешенные границы\n- Для ЧМЭ не показан точный внутренний путь выпуска/разрядки через клапаны, пока эти приборы не представлены каноническими Atlas-ID с подтвержденным источником.\n- Схемы Ермака сохраняют исходное обозначение `source_backed_functional`; ни одна не повышена автоматически до монтажной.\n\nGenerated by `qa/build_package_b_stepwise_flows.py`.\n"""
    CHECKPOINT.parent.mkdir(parents=True, exist_ok=True)
    CHECKPOINT.write_text(checkpoint, encoding="utf-8")
    print("PACKAGE_B_BUILD_OK", "sequences=", len(sequences), "steps=", steps, "families=", counts, "domains=", domains)


if __name__ == "__main__":
    main()
