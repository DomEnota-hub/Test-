#!/usr/bin/env python3
from collections import deque
from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump

TARGETS={f'ER-DIAG-{i:03d}' for i in range(103,111)}

def reach(s):
    by={n['id']:n for n in s['graph']['nodes']}; q=deque([s['graph']['startNodeId']]); seen=set()
    while q:
        x=q.popleft()
        if x in seen: continue
        if x not in by: raise SystemExit(f"{s['id']}: missing node {x}")
        seen.add(x); n=by[x]
        if n.get('nextNodeId'): q.append(n['nextNodeId'])
        q.extend(c['nextNodeId'] for c in n.get('choices',[]))
    return seen

def main():
    data=load(APP_ASSET); patch=load(PATCH_ASSET)
    if data!=patch: raise SystemExit('app/patch differ before batch11 reachability patch')
    by={s['id']:s for s in data['scenarios']}; removed=[]
    for sid in sorted(TARGETS):
        s=by[sid]; seen=reach(s); nodes=s['graph']['nodes']; unreachable=[n for n in nodes if n['id'] not in seen]
        bad=[n['id'] for n in unreachable if n.get('type')!='terminal']
        if bad: raise SystemExit(f'{sid}: unreachable non-terminal nodes: {bad}')
        for n in unreachable: removed.append(f"{sid}:{n['id']}")
        s['graph']['nodes']=[n for n in nodes if n['id'] in seen]
    dump(APP_ASSET,data); dump(PATCH_ASSET,data)
    if APP_ASSET.read_bytes()!=PATCH_ASSET.read_bytes(): raise SystemExit('app/patch differ after patch')
    print('ERMAK_BATCH11_PRUNED_UNREACHABLE='+(','.join(removed) if removed else 'none'))

if __name__=='__main__': main()
