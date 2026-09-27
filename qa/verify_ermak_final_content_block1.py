#!/usr/bin/env python3
import gzip
import json
from collections import deque
from pathlib import Path

APP=Path('app/src/main/assets/technical/ermak_diagnostics.json.gz')
PATCH=Path('patch/app/src/main/assets/technical/ermak_diagnostics.json.gz')
GENERIC='Отказ локальный (одна секция/узел) или общий?'
FIRE={f'ER-DIAG-{i:03d}' for i in range(31,37)}
DEEP={f'ER-DIAG-{i:03d}' for i in range(123,129)} | {f'ER-DIAG-{i:03d}' for i in range(131,136)}
TARGETS=FIRE|DEEP
TOKENS={
 'ER-DIAG-123':['A23','SF29','SA5','SA6','U77','U80','U81'],
 'ER-DIAG-124':['пульт','МСУД','рабочей кабины','команда'],
 'ER-DIAG-125':['перекос','просадка','подвешив','безопасной остановки'],
 'ER-DIAG-126':['автосцеп','межсекцион','расцеплен','между секциями'],
 'ER-DIAG-127':['КТС-УАСП','шлейф','датчик','дым','огонь'],
 'ER-DIAG-128':['КТС-УАСП','пожаротуш','пуск','огнетушащ'],
 'ER-DIAG-131':['МСУД-Н','БУ-193','самоконтрол','межсекцион'],
 'ER-DIAG-132':['блок индикации помощника','основн','комплекс'],
 'ER-DIAG-133':['ограничител','перенапряж','ОПН','под напряжением'],
 'ER-DIAG-134':['ослаблен','возбужден','РОВ-21','ступен'],
 'ER-DIAG-135':['ДУК-4-01','угла коммутации','ВИП','канал'],
}

def load(p):
    with gzip.open(p,'rt',encoding='utf-8') as f:return json.load(f)

def reachable(s):
    nodes={n['id']:n for n in s['graph']['nodes']}
    q=deque([s['graph']['startNodeId']]); seen=set()
    while q:
        x=q.popleft()
        if x in seen: continue
        assert x in nodes,(s['id'],'missing',x)
        seen.add(x); n=nodes[x]
        if n.get('nextNodeId'): q.append(n['nextNodeId'])
        q.extend(c['nextNodeId'] for c in n.get('choices',[]))
    return seen

app=load(APP); patch=load(PATCH)
assert app==patch,'app/patch JSON differ'
assert APP.read_bytes()==PATCH.read_bytes(),'app/patch gzip bytes differ'
assert len(app['scenarios'])==136
by={s['id']:s for s in app['scenarios']}
assert len(by)==136 and TARGETS<=by.keys()

strict=[]
for s in app['scenarios']:
    if any(n.get('prompt')==GENERIC for n in s.get('graph',{}).get('nodes',[])):
        strict.append(s['id'])
assert not strict,strict
print('ERMAK_STRICT_GENERIC_ROUTES=0')

for sid in sorted(FIRE):
    s=by[sid]
    assert s.get('sourceAudit',{}).get('scope')=='final_content_completion',sid
    assert s.get('sourceAudit',{}).get('status')=='KEEP',sid
    refs={r.get('sourceId'):r for r in s.get('sourceRefs',[])}
    assert refs['ER-AUDIT-NORM-FIRE247R-FINAL']['version']['status']=='CURRENT_CONFIRMED',sid
    assert 'ER-AUDIT-MFR-RE1-FINAL' in refs,sid
    assert not any(n.get('type')=='source_action' for n in s.get('graph',{}).get('nodes',[])),sid
    text=json.dumps(s,ensure_ascii=False).lower()
    assert 'не выполнять пробный выпуск' in text,sid
    assert 'не обходить пожарные' in text,sid

for sid in sorted(DEEP):
    s=by[sid]
    nodes=s['graph']['nodes']; ids={n['id'] for n in nodes}
    assert s['graph']['startNodeId']=='start',sid
    assert len(ids)==len(nodes),sid
    assert reachable(s)==ids,(sid,sorted(ids-reachable(s)))
    assert not any(n.get('type')=='source_action' for n in nodes),sid
    prompts=[n.get('prompt','') for n in nodes if n.get('type')=='question']
    assert GENERIC not in prompts,sid
    assert len(set(prompts))>=3,(sid,prompts)
    assert s.get('sourceAudit',{}).get('scope')=='final_content_completion',sid
    assert s.get('sourceAudit',{}).get('status')=='IMPROVE',sid
    assert s.get('actions',[{}])[0].get('userFacingPolicy')=='TRIAGE_ONLY_NO_REPAIR',sid
    pr=s.get('vl80sUiProjection',{})
    assert len(pr.get('probableCauses',[]))>=6,sid
    assert len(pr.get('checks',[]))>=3,sid
    assert len(pr.get('prohibited',[]))>=3,sid
    text=json.dumps(s,ensure_ascii=False).lower()
    for token in TOKENS[sid]:
        assert token.lower() in text,(sid,token)

# Fire-system separation: real fire always routes away from ordinary fault diagnosis.
for sid in ('ER-DIAG-127','ER-DIAG-128'):
    s=by[sid]; text=json.dumps(s,ensure_ascii=False).lower()
    assert 'fire-terminal' in {n['id'] for n in s['graph']['nodes']},sid
    assert '№247/р' in text,sid

# Detection fault and suppression fault are deliberately different contracts.
text127=json.dumps(by['ER-DIAG-127'],ensure_ascii=False).lower()
assert 'шлейф' in text127 and 'датчик' in text127
assert 'дым' in text127 and 'огонь' in text127
text128=json.dumps(by['ER-DIAG-128'],ensure_ascii=False).lower()
assert 'пробн' in text128 and 'огнетушащ' in text128
assert 'пожаротуш' in text128 and 'пуск' in text128

# High-voltage/electronic granular routes stay triage-only and profile-gated.
for sid in ('ER-DIAG-131','ER-DIAG-132','ER-DIAG-133','ER-DIAG-134','ER-DIAG-135'):
    text=json.dumps(by[sid],ensure_ascii=False).lower()
    assert 'profile-required' in {n['id'] for n in by[sid]['graph']['nodes']},sid
    assert 'triage_only_no_repair'.lower() in text,sid

# Explicit safety properties of the last mechanical/fire scenarios.
text125=json.dumps(by['ER-DIAG-125'],ensure_ascii=False).lower()
assert 'не назначать допустимую скорость' in text125
text126=json.dumps(by['ER-DIAG-126'],ensure_ascii=False).lower()
assert 'не входить между секциями' in text126 and 'stop-terminal' in text126
text133=json.dumps(by['ER-DIAG-133'],ensure_ascii=False).lower()
assert 'не осматривать/измерять опн под напряжением' in text133
text134=json.dumps(by['ER-DIAG-134'],ensure_ascii=False).lower()
assert 'не включать аппараты ступеней вручную' in text134
text135=json.dumps(by['ER-DIAG-135'],ensure_ascii=False).lower()
assert 'не измерять цепи дук' in text135

# No exact duplicate question-tree signatures among the newly rebuilt routes.
sigs={}
for sid in sorted(DEEP):
    qs=tuple(n.get('prompt','') for n in by[sid]['graph']['nodes'] if n.get('type')=='question')
    assert qs not in sigs,(sid,sigs.get(qs))
    sigs[qs]=sid

print('ERMAK_FINAL_CONTENT_BLOCK1=17')
print('ERMAK_FINAL_CONTENT_BLOCK1_CONTRACT_PASS')
