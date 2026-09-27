#!/usr/bin/env python3
from copy import deepcopy
from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump

TARGETS = {'ER-DIAG-012', 'ER-DIAG-013', 'ER-DIAG-014'}


def patch(root):
    root = deepcopy(root)
    by = {s['id']: s for s in root['scenarios']}
    for sid in TARGETS:
        s = by[sid]
        nodes = s['graph']['nodes']
        evidence = next(n for n in nodes if n['id'] == 'evidence')
        evidence['choices'][0]['nextNodeId'] = 'historical-gate'
        nodes.append({
            'id': 'historical-gate',
            'type': 'source_action',
            'text': 'Исторический источник 2010 года описывает ручной перевод QT1. По результатам аудита 26.09.2026 это не является разрешённым пользовательским действием; узел сохранён только для runtime-проверки safety-gate и требует отдельного актуального профильного подтверждения.',
            'riskClass': 'manual_power_apparatus',
            'userFacingPolicy': 'SAFETY_GATE_REQUIRED',
            'sourceBound': True,
            'nextNodeId': 'reassess',
        })
    return root


def main():
    app = load(APP_ASSET)
    mirror = load(PATCH_ASSET)
    if app != mirror:
        raise SystemExit('app/patch diagnostics differ before safety-gate patch')
    out = patch(app)
    dump(APP_ASSET, out)
    dump(PATCH_ASSET, out)
    if APP_ASSET.read_bytes() != PATCH_ASSET.read_bytes():
        raise SystemExit('app/patch diagnostics differ after safety-gate patch')
    print('ERMAK_LEGACY_BATCHA_SAFETY_GATE_PATCHED=' + ','.join(sorted(TARGETS)))


if __name__ == '__main__':
    main()
