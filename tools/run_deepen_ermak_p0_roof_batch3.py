#!/usr/bin/env python3
"""Run roof batch 3 with the emergency-only ER-DIAG-030 graph pruned to reachable nodes."""
from deepen_ermak_p0_roof_batch3 import ROUTES, main

# ER-DIAG-030 is an emergency route: once the roof flash is confirmed it ends in
# stop_and_report. Profile-specific work belongs after the emergency procedure,
# not inside this diagnostic graph. Remove generic tail nodes that are therefore
# intentionally unreachable.
ROUTES["ER-DIAG-030"]["nodes"] = [
    node
    for node in ROUTES["ER-DIAG-030"]["nodes"]
    if node.get("id") not in {"profile", "profile-required", "reassess"}
]

if __name__ == "__main__":
    main()
