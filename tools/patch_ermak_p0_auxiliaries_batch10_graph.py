#!/usr/bin/env python3
from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump

TARGET='ER-DIAG-069'

def main():
    data=load(APP_ASSET); patch=load(PATCH_ASSET)
    if data!=patch: raise SystemExit('app/patch differ before batch10 graph patch')
    by={s['id']:s for s in data['scenarios']}
    s=by[TARGET]
    nodes={n['id']:n for n in s['graph']['nodes']}
    for nid in ('mechanical','electrical'):
        if nid not in nodes: raise SystemExit(f'{TARGET}: missing {nid}')
        nodes[nid]['nextNodeId']='heat'
    if 'heat' not in nodes: raise SystemExit(f'{TARGET}: missing heat')
    dump(APP_ASSET,data); dump(PATCH_ASSET,data)
    if APP_ASSET.read_bytes()!=PATCH_ASSET.read_bytes(): raise SystemExit('app/patch differ after batch10 graph patch')
    print('ERMAK_BATCH10_GRAPH_PATCH=ER-DIAG-069:mechanical/electrical->heat')

if __name__=='__main__':
    main()
