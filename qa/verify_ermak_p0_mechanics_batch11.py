#!/usr/bin/env python3
import gzip,json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
TARGETS={f'ER-DIAG-{i:03d}' for i in range(103,111)}
GENERIC='Отказ локальный (одна секция/узел) или общий?'
TOKENS={
 'ER-DIAG-103':['SF30','SA3','S30','A55','KV16','У11','У14'],
 'ER-DIAG-104':['80 °C','скольжения','качения','МОП'],
 'ER-DIAG-105':['80 °C','буксов'],
 'ER-DIAG-106':['80 °C','зубчат','смазк'],
 'ER-DIAG-107':['80 °C','ползун','бандаж','КМБ'],
 'ER-DIAG-108':['15 км/ч','10 км/ч','>4 мм','110 км/ч'],
 'ER-DIAG-109':['контрольной отмет','30 %','100 мм'],
 'ER-DIAG-110':['экстренным торможением','полной остановки','ДСП','ДНЦ'],
}

def load(p):
    with gzip.open(p,'rt',encoding='utf-8') as f:return json.load(f)

def reachable(s):
    nodes={n['id']:n for n in s['graph']['nodes']}; q=deque([s['graph']['startNodeId']]); seen=set()
    while q:
        x=q.popleft()
        if x in seen: continue
        assert x in nodes,(s['id'],x)
        seen.add(x); n=nodes[x]
        if n.get('nextNodeId'): q.append(n['nextNodeId'])
        q.extend(c['nextNodeId'] for c in n.get('choices',[]))
    return seen

def assert_acyclic(s):
    nodes={n['id']:n for n in s['graph']['nodes']}; state={}
    def visit(node_id):
        assert state.get(node_id)!=1,(s['id'],'cycle',node_id)
        if state.get(node_id)==2:return
        state[node_id]=1; node=nodes[node_id]
        targets=([node['nextNodeId']] if node.get('nextNodeId') else [])+[c['nextNodeId'] for c in node.get('choices',[])]
        for target in targets: visit(target)
        state[node_id]=2
    visit(s['graph']['startNodeId'])

app=load(APP); patch=load(PATCH)
assert app==patch
assert len(app['scenarios'])==136
by={s['id']:s for s in app['scenarios']}; assert TARGETS<=by.keys()
for sid in sorted(TARGETS):
    s=by[sid]; ids={n['id'] for n in s['graph']['nodes']}
    assert reachable(s)==ids,(sid,sorted(ids-reachable(s)))
    assert_acyclic(s)
    prompts=[n.get('prompt','') for n in s['graph']['nodes'] if n.get('type')=='question']
    assert GENERIC not in prompts,sid
    assert len(set(prompts))>=3,(sid,prompts)
    assert not any(n.get('type')=='source_action' for n in s['graph']['nodes']),sid
    pr=s.get('vl80sUiProjection',{})
    assert len(pr.get('probableCauses',[]))>=6,sid
    assert len(pr.get('checks',[]))>=3,sid
    assert len(pr.get('prohibited',[]))>=3,sid
    assert s.get('sourceAudit',{}).get('scope')=='p0_mechanics_batch11',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    text=json.dumps(s,ensure_ascii=False).lower()
    for token in TOKENS[sid]: assert token.lower() in text,(sid,token)

# Current PTE is primary for operational mechanical limits.
for sid in ('ER-DIAG-104','ER-DIAG-105','ER-DIAG-106','ER-DIAG-107','ER-DIAG-108','ER-DIAG-109','ER-DIAG-110'):
    refs={r.get('sourceId'):r for r in by[sid].get('sourceRefs',[])}
    assert refs['ER-AUDIT-NORM-PTE250-MECH-B11']['version']['status']=='CURRENT_CONFIRMED',sid

# >80C is a hard PTE stop for axlebox/MOP/reducer support bearings.
# Accept both normative wording styles used in the graph: "запрещено" and
# the hard-stop terminal's equivalent "не разрешается".
for sid in ('ER-DIAG-104','ER-DIAG-105','ER-DIAG-106','ER-DIAG-107'):
    t=json.dumps(by[sid],ensure_ascii=False).lower()
    assert '>80 °c' in t or 'свыше 80 °c' in t,(sid,'80C stop')
    assert ('запрещ' in t or 'не разреш' in t),(sid,'operational prohibition')

# Exact locomotive flat ranges from PTE p.155; do not mix wagon limits.
t108=json.dumps(by['ER-DIAG-108'],ensure_ascii=False).lower()
for x in ('>1 до 2 мм','15 км/ч','>2 до 4 мм','10 км/ч','>4 мм','вывешив'):
    assert x.lower() in t108,x
assert 'не применять вагонные пределы' in t108

# Band loosening/shift is a direct prohibition, not a made-up low-speed route.
t109=json.dumps(by['ER-DIAG-109'],ensure_ascii=False).lower()
assert 'запрещают эксплуатацию' in t109
assert 'сдвиг контрольной отмет' in t109
assert 'не назначать пониженную скорость без нормативного основания' in t109

# Shock vs derailment remains two different branches.
t110=json.dumps(by['ER-DIAG-110'],ensure_ascii=False).lower()
assert 'экстренным торможением' in t110
assert 'торможения до полной остановки' in t110
refs110={r.get('sourceId'):r for r in by['ER-DIAG-110'].get('sourceRefs',[])}
assert refs110['ER-AUDIT-NORM-2580R-MECH-B11']['version']['status']=='REQUIRES_REVIEW'

strict=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict}')
assert strict<=21,strict
print('ERMAK P0 MECHANICS BATCH11 CONTRACT PASS')
