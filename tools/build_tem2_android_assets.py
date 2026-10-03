#!/usr/bin/env python3
"""Project the completed TEM2 knowledge manifest into Android catalog/runtime assets.

The manifest declares profile applicability; filenames here only locate the manifest's
canonical common layer. No sibling profile is inferred from an identifier prefix.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'docs/locomotives/diesel/tem2-family/common'
MANIFEST = json.loads((ROOT / 'docs/locomotives/diesel/tem2-family/family_manifest.json').read_text())
STAGE2 = json.loads((BASE / 'stage2_model.json').read_text())
STAGE3 = json.loads((BASE / 'stage3_model.json').read_text())
STAGE4 = json.loads((BASE / 'stage4_acceptance.json').read_text())
SOURCES = json.loads((BASE / 'source_registry.json').read_text())['sources'] + STAGE3['researchEvidence']
PROFILE_FAMILY = {'tem2-base': 'TEM2', 'tem2u-improved': 'TEM2U'}
assert {p['profileId'] for p in MANIFEST['profiles']} == set(PROFILE_FAMILY)
assert MANIFEST['canonicalLayers']['common'] == 'common'

def write(relative, obj):
    for tree in ('app', 'patch/app'):
        target = ROOT / tree / 'src/main/assets/technical' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')

def card(id, section, title, subtitle='', status='SOURCE_BACKED_FUNCTIONAL', blocks=(), related=(), sequence=(), hotspots=(), aliases=()):
    return {'id': id, 'section': section, 'title': title, 'subtitle': subtitle,
            'status': status, 'blocks': [{'title': t, 'lines': list(lines)} for t, lines in blocks],
            'relatedIds': list(dict.fromkeys(related)), 'sequence': list(sequence),
            'hotspots': list(hotspots), 'searchAliases': list(aliases),
            'searchText': ' '.join([title, subtitle, *aliases, *(x for _, lines in blocks for x in lines)]).lower()}

source_by_id = {x['id']: x for x in SOURCES}
source_title = {x['id']: f"{x['title']} ({x.get('year', 'редакция не указана')}; {x.get('currentness', 'статус не указан')}; применимость: {', '.join(x.get('applicability', []))})" for x in SOURCES}
def display_source_for_profile(source_id, family):
    source = source_by_id.get(source_id)
    if source is None:
        return False
    # A sibling's factory manual is evidence in the common foundation, but it
    # must not appear as the selected locomotive's equipment authority in UI.
    return source.get('kind') != 'MANUFACTURER_MANUAL' or (
        'ТЭМ2У' if family == 'TEM2U' else 'ТЭМ2') in source.get('applicability', [])
def expanded_sources(groups):
    return list(dict.fromkeys(i for group in groups for i in STAGE2['sourceGroups'].get(group, [group])))
system_title = {x[0]: x[1] for x in STAGE2['systems']}
equipment = {x[0]: x for x in STAGE2['equipment']}
schemes = STAGE2['schemes']
flows = STAGE2['flows']
scenarios = STAGE3['scenarios']
route_aliases = {}
for phrase, scenario_id in STAGE3['assistantSemantics']['routeCases'] + STAGE3['assistantSemantics']['spokenQueries']:
    route_aliases.setdefault(scenario_id, []).append(phrase)
route_aliases.setdefault('TEM2-DIAG-016', []).append('компрессор не выключается')
route_aliases.setdefault('TEM2-DIAG-009', []).append('выбило защиту')
route_aliases.setdefault('TEM2-DIAG-006', []).append('дизель сам заглох')
core_checks = STAGE4['coreChecks'] + STAGE4['profileChecks']
phase_names = {'OUTSIDE':'Снаружи', 'ENGINE_ROOM':'Машинное отделение', 'CAB':'Кабина', 'BRAKE_PNEUMATIC':'Тормоза и пневматика'}
entries = {family: [] for family in PROFILE_FAMILY.values()}
sequences, layouts = [], []

for profile, family in PROFILE_FAMILY.items():
    rows = entries[family]
    applicable = {x[0]: x for x in STAGE2['equipment'] if profile in x[3]}
    assert len(applicable) == STAGE4['expandedAcceptance']['expectedEquipmentCountByProfile'][profile]
    for sid, title, domain in STAGE2['systems']:
        ids = [eid for eid, x in applicable.items() if x[2] == sid]
        rows.append(card(f'{family}-{sid}', 'SYSTEMS', title, domain, blocks=[('Оборудование', [applicable[i][1] for i in ids])], related=ids))
    for eid, title, sid, app, src in applicable.values():
        source_ids = expanded_sources(src)
        related_schemes = [f'{family}-INT-{s["id"]}' for s in schemes if profile in s['app'] and eid in s['nodes']]
        related_diags = [s['id'] for s in scenarios if profile in s['profileIds'] and eid in s['equipmentIds']]
        rows.append(card(eid, 'EQUIPMENT', title, system_title[sid], 'PROFILE_CONFIRMATION_REQUIRED',
            [('Применимость', [family, 'Фактически установленное исполнение подлежит подтверждению']),
             ('Источники', [source_title[i] for i in source_ids])],
             related=[*related_schemes, *related_diags, f'{family}-ACC-{eid}'], aliases=[title]))
    for scheme in schemes:
        if profile not in scheme['app']: continue
        sid = scheme['id']; view_id = f'{family}-INT-{sid}'
        nodes = [eid for eid in scheme['nodes'] if eid in applicable]
        related_flows = [f for f in flows if f[3] == sid and profile in f[2]]
        hotspots = [{'equipmentId': eid, 'label': applicable[eid][1], 'x': 20+(i%3)*250,
                     'y':20+(i//3)*115, 'width':220, 'height':88} for i,eid in enumerate(nodes)]
        rows.append(card(view_id, 'ELECTRICAL' if scheme['domain']=='ELECTRICAL' else 'PNEUMATIC',
            scheme['title'], 'Функциональная схема — не монтажный чертёж',
            blocks=[('Уровень точности',['SOURCE_BACKED_FUNCTIONAL: направления связей без номеров проводов, клемм и труб']),
                    ('Источники',[source_title[x] for x in expanded_sources(scheme['src']) if x in source_title])],
            related=nodes, sequence=nodes, hotspots=hotspots))
        graph_steps = []
        for flow in related_flows:
            ids = [eid for eid in flow[4] if eid in applicable]
            graph_steps.append({'title':scheme['title'], 'explanation':'Функциональная последовательность; проверьте фактическое исполнение.',
                'edges':[{'fromEquipmentId':a, 'toEquipmentId':b,
                           'flowKind': {'ELECTRICAL_TRACTION_CURRENT':'TRACTION_CURRENT','ELECTRICAL_EXCITATION':'EXCITATION','ELECTRICAL_CONTROL_POWER':'CONTROL_POWER','ELECTRICAL_AUX_POWER':'AUXILIARY_POWER','PNEUMATIC_SUPPLY':'AIR_SUPPLY','PNEUMATIC_BRAKE_CONTROL':'CONTROL_PRESSURE','PNEUMATIC_BRAKE_ACTUATION':'BRAKE_CYLINDER_CONTROL'}.get(flow[1], 'COMMAND'),
                           'label':flow[1].replace('_',' ').lower()} for a,b in zip(ids, ids[1:])]})
        if not graph_steps: graph_steps = [{'title':'Обзор оборудования','explanation':'Функциональные связи не подтверждены для этого представления.','edges':[]}]
        seq_id = f'{family}-{sid}-FLOW'
        sequences.append({'id':seq_id,'family':family,'sourceSchemeRef':view_id,'title':scheme['title'],
            'functionalDisclaimer':'Функциональная последовательность по источникам; не является монтажной схемой и не подтверждает фактически установленное оборудование.',
            'steps':graph_steps,'sourcePresentations':[{'title':source_title[x], 'documentDetails':source_by_id[x].get('organization','')} for x in expanded_sources(scheme['src']) if x in source_title]})
        layout_nodes = [{'nodeKey':eid,'equipmentId':eid,'label':applicable[eid][1],
            'position':{'x':20+(i%3)*250,'y':20+(i//3)*115,'width':220,'height':88}} for i,eid in enumerate(nodes)]
        layouts.append({'sequenceId':seq_id,'contentBounds':{'left':0,'top':0,'right':750,'bottom':max(230, 40+((len(nodes)+2)//3)*115)},'nodes':layout_nodes})
    checks = [x for x in core_checks if profile in x['profiles']]
    for check in checks:
        rows.append(card(f'{family}-{check["id"]}','ACCEPTANCE',check['title'],phase_names[check['phase']],check['scopeStatus'],
            [('Проверка',[check['observation']]),('Нормальное состояние',[check['normalState']]),
             ('Признаки отклонения',[check['deviationState']]),
             ('Источники',[source_title[x] for x in check['sourceRefs'] if display_source_for_profile(x, family)])],
             related=[i for i in check['equipmentRefs'] if i in applicable]
                     +[i for i in check['diagnosticRefs'] if any(
                         scenario['id'] == i and profile in scenario['profileIds'] for scenario in scenarios)]))
    for eid, item in applicable.items():
        phase = STAGE4['expandedAcceptance']['phaseOverrides'].get(eid, STAGE4['expandedAcceptance']['systemPhaseMap'][item[2]])
        rows.append(card(f'{family}-ACC-{eid}','ACCEPTANCE',item[1],phase_names[phase],
            'REFERENCE_CHECK_LOCAL_PROCESS_REQUIRED',
            [('Проверка',['Сверить фактическое наличие и доступные признаки состояния; объём определяет применимый локальный процесс.'])],related=[eid]))
    for route in STAGE4['routes']:
        ordered = [x for phase in route['phases'] for x in checks if x['phase']==phase]
        expanded = [x for phase in route['phases'] for x in applicable.values()
                    if STAGE4['expandedAcceptance']['phaseOverrides'].get(x[0],STAGE4['expandedAcceptance']['systemPhaseMap'][x[2]])==phase]
        route_suffix = 'route_from_outside' if route['phases'][0]=='OUTSIDE' else 'route_from_cab'
        rows.append(card(f'{family}-ROUTE-{route_suffix}','ACCEPTANCE',route['title'],
            'Маршрут с сохранением отметок и заметок', 'REFERENCE_CHECK_LOCAL_PROCESS_REQUIRED',
            [('Объём',['Core и расширенные справочные пункты; локальный утверждённый процесс имеет приоритет.'])],
            sequence=[*[f'{family}-{x["id"]}' for x in ordered],*[f'{family}-ACC-{x[0]}' for x in expanded]]))
    rows.append(card(f'{family}-ROUTE-route_canonical','ACCEPTANCE','Полный осмотр',
        'Выберите маршрут; отметки физических пунктов общие',
        'REFERENCE_CHECK_LOCAL_PROCESS_REQUIRED',
        sequence=[f'{family}-ROUTE-route_from_outside', f'{family}-ROUTE-route_from_cab']))
    assert len([x for x in rows if x['section']=='EQUIPMENT'])==len(applicable)
    write(f'tem2_{family.lower()}_catalog.json',{'schemaVersion':1,'profileId':profile,'entries':rows})

assert len(sequences)==17 and sum(len(x['hotspots']) for rows in entries.values() for x in rows if x['section'] in ('ELECTRICAL','PNEUMATIC'))==91
write('tem2_stepwise_scheme_flows.json',{'sequences':sequences})
write('tem2_scheme_layout_metadata.json',{'layouts':layouts})

for profile, family in PROFILE_FAMILY.items():
    selected = [x for x in scenarios if profile in x['profileIds']]
    applicable_ids = {x[0] for x in STAGE2['equipment'] if profile in x[3]}
    projected=[]
    for item in selected:
        graph=item['runtimeGraph']; nodes=[]
        for node in graph['nodes']:
            text=node['text']
            if node['type']=='check':
                text += '\nОжидается: '+node['expected']+'\nПри отклонении: '+node['ifAbnormal']
            nodes.append({'id':node['id'],'type':node['type'],'text':text,'nextNodeId':node.get('nextNodeId'),
                'actionLevel':node.get('actionLevel','INFORMATION_ONLY'),
                'choices':node.get('choices',[])+([{'label':'Не знаю / исполнение не подтверждено','nextNodeId':node['uncertainNextNodeId'],'responseKind':'UNKNOWN'}] if node.get('uncertainNextNodeId') else [])})
        projected.append({'id':item['id'],'title':item['title'],'symptom':'; '.join(item['symptoms']),
            'category':item['category'],'severity':item['riskClass'],
            'equipmentRefs':[i for i in item['equipmentIds'] if i in applicable_ids],
            'searchTerms':item['queries']+item['symptoms']+route_aliases.get(item['id'],[]),
            'sourceAgeNote':'Исторические источники не дают действующего разрешения на действия.',
            'informationConfidence':'SOURCE_BACKED_FUNCTIONAL',
            'applicability':{'profiles':item['profileIds'],'variantSelectionRequired':True},
            'graph':{'startNodeId':graph['startNodeId'],'nodes':nodes},
            'sourceRefs':[{'sourceId':i,'document':source_title.get(i,'Источник не установлен'),'role':'FACTORY_BASELINE_OR_CURRENT_REFERENCE'} for i in item['evidenceRefs']]})
    assert len(projected)==(22 if profile=='tem2-base' else 24)
    write(f'tem2_{family.lower()}_diagnostics.json',{'scenarios':projected})
    for item in selected:
        entries[family].append(card(item['id'],'DIAGNOSTICS',item['title'],
            '; '.join(item['symptoms']),item['riskClass'],
            [('Наблюдение',item['symptoms']),('Источники',[source_title.get(i,'Источник не установлен') for i in item['evidenceRefs']])],
            related=[i for i in item['equipmentIds'] if i in applicable_ids]+[f'{family}-INT-{i}' for i in item['schemeRefs']],
            aliases=item['queries']+route_aliases.get(item['id'],[])))
    write(f'tem2_{family.lower()}_catalog.json',{'schemaVersion':1,'profileId':profile,'entries':entries[family]})

print('TEM2 projection:',len(STAGE2['systems']),'systems',len(STAGE2['equipment']),'equipment',
      len(schemes),'schemes',len(flows),'flows',sum(len(x[4]) for x in flows),'steps',
      len(sequences),'views',sum(len(x['hotspots']) for rows in entries.values() for x in rows if x['section'] in ('ELECTRICAL','PNEUMATIC')),'hotspots')

for tree in ('app','patch/app'):
    target=ROOT/tree/'src/main/assets/technical/source_presentations.json'
    data=json.loads(target.read_text())
    for sid,title in source_title.items():
        data['sources'][sid]={'title':title,'detail':source_by_id[sid].get('organization','')}
    target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
