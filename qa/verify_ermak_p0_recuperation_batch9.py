#!/usr/bin/env python3
import gzip,json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
TARGETS={f'ER-DIAG-{i:03d}' for i in range(57,65)}
GENERIC='Отказ локальный (одна секция/узел) или общий?'
TOKENS={
 'ER-DIAG-057':['QT1','K1','U3','KM13','KV15','KM41','KM42'],
 'ER-DIAG-058':['SP3','SP2','QF11','QF12','A6','A27','KM13'],
 'ER-DIAG-059':['SP3','ТЦ'],
 'ER-DIAG-060':['SP2','A14','ТМ'],
 'ER-DIAG-061':['KA7','KA15','U3'],
 'ER-DIAG-062':['R10','A6','KV01','KV02','M13','KM13'],
 'ER-DIAG-063':['юз','МСУД','пес'],
 'ER-DIAG-064':['SP3','ТЦ'],
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
by={s['id']:s for s in app['scenarios']}
assert TARGETS<=by.keys()

for sid in sorted(TARGETS):
    s=by[sid]; nodes=s['graph']['nodes']; ids={n['id'] for n in nodes}
    assert reachable(s)==ids,(sid,sorted(ids-reachable(s)))
    prompts=[n.get('prompt','') for n in nodes if n.get('type')=='question']
    assert GENERIC not in prompts,sid
    assert len(set(prompts))>=4,(sid,prompts)
    assert 'profile-required' in ids,sid
    assert not any(n.get('type')=='source_action' for n in nodes),sid
    pr=s.get('vl80sUiProjection',{})
    assert len(pr.get('probableCauses',[]))>=6,sid
    assert len(pr.get('checks',[]))>=3,sid
    assert len(pr.get('prohibited',[]))>=3,sid
    assert s.get('sourceAudit',{}).get('scope')=='p0_recuperation_batch9',sid
    assert s.get('sourceAudit',{}).get('status')=='IMPROVE',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    assert s.get('applicability',{}).get('profiles')==['base_early'],sid
    refs={r.get('sourceId'):r for r in s.get('sourceRefs',[])}
    assert 'ER-AUDIT-MFR-RE1-RECUP' in refs,sid
    assert 'ER-AUDIT-LEGACY-RECUP-MEMO' in refs,sid
    assert refs['ER-AUDIT-LEGACY-RECUP-MEMO'].get('version',{}).get('status')=='HISTORICAL',sid
    for r in s.get('sourceRefs',[]):
        if r.get('sourceId')=='ER-SRC-011':
            assert r.get('version',{}).get('status')=='HISTORICAL',sid
    text=json.dumps(s,ensure_ascii=False).lower()
    for token in TOKENS[sid]: assert token.lower() in text,(sid,token)

for sid in ('ER-DIAG-059','ER-DIAG-060','ER-DIAG-064'):
    refs={r.get('sourceId'):r for r in by[sid].get('sourceRefs',[])}
    assert refs['ER-AUDIT-NORM-BRAKES-2026']['version']['status']=='CURRENT_CONFIRMED',sid
    assert refs['ER-AUDIT-NORM-BRAKES-2026']['version']['effectiveFrom']=='2026-07-01',sid
    assert 'ER-AUDIT-NORM-3508R' in refs,sid

# Explicitly preserve uncertainty found during cross-checks.
t61=json.dumps(by['ER-DIAG-061'],ensure_ascii=False)
assert 'KA7/KA15' in t61
assert 'не считать ka7 или ka15 универсальным' in t61.lower()

t62=json.dumps(by['ER-DIAG-062'],ensure_ascii=False)
assert 'A6' in t62 and 'A27' in t62 and 'не смешивать' in t62.lower()

strict=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict}')
assert strict<=38,strict
print('ERMAK P0 RECUPERATION BATCH9 CONTRACT PASS')
