#!/usr/bin/env python3
from collections import deque

from deepen_ermak_p0_batch1 import APP_ASSET, PATCH_ASSET, load, dump
from deepen_ermak_p0_pneumatics_batch5 import ROUTES, transform


def prune_only_unused_route_back(scenario: dict) -> None:
    graph = scenario["graph"]
    nodes = {node["id"]: node for node in graph["nodes"]}
    queue = deque([graph["startNodeId"]])
    seen: set[str] = set()

    while queue:
        node_id = queue.popleft()
        if node_id in seen:
            continue
        if node_id not in nodes:
            raise SystemExit(f"{scenario['id']}: missing reachable node {node_id}")
        seen.add(node_id)
        node = nodes[node_id]
        next_id = node.get("nextNodeId")
        if next_id:
            queue.append(next_id)
        for choice in node.get("choices", []):
            target = choice.get("nextNodeId")
            if target:
                queue.append(target)

    unreachable = set(nodes) - seen
    unexpected = unreachable - {"not-this-scenario"}
    if unexpected:
        raise SystemExit(f"{scenario['id']}: unexpected unreachable nodes {sorted(unexpected)}")
    if "not-this-scenario" in unreachable:
        graph["nodes"] = [node for node in graph["nodes"] if node.get("id") != "not-this-scenario"]


def main() -> None:
    app = load(APP_ASSET)
    patch = load(PATCH_ASSET)
    if app != patch:
        raise SystemExit("app/patch Ermak diagnostic assets differ before transformation")

    transformed = transform(app)
    by_id = {scenario["id"]: scenario for scenario in transformed.get("scenarios", [])}
    for sid in sorted(ROUTES):
        prune_only_unused_route_back(by_id[sid])

    if len(transformed.get("scenarios", [])) != 136:
        raise SystemExit("Ermak scenario count changed")

    dump(APP_ASSET, transformed)
    dump(PATCH_ASSET, transformed)
    if APP_ASSET.read_bytes() != PATCH_ASSET.read_bytes():
        raise SystemExit("app/patch Ermak diagnostic assets differ after transformation")

    print("ERMAK_P0_PNEUMATICS_BATCH5=" + ",".join(sorted(ROUTES)))


if __name__ == "__main__":
    main()
