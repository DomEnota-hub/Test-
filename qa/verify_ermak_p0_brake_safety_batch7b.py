#!/usr/bin/env python3
import gzip,json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
TARGETS={'ER-DIAG-090','ER-DIAG-091','ER-DIAG-092'}
GENERIC='Отказ локальный (одна секция/узел) или общий?'

def load(p):
    with gzip.open(p,'rt',encoding='utf-8') as f:return json.load(f)

def reachable(s):
    nodes={n['id']:n for n in s['graph']['nodes']}; q=deque([s['graph']['startNodeId']]); seen=set()
    while q:
        x=q.popleft()
        if x in seen: continue
        assert x in nodes,(s['id'],x); seen.add(x); n=nodes[x]
        if n.get('nextNodeId'): q.append(n['nextNodeId'])
        q.extend(c['nextNodeId'] for c in n.get('choices',[]))
    return seen

app=load(APP); patch=load(PATCH)
assert app==patch
assert len(app['scenarios'])==136
by={s['id']:s for s in app['scenarios']}
for sid in TARGETS:
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
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    assert s.get('sourceAudit',{}).get('status')=='IMPROVE',sid
    br=[r for r in s.get('sourceRefs',[]) if r.get('sourceId')=='ER-AUDIT-NORM-BRAKES-2026']
    assert br and br[0].get('version',{}).get('status')=='CURRENT_CONFIRMED',sid
    assert br[0].get('version',{}).get('effectiveFrom')=='2026-07-01',sid
    assert '01.07.2026' in s.get('sourceAgeNote',''),sid

p90=' '.join(n.get('prompt','')+' '+n.get('text','') for n in by['ER-DIAG-090']['graph']['nodes']).lower()
assert 'тормоз' in p90 and ('магистрал' in p90 or 'тм' in p90) and ('датчик' in p90 or 'индикац' in p90)
assert any(r.get('sourceId')=='ER-AUDIT-NORM-PTE-250' for r in by['ER-DIAG-090']['sourceRefs'])

p91=' '.join(n.get('prompt','')+' '+n.get('text','') for n in by['ER-DIAG-091']['graph']['nodes']).lower()
assert ('зарядн' in p91 or 'сверхзаряд' in p91) and ('ур' in p91 or 'уравнитель' in p91) and ('датчик' in p91 or 'бто' in p91)

p92=' '.join(n.get('prompt','')+' '+n.get('text','') for n in by['ER-DIAG-092']['graph']['nodes']).lower()
assert 'рукав' in p92 and ('давлен' in p92 or 'кран' in p92) and ('провер' in p92 or 'опробован' in p92)
assert not any(n.get('userFacingPolicy')=='SAFETY_FIRST' for n in by['ER-DIAG-092']['graph']['nodes'])
assert next(n for n in by['ER-DIAG-092']['graph']['nodes'] if n['id']=='safety-stop')['type']=='info'
assert any(r.get('sourceId')=='ER-AUDIT-SAFETY-2961R' for r in by['ER-DIAG-092']['sourceRefs'])
assert any(r.get('sourceId')=='ER-AUDIT-NORM-PTE-250' for r in by['ER-DIAG-092']['sourceRefs'])

strict=sum(any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])) for s in app['scenarios'])
print(f'ERMAK_STRICT_GENERIC_ROUTES={strict}')
assert strict<=56,strict
print('ERMAK P0 BRAKE SAFETY BATCH7B CONTRACT PASS')
