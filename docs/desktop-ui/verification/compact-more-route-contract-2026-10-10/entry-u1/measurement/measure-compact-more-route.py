#!/usr/bin/env python3
"""Verify the compact (600dp) route-entry contract from a TmpCompactDrawerProbeTest log.

Usage: python3 measure-compact-more-route.py [probe.log]
Exit code 0 means every observed route matched the contract; 1 lists the mismatches.
"""
import re
import sys
from pathlib import Path

DEFAULT_LOG = Path(__file__).resolve().parents[1] / "logs" / "harness-u1-compact-probe.log"

# step -> (must_be_present, must_be_absent)
CONTRACT = {
    "initial(account)": (set(), {"navigator-overlay", "workspace-navigator", "plugins-search"}),
    # The Workspace route keeps its drawer open above the content at compact width.
    "initial-Harness": ({"navigator-overlay", "navigator-scrim", "workspace-navigator"}, set()),
    # The Plugins route exposes its content; the page-owned navigator is a drawer the user opens.
    "initial-PluginInventory": ({"plugins-search"}, {"navigator-overlay", "navigator-scrim", "plugins-navigator"}),
    "initial-ScheduledTasks": ({"navigator-overlay", "navigator-scrim", "scheduled-navigator"}, set()),
    "initial-Settings": ({"navigator-overlay", "navigator-scrim", "settings-navigator"}, set()),
    "after-more-rail-more-plugins": ({"plugins-search"}, {"navigator-overlay", "navigator-scrim", "plugins-navigator"}),
    "after-more-rail-more-scheduled": ({"navigator-overlay", "navigator-scrim", "scheduled-navigator"}, set()),
    "after-more-rail-more-hosts": ({"navigator-overlay", "navigator-scrim", "hosts-navigator"}, set()),
    "after-more-rail-more-settings": ({"navigator-overlay", "navigator-scrim", "settings-navigator"}, set()),
}

LINE = re.compile(r"^PROBE (.+?) -> \[(.*)\]$")


def parse(path):
    observed = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        m = LINE.match(raw.strip())
        if m:
            tags = {t.strip() for t in m.group(2).split(",") if t.strip()}
            observed[m.group(1)] = tags
    return observed


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_LOG
    observed = parse(path)
    problems = []
    for step, (present, absent) in CONTRACT.items():
        if step not in observed:
            problems.append(step + ": no PROBE line in " + str(path))
            continue
        got = observed[step]
        for tag in sorted(present - got):
            problems.append(step + ": expected present but missing -> " + tag)
        for tag in sorted(absent & got):
            problems.append(step + ": expected absent but mounted -> " + tag)
    print("log:", path)
    for step in CONTRACT:
        print("  " + step + " -> " + str(sorted(observed.get(step, []))))
    print("PROBLEMS:", len(problems))
    for p in problems:
        print("  - " + p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
