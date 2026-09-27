#!/usr/bin/env python3
from collections import deque
from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump

TARGETS={f'ER-DIAG-{i:03d}' for i in range(89,98)}

def reach(nodes,start='start'):
    by={n['id']:n for n in nodes}; q=deque([start]); seen=set()
    while q:
        x=q.popleft()
        if x in seen: continue
        if x not in by: raise SystemExit(f'missing node target {x}')
        seen.add(x); n=by[x]
        if n.get('nextNodeId'): q.append(n['nextNodeId'])
        q.extend(c['nextNodeId'] for c in n.get('choices',[]))
    return seen

def main():
    app=load(APP_ASSET); patch=load(PATCH_ASSET)
    if app!=patch: raise SystemExit('app/patch differ before reachability patch')
    by={s['id']:s for s in app['scenarios']}
    removed=[]
    for sid in sorted(TARGETS):
        nodes=by[sid]['graph']['nodes']; seen=reach(nodes,by[sid]['graph']['startNodeId'])
        unreachable=[n for n in nodes if n['id'] not in seen]
        bad=[n['id'] for n in unreachable if n.get('type')!='terminal']
        if bad: raise SystemExit(f'{sid}: unreachable non-terminal nodes {bad}')
        if unreachable:
            removed.extend(f"{sid}:{n['id']}" for n in unreachable)
            by[sid]['graph']['nodes']=[n for n in nodes if n['id'] in seen]
            pr=by[sid].setdefault('vl80sUiProjection',{})
            pr['questions']=[q for q in pr.get('questions',[]) if q.get('key') in seen]
    dump(APP_ASSET,app); dump(PATCH_ASSET,app)
    if APP_ASSET.read_bytes()!=PATCH_ASSET.read_bytes(): raise SystemExit('app/patch differ after reachability patch')
    print('ERMAK_BATCH7_PRUNED_UNREACHABLE='+(','.join(removed) if removed else 'none'))

if __name__=='__main__': main()
