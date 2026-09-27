#!/usr/bin/env python3
from pathlib import Path

src_path = Path('tools/deepen_ermak_final_content_block1.py')
source = src_path.read_text(encoding='utf-8')
old = """    seen=reachable(nodes)\n    missing=set(ids)-seen\n    if missing:\n        raise SystemExit(f\"{s['id']}: unreachable nodes {sorted(missing)}\")\n    s['graph']={'startNodeId':'start','nodes':nodes}\n"""
new = """    seen=reachable(nodes)\n    missing=set(ids)-seen\n    if missing:\n        by_id={n['id']:n for n in nodes}\n        non_terminal=sorted(x for x in missing if by_id.get(x,{}).get('type') != 'terminal')\n        if non_terminal:\n            raise SystemExit(f\"{s['id']}: unreachable non-terminal nodes {non_terminal}\")\n        nodes=[n for n in nodes if n['id'] not in missing]\n        print(f\"{s['id']}: pruned unreachable terminal nodes {sorted(missing)}\")\n    s['graph']={'startNodeId':'start','nodes':nodes}\n"""
if old not in source:
    raise SystemExit('final content transformer patch target not found')
patched = source.replace(old, new, 1)
exec(compile(patched, str(src_path), 'exec'), {'__name__':'__main__','__file__':str(src_path)})
