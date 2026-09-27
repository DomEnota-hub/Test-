#!/usr/bin/env python3
import gzip, json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
EARLY={'ER-DIAG-037','ER-DIAG-038','ER-DIAG-039','ER-DIAG-042','ER-DIAG-043','ER-DIAG-044'}
LATE={'ER-DIAG-045','ER-DIAG-046','ER-DIAG-047','ER-DIAG-048'}
TARGETS=EARLY|LATE
GENERIC='Отказ локальный (одна секция/узел) или общий?'
TOKENS={
 'ER-DIAG-037':['А25','KM5','F3','F16','A64'],
 'ER-DIAG-038':['СИ СЕТЬ','T19','T20','ГВ'],
 'ER-DIAG-039':['СИ САУТ','САУТ'],
 'ER-DIAG-042':['ДПС','SF89','SF91'],
 'ER-DIAG-043':['SF86','телеметр'],
 'ER-DIAG-044':['A64','SF45','SF46'],
 'ER-DIAG-045':['МСУД-015','CAN'],
 'ER-DIAG-046':['боксован','огранич'],
 'ER-DIAG-047':['асимметр','алгоритм','момент'],
 'ER-DIAG-048':['ДПС','боксован','защит'],
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
assert app==patch,'app/patch diagnostics differ'
assert len(app['scenarios'])==136
by={s['id']:s for s in app['scenarios']}
assert TARGETS<=by.keys()

for sid in sorted(TARGETS):
    s=by[sid]; nodes=s['graph']['nodes']; ids={n['id'] for n in nodes}
    assert s['graph']['startNodeId']=='start',sid
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
    assert s.get('sourceAudit',{}).get('scope')=='p0_msud_batch8',sid
    assert s.get('sourceAudit',{}).get('status')=='IMPROVE',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    text=json.dumps(s,ensure_ascii=False).lower()
    for token in TOKENS[sid]: assert token.lower() in text,(sid,token)

for sid in EARLY:
    s=by[sid]
    refs={r.get('sourceId'):r for r in s.get('sourceRefs',[])}
    assert 'ER-AUDIT-MFR-RE1-PUBLIC' in refs,sid
    assert 'ER-AUDIT-LEGACY-MSUD-MEMO' in refs,sid
    assert refs['ER-AUDIT-LEGACY-MSUD-MEMO'].get('version',{}).get('status')=='HISTORICAL',sid
    assert s.get('applicability',{}).get('profiles')==['base_early'],sid
    for r in s.get('sourceRefs',[]):
        if r.get('sourceId')=='ER-SRC-011':
            assert r.get('version',{}).get('status')=='HISTORICAL',sid

for sid in LATE:
    s=by[sid]
    refs={r.get('sourceId'):r for r in s.get('sourceRefs',[])}
    assert 'ER-AUDIT-MSUD015-TECH-2022' in refs,sid
    assert 'ER-AUDIT-MSUD015-OPS-2026' in refs,sid
    assert refs['ER-AUDIT-MSUD015-OPS-2026'].get('version',{}).get('status')=='CURRENT_SECONDARY',sid
    assert s.get('applicability',{}).get('variantSelectionRequired') is True,sid
    assert 'МСУД-015' in s.get('sourceAgeNote',''),sid

# The open early-profile sources disagree on the exact breaker designation for speed sensors.
# The route must preserve that uncertainty instead of universalising one number.
t42=json.dumps(by['ER-DIAG-042'],ensure_ascii=False)
assert 'SF89/SF91' in t42
assert 'не считать универсальным' in t42.lower() or 'не считать универсальное' in t42.lower() or 'не считать универс' in t42.lower()

strict=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict}')
assert strict<=46,strict
print('ERMAK P0 MSUD BATCH8 CONTRACT PASS')
