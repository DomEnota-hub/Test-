#!/usr/bin/env python3
"""Apply batch6 with all diagnostic comparison branches explicitly connected."""
from deepen_ermak_p0_brakes_batch6 import ROUTES, main


def point_finding(route_id: str, node_id: str, next_id: str) -> None:
    for node in ROUTES[route_id]["nodes"]:
        if node.get("id") == node_id:
            node["nextNodeId"] = next_id
            return
    raise SystemExit(f"missing node {route_id}:{node_id}")


# Symptoms outside the selected route explicitly return to scenario selection.
point_finding("ER-DIAG-082", "common", "not-this-scenario")
point_finding("ER-DIAG-083", "mechanical", "not-this-scenario")
point_finding("ER-DIAG-084", "indication", "not-this-scenario")

# Comparison branches are part of the diagnosis, not dead explanatory nodes.
# 086: when the automatic channel reaches the locomotive but execution is lost,
# compare the independent direct-acting channel before localizing common hardware.
point_finding("ER-DIAG-086", "local-control", "direct")

# 087: after establishing whether the reserve command is formed/executed,
# compare the normal channel to separate reserve-only from common brake failures.
point_finding("ER-DIAG-087", "execution", "normal")
point_finding("ER-DIAG-087", "control", "normal")

# 088: after locating command-generation versus pneumatic execution of
# substitution, compare ordinary pneumatic braking to isolate the interface.
point_finding("ER-DIAG-088", "pneumatic", "manual-brake")
point_finding("ER-DIAG-088", "interface", "manual-brake")

if __name__ == "__main__":
    main()
