#!/usr/bin/env python3
from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump
from deepen_ermak_p0_protections_batch4 import ROUTES, transform


def main() -> None:
    app = load(APP_ASSET)
    patch = load(PATCH_ASSET)
    if app != patch:
        raise SystemExit("app/patch Ermak diagnostic assets differ before transformation")

    transformed = transform(app)
    by_id = {s["id"]: s for s in transformed.get("scenarios", [])}

    # Круговой огонь ТЭД — аварийный маршрут, а не обычный triage.
    flashover = by_id["ER-DIAG-101"]
    flashover["severity"] = "STOP_AND_REPORT"
    flashover.setdefault("display", {})["emergencyPriority"] = True
    flashover.setdefault("vl80sUiProjection", {})["severity"] = "STOP_AND_REPORT"

    # В ER-DIAG-102 старт сознательно разводит реальное боксование и сомнительный
    # канал скорости; общего выхода "это не тот сценарий" в графе нет. Убираем
    # общий служебный terminal, чтобы граф содержал только достижимые узлы.
    anti_slip = by_id["ER-DIAG-102"]
    anti_slip["graph"]["nodes"] = [
        node for node in anti_slip["graph"]["nodes"] if node.get("id") != "not-this-scenario"
    ]

    if len(transformed.get("scenarios", [])) != 136:
        raise SystemExit("Ermak scenario count changed")

    dump(APP_ASSET, transformed)
    dump(PATCH_ASSET, transformed)
    if APP_ASSET.read_bytes() != PATCH_ASSET.read_bytes():
        raise SystemExit("app/patch Ermak diagnostic assets differ after transformation")
    print("ERMAK_P0_PROTECTIONS_BATCH4=" + ",".join(sorted(ROUTES)))


if __name__ == "__main__":
    main()
