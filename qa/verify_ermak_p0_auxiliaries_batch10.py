#!/usr/bin/env python3
import gzip,json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
TARGETS={f'ER-DIAG-{i:03d}' for i in range(65,74)}
GENERIC='Отказ локальный (одна секция/узел) или общий?'
TOKENS={
 'ER-DIAG-065':['M15','KM15','KM9','SK11','KV47','S18'],
 'ER-DIAG-066':['M15','KM15','KM9','U5','SK10'],
 'ER-DIAG-067':['M11','M12','KM7','KM8','KM11','KM12','ВС1','ВС2'],
 'ER-DIAG-068':['M11','M12','ВИП','ВС1','ВС2'],
 'ER-DIAG-069':['M13','KM13','R10'],
 'ER-DIAG-070':['U5','KM7','KM8','KM9','KM11','KM12','KM15'],
 'ER-DIAG-071':['M35','SP7','KO3','KP3'],
 'ER-DIAG-072':['A50','KM36','F23','F24','E3','E4','KV51','KV56'],
 'ER-DIAG-073':['M14','KM14','SP6','KV48','KO7','KP1','KP2'],
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
    assert 'profile-required' in ids and 'reassess' in ids and 'not-this-scenario' in ids,sid
    assert not any(n.get('type')=='source_action' for n in nodes),sid
    pr=s.get('vl80sUiProjection',{})
    assert len(pr.get('probableCauses',[]))>=6,sid
    assert len(pr.get('checks',[]))>=3,sid
    assert len(pr.get('prohibited',[]))>=3,sid
    assert s.get('sourceAudit',{}).get('scope')=='p0_auxiliaries_batch10',sid
    assert s.get('sourceAudit',{}).get('status')=='IMPROVE',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    assert s.get('applicability',{}).get('profiles')==['base_early'],sid
    refs={r.get('sourceId'):r for r in s.get('sourceRefs',[])}
    assert 'ER-AUDIT-MFR-RE1-AUX' in refs,sid
    assert 'ER-AUDIT-LEGACY-AUX-B10' in refs,sid
    assert refs['ER-AUDIT-LEGACY-AUX-B10'].get('version',{}).get('status')=='HISTORICAL',sid
    text=json.dumps(s,ensure_ascii=False).lower()
    for token in TOKENS[sid]: assert token.lower() in text,(sid,token)

# 065: cold-oil non-start can be a normal profile interlock, not automatically a pump fault.
t65=json.dumps(by['ER-DIAG-065'],ensure_ascii=False).lower()
assert 'штатной блокиров' in t65 and 'sk11/kv47' in t65
assert 'не считать отсутствие пуска на холодном масле отказом' in t65

# 067: explicit factory prohibition against switching U5 into running 50 Hz fans must survive.
t67=json.dumps(by['ER-DIAG-067'],ensure_ascii=False).lower()
assert 'не включать u5 при работающих m11/m12 на нормальной частоте' in t67

# 072: do not collapse AC, calorifers and panel heaters into one cause.
t72=json.dumps(by['ER-DIAG-072'],ensure_ascii=False).lower()
assert 'кондиционер' in t72 and 'калорифер' in t72 and 'панель' in t72
assert 'km36' in t72 and 'kv51' in t72 and 'km21' in t72

# 073 gets current brake safety cross-check because PM depletion can affect braking.
refs73={r.get('sourceId'):r for r in by['ER-DIAG-073'].get('sourceRefs',[])}
assert refs73['ER-AUDIT-NORM-BRAKES-2026-B10']['version']['status']=='CURRENT_CONFIRMED'
assert refs73['ER-AUDIT-NORM-BRAKES-2026-B10']['version']['effectiveFrom']=='2026-07-01'

strict=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict}')
assert strict<=29,strict
print('ERMAK P0 AUXILIARIES BATCH10 CONTRACT PASS')
