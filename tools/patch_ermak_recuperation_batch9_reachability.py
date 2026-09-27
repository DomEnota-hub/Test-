#!/usr/bin/env python3
from collections import deque
from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump

TARGETS={f'ER-DIAG-{i:03d}' for i in range(57,65)}

def reachable(nodes,start):
    by={n['id']:n for n in nodes}; q=deque([start]); seen=set()
    while q:
        node_id=q.popleft()
        if node_id in seen: continue
        if node_id not in by: raise SystemExit(f'missing node target {node_id}')
        seen.add(node_id); node=by[node_id]
        if node.get('nextNodeId'): q.append(node['nextNodeId'])
        q.extend(c['nextNodeId'] for c in node.get('choices',[]))
    return seen

def main():
    app=load(APP_ASSET); patch=load(PATCH_ASSET)
    if app!=patch: raise SystemExit('app/patch differ before batch9 reachability patch')
    by={s['id']:s for s in app['scenarios']}; removed=[]
    for sid in sorted(TARGETS):
        s=by[sid]; nodes=s['graph']['nodes']; seen=reachable(nodes,s['graph']['startNodeId'])
        unreachable=[n for n in nodes if n['id'] not in seen]
        non_terminal=[n['id'] for n in unreachable if n.get('type')!='terminal']
        if non_terminal: raise SystemExit(f'{sid}: unreachable non-terminal nodes {non_terminal}')
        if unreachable:
            removed.extend(f"{sid}:{n['id']}" for n in unreachable)
            s['graph']['nodes']=[n for n in nodes if n['id'] in seen]
            projection=s.setdefault('vl80sUiProjection',{})
            projection['questions']=[q for q in projection.get('questions',[]) if q.get('key') in seen]
    dump(APP_ASSET,app); dump(PATCH_ASSET,app)
    if APP_ASSET.read_bytes()!=PATCH_ASSET.read_bytes(): raise SystemExit('app/patch mismatch after batch9 reachability patch')
    print('ERMAK_BATCH9_PRUNED_UNREACHABLE='+(','.join(removed) if removed else 'none'))

if __name__=='__main__': main()
