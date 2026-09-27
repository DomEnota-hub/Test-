#!/usr/bin/env python3
import gzip, json
from collections import deque
from pathlib import Path

APP = Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH = Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
TARGETS = {'ER-DIAG-001'} | {f'ER-DIAG-{i:03d}' for i in range(6,15)} | {f'ER-DIAG-{i:03d}' for i in range(16,30)}
SAFETY_GATE_TARGETS = {'ER-DIAG-012','ER-DIAG-013','ER-DIAG-014'}
GENERIC = 'Отказ локальный (одна секция/узел) или общий?'
TOKENS = {
 'ER-DIAG-001': ['F1','F2','SA1','SA2'],
 'ER-DIAG-006': ['SF22','QF1-UA2'],
 'ER-DIAG-007': ['F37','QF11','QF12'],
 'ER-DIAG-008': ['QF11','QF12'],
 'ER-DIAG-009': ['QF11','QF12'],
 'ER-DIAG-010': ['KM41','KM42'],
 'ER-DIAG-011': ['KM41','KM42'],
 'ER-DIAG-012': ['Э6','QT1'],
 'ER-DIAG-013': ['Н36','QT1'],
 'ER-DIAG-014': ['Н37','QT1'],
 'ER-DIAG-016': ['A73','A74'],
 'ER-DIAG-017': ['SF45','МСУД'],
 'ER-DIAG-018': ['Q6','QS28'],
 'ER-DIAG-019': ['Q6','QS27','QS28'],
 'ER-DIAG-020': ['SF25'],
 'ER-DIAG-021': ['SF27','ТРТ'],
 'ER-DIAG-022': ['KK14','KM14'],
 'ER-DIAG-023': ['SF26','S11'],
 'ER-DIAG-024': ['SF26','S12'],
 'ER-DIAG-025': ['SF26','S17'],
 'ER-DIAG-026': ['KM11'],
 'ER-DIAG-027': ['KM12'],
 'ER-DIAG-028': ['KK11','KM11'],
 'ER-DIAG-029': ['KK12','KM12'],
}

def load(path):
    with gzip.open(path,'rt',encoding='utf-8') as f:return json.load(f)

app=load(APP); patch=load(PATCH)
assert app==patch, 'app/patch diagnostics differ'
assert len(app['scenarios'])==136
by={s['id']:s for s in app['scenarios']}
assert TARGETS <= by.keys()
for sid in sorted(TARGETS):
    s=by[sid]
    nodes=s['graph']['nodes']; ids={n['id'] for n in nodes}
    assert s['graph']['startNodeId']=='start'
    assert len(ids)==len(nodes)
    source_actions=[n for n in nodes if n.get('type')=='source_action']
    if sid in SAFETY_GATE_TARGETS:
        assert len(source_actions)==1, (sid,source_actions)
        gate=source_actions[0]
        assert gate.get('id')=='historical-gate', sid
        assert gate.get('userFacingPolicy')=='SAFETY_GATE_REQUIRED', sid
        assert gate.get('riskClass')=='manual_power_apparatus', sid
        assert gate.get('sourceBound') is True, sid
        assert 'не является разрешённым пользовательским действием' in gate.get('text',''), sid
        assert gate.get('nextNodeId')=='reassess', sid
    else:
        assert not source_actions, sid
    qs=[n.get('prompt','') for n in nodes if n.get('type')=='question']
    assert GENERIC not in qs, sid
    assert len(set(qs))>=4, (sid,qs)
    assert 'profile-required' in ids
    for n in nodes:
        if n.get('nextNodeId'): assert n['nextNodeId'] in ids,(sid,n)
        for c in n.get('choices',[]): assert c['nextNodeId'] in ids,(sid,c)
    seen=set(); dq=deque(['start'])
    while dq:
        x=dq.popleft()
        if x in seen: continue
        seen.add(x); n=next(n for n in nodes if n['id']==x)
        if n.get('nextNodeId'): dq.append(n['nextNodeId'])
        dq.extend(c['nextNodeId'] for c in n.get('choices',[]))
    assert seen==ids,(sid,sorted(ids-seen))
    pr=s.get('vl80sUiProjection',{})
    assert len(pr.get('probableCauses',[]))>=4,sid
    assert len(pr.get('checks',[]))>=3,sid
    assert len(pr.get('prohibited',[]))>=3,sid
    assert s.get('sourceAudit',{}).get('status')=='IMPROVE',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    assert any(r.get('sourceId')=='ER-AUDIT-NORM-996R-2024' for r in s.get('sourceRefs',[])),sid
    old=[r for r in s.get('sourceRefs',[]) if r.get('sourceId')=='ER-SRC-010']
    assert old and old[0].get('version',{}).get('status')=='HISTORICAL',sid
    text=json.dumps(s,ensure_ascii=False)
    for token in TOKENS[sid]: assert token in text,(sid,token)

for sid in TARGETS:
    s=by[sid]
    canonical_text=' '.join(
        [n.get('prompt','')+' '+n.get('result','') for n in s['graph']['nodes'] if n.get('type')!='source_action'] +
        [a.get('summary','') for a in s.get('actions',[])]
    ).lower()
    assert 'нажав рукой' not in canonical_text, sid
    assert 'перевести тормозные переключатели qt1 в режим «тяга» вручную' not in canonical_text, sid
    assert 'поочередно включая секции определить' not in canonical_text, sid

strict_generic=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict_generic}')
assert strict_generic <= 65
print('ERMAK LEGACY BATCH A CONTRACT PASS')
