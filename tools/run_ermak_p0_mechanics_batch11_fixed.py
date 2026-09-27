#!/usr/bin/env python3
"""Execute batch11 with an explicit hard-stop terminal injected in-memory.

The original batch11 transformer intentionally references ``stop-terminal`` for
PTE hard prohibitions. The shared batch1 terminal set has no such node, so this
wrapper adds it deterministically without editing source files at runtime.
Only the two diagnostic assets may be changed by the executed transformer.
"""
from __future__ import annotations

from pathlib import Path

SRC = Path(__file__).with_name('deepen_ermak_p0_mechanics_batch11.py')

IMPORT_OLD = (
    'from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, COMMON_TERMINALS, '
    'q, finding, info, apply_route, load, dump'
)
IMPORT_NEW = (
    'from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, COMMON_TERMINALS, '
    'q, finding, info, terminal, apply_route, load, dump'
)
MARKER = "ROUTES={}"
INSERT = """B11_TERMINALS=[
    terminal(
        'stop-terminal',
        'Достигнута действующая нормативная или аварийная граница STOP. Дальнейшая эксплуатация/движение не разрешается этим маршрутом; дальнейший порядок только по действующему source-bound документу и оперативному решению.',
        'safety_stop',
    ),
    *COMMON_TERMINALS,
]

ROUTES={}"""


def main() -> None:
    text = SRC.read_text(encoding='utf-8')
    if text.count(IMPORT_OLD) != 1:
        raise SystemExit('batch11 wrapper: unexpected import marker')
    if text.count(MARKER) != 1:
        raise SystemExit('batch11 wrapper: unexpected ROUTES marker')
    if '*COMMON_TERMINALS]' not in text:
        raise SystemExit('batch11 wrapper: terminal expansion marker missing')

    patched = text.replace(IMPORT_OLD, IMPORT_NEW, 1)
    patched = patched.replace(MARKER, INSERT, 1)
    patched = patched.replace('*COMMON_TERMINALS]', '*B11_TERMINALS]')

    namespace = {
        '__name__': '__main__',
        '__file__': str(SRC),
        '__package__': None,
    }
    exec(compile(patched, str(SRC), 'exec'), namespace, namespace)


if __name__ == '__main__':
    main()
