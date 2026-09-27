#!/usr/bin/env python3
import gzip,json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
TARGETS={'ER-DIAG-111','ER-DIAG-112','ER-DIAG-113','ER-DIAG-114','ER-DIAG-116','ER-DIAG-117','ER-DIAG-118','ER-DIAG-119','ER-DIAG-120','ER-DIAG-121'}
GENERIC='Отказ локальный (одна секция/узел) или общий?'
TOKENS={
 'ER-DIAG-111':['A60','SF35','SF28','S37','S38','Z2','761/р'],
 'ER-DIAG-112':['У17','У18','SF30','S58','S59'],
 'ER-DIAG-113':['S39','SF37','KM45','U82','U83','A42'],
 'ER-DIAG-114':['A120','A121','A122','A123','SA7','KT11'],
 'ER-DIAG-116':['PV1','T12','U21','ER-DIAG-129'],
 'ER-DIAG-117':['PJ1','T7','T12','U21'],
 'ER-DIAG-118':['спутников','base_early','БЛОК'],
 'ER-DIAG-119':['радиоканал','A60','base_early','БЛОК'],
 'ER-DIAG-120':['У30','КН45','КН46','гребн'],
 'ER-DIAG-121':['ручн','стояночн','винтов'],
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

app=load(APP); patch=load(PATCH)
assert app==patch
assert len(app['scenarios'])==136
by={s['id']:s for s in app['scenarios']}; assert TARGETS<=by.keys()
for sid in sorted(TARGETS):
    s=by[sid]; ids={n['id'] for n in s['graph']['nodes']}
    assert reachable(s)==ids,(sid,sorted(ids-reachable(s)))
    prompts=[n.get('prompt','') for n in s['graph']['nodes'] if n.get('type')=='question']
    assert GENERIC not in prompts,sid
    assert len(set(prompts))>=3,(sid,prompts)
    assert not any(n.get('type')=='source_action' for n in s['graph']['nodes']),sid
    pr=s.get('vl80sUiProjection',{})
    assert len(pr.get('probableCauses',[]))>=6,sid
    assert len(pr.get('checks',[]))>=3,sid
    assert len(pr.get('prohibited',[]))>=3,sid
    assert s.get('sourceAudit',{}).get('scope')=='p0_cab_safety_batch12',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    text=json.dumps(s,ensure_ascii=False).lower()
    for token in TOKENS[sid]: assert token.lower() in text,(sid,token)

# Current PTE hard operational prohibitions.
for sid in ('ER-DIAG-111','ER-DIAG-112','ER-DIAG-114','ER-DIAG-121'):
    t=json.dumps(by[sid],ensure_ascii=False).lower()
    assert 'птэ №250' in t or 'птэ' in t,sid
    assert ('запрещ' in t or 'не разреш' in t),sid
    refs={r.get('sourceId'):r for r in by[sid].get('sourceRefs',[])}
    assert refs['ER-AUDIT-NORM-PTE250-CAB-B12']['version']['status']=='CURRENT_CONFIRMED',sid

# Radio must use current 761/r, not obsolete 127r as operational basis.
refs111={r.get('sourceId'):r for r in by['ER-DIAG-111'].get('sourceRefs',[])}
assert refs111['ER-AUDIT-NORM-761R-RADIO-B12']['version']['status']=='CURRENT_CONFIRMED'
t111=json.dumps(by['ER-DIAG-111'],ensure_ascii=False).lower()
assert '№761/р' in t111 and 'местной инструкц' in t111

# Safety-complex channel loss is not automatically a whole-device failure.
for sid in ('ER-DIAG-118','ER-DIAG-119'):
    t=json.dumps(by[sid],ensure_ascii=False).lower()
    assert 'profile' in t or 'профил' in t
    assert 'обязательн' in t and 'safety' in t
    assert 'не' in t and ('автомат' in t or 'только' in t)
    assert 'profile-terminal' in {n['id'] for n in by[sid]['graph']['nodes']}

# 114 must split common power, one wiper and washer; 120 command/air/lubricant/nozzle.
t114=json.dumps(by['ER-DIAG-114'],ensure_ascii=False).lower()
assert 'оба стеклоочистителя' in t114 and 'один a122/a123' in t114 and 'омыватель a121' in t114
t120=json.dumps(by['ER-DIAG-120'],ensure_ascii=False).lower()
for x in ('команд','воздух','смазоч','форсунк'):
    assert x in t120,x

# Manual/parking brake is hard-stop and current 2026 brake rules are attached.
refs121={r.get('sourceId'):r for r in by['ER-DIAG-121'].get('sourceRefs',[])}
assert refs121['ER-AUDIT-NORM-BRAKES-2026-CAB-B12']['version']['status']=='CURRENT_CONFIRMED'
assert refs121['ER-AUDIT-NORM-BRAKES-2026-CAB-B12']['version']['effectiveFrom']=='2026-07-01'

strict=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict}')
assert strict<=11,strict
print('ERMAK P0 CAB/SAFETY BATCH12 CONTRACT PASS')
