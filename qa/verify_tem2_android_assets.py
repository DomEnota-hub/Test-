#!/usr/bin/env python3
"""Read-only Android projection and app/patch consistency guard for TEM2 profiles."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'docs/locomotives/diesel/tem2-family/common'
stage2 = json.loads((BASE/'stage2_model.json').read_text())
stage3 = json.loads((BASE/'stage3_model.json').read_text())
stage4 = json.loads((BASE/'stage4_acceptance.json').read_text())
assets = ('tem2_tem2_catalog.json','tem2_tem2u_catalog.json','tem2_tem2_diagnostics.json',
          'tem2_tem2u_diagnostics.json','tem2_stepwise_scheme_flows.json','tem2_scheme_layout_metadata.json',
          'source_presentations.json')
for name in assets:
    app = (ROOT/'app/src/main/assets/technical'/name).read_bytes()
    patch = (ROOT/'patch/app/src/main/assets/technical'/name).read_bytes()
    assert app == patch, f'app/patch mismatch: {name}'

def asset(name):
    return json.loads((ROOT/'app/src/main/assets/technical'/name).read_text())

for profile, family, equipment_count, scenarios in (
        ('tem2-base','tem2',33,22),('tem2u-improved','tem2u',35,24)):
    catalog=asset(f'tem2_{family}_catalog.json')
    assert catalog['profileId']==profile
    rows=catalog['entries']; ids=[r['id'] for r in rows]
    assert len(ids)==len(set(ids)), f'{profile}: duplicate catalog ID'
    byid={r['id']:r for r in rows}
    for row in rows:
        references = row.get('relatedIds', []) + row.get('sequence', []) + [
            hotspot['equipmentId'] for hotspot in row.get('hotspots', [])]
        assert all(ref in byid for ref in references), (
            profile, row['id'], [ref for ref in references if ref not in byid])
        if row['section'] == 'ACCEPTANCE':
            source_lines = [line for block in row.get('blocks', []) if block['title'] == 'Источники'
                            for line in block['lines']]
            sibling_manual = 'Тепловоз ТЭМ2У. Руководство' if family == 'tem2' else (
                'Тепловоз ТЭМ2. Руководство')
            assert all(sibling_manual not in line for line in source_lines), (profile, row['id'])
    assert len([r for r in rows if r['section']=='SYSTEMS'])==9
    assert len([r for r in rows if r['section']=='EQUIPMENT'])==equipment_count
    assert len([r for r in rows if r['section'] in ('ELECTRICAL','PNEUMATIC')])==(8 if family=='tem2' else 9)
    assert len([r for r in rows if r['section']=='DIAGNOSTICS'])==scenarios
    assert len([r for r in rows if r['section']=='ACCEPTANCE' and r['id'].startswith(f'{family.upper()}-ACC-')])==equipment_count
    diag=asset(f'tem2_{family}_diagnostics.json')['scenarios']
    assert len(diag)==scenarios
    for s in diag:
        assert profile in s['applicability']['profiles']
        assert s['id'] in byid
        assert all(i in byid for i in s['equipmentRefs'])
        graph=s['graph']; node_map={n['id']:n for n in graph['nodes']}
        assert graph['startNodeId'] in node_map
        for n in graph['nodes']:
            destinations=[x['nextNodeId'] for x in n['choices']]
            if n.get('nextNodeId'): destinations.append(n['nextNodeId'])
            assert all(x in node_map for x in destinations), (s['id'],n['id'])
            if n['type']=='check':
                assert 'Ожидается:' in n['text'] and 'При отклонении:' in n['text']
    for route in ('route_from_outside','route_from_cab'):
        sequence=byid[f'{family.upper()}-ROUTE-{route}']['sequence']
        assert all(i in byid for i in sequence)
        assert len(sequence)==(14 if family=='tem2' else 16)+equipment_count
    assert all(family.upper() not in e['id'] or profile in e.get('app', [profile]) for e in rows)

flow=asset('tem2_stepwise_scheme_flows.json')['sequences']
layout=asset('tem2_scheme_layout_metadata.json')['layouts']
assert len(flow)==len(layout)==17
assert len({s['id'] for s in flow})==17
assert {x['sequenceId'] for x in layout}=={x['id'] for x in flow}
assert sum(len(n['nodes']) for n in layout)==91
assert len(stage2['systems'])==9 and len(stage2['equipment'])==36
assert len(stage2['schemes'])==9 and len(stage2['flows'])==11
assert sum(len(f[4]) for f in stage2['flows'])==42
assert len(stage3['scenarios'])==24 and len(stage4['coreChecks'])==14 and len(stage4['profileChecks'])==2

java=ROOT/'app/src/main/java/ru/railbrake/calculator'
for path in (
    'core/LocomotiveCatalogRegistry.kt','core/LocomotiveProfileContext.kt',
    'core/TechnicalDataRepository.kt','core/WorkingLocomotive.kt','core/Tem2DiagnosticRepository.kt',
    'core/assistant/AssistantContentLoader.kt','core/assistant/AssistantConversation.kt',
    'core/assistant/AssistantQueryParser.kt','core/assistant/AssistantEngine.kt',
    'ui/AssistantHomePanel.kt','ui/BrakeCalculatorApp.kt','ui/ErmakDiagnosticsScreen.kt',
    'ui/LocomotiveDiagnosticsScreen.kt','ui/StepwiseSchemeViewer.kt','ui/TechnicalCatalogScreen.kt'):
    app=(java/path).read_bytes()
    mirror=(ROOT/'patch/app/src/main/java/ru/railbrake/calculator'/path).read_bytes()
    assert app==mirror, f'app/patch mismatch: {path}'
print('TEM2_ANDROID_PROJECTION_PASS: profiles=2 systems=9 equipment=36 schemes=9 flows=11 steps=42 views=17 hotspots=91 diagnostics=22/24 acceptance=33/35 mirrors=OK')
