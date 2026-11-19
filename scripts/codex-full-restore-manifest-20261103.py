#!/usr/bin/env python3
"""Hash every artifact under full-restore/ and write artifacts.json.

Read-only walk; the only write is the manifest itself.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import platform
from datetime import datetime, timezone
from pathlib import Path

CHUNK = 4 * 1024 * 1024


def digest(path: Path) -> tuple[str, int]:
    h = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(CHUNK), b""):
            h.update(block)
            size += len(block)
    return h.hexdigest(), size


def category(rel: str) -> str:
    first = rel.split("/", 1)[0]
    if first.startswith("original-superseded") or first.startswith("inventory-superseded"):
        return "superseded-run-evidence"
    return {
        "01-pipeline-audit": "phase1-audit",
        "02-baseline": "phase2-baseline",
        "04-semantic": "phase4-semantic",
        "05-semantic": "phase5-semantic",
        "06-semantic": "phase6-semantic",
        "07-semantic": "phase7-semantic",
        "08-semantic": "phase8-semantic",
        "09-semantic": "phase9-semantic",
        "10-semantic": "phase10-semantic",
        "11-semantic": "phase11-semantic",
        "12-semantic": "phase12-semantic",
        "13-runtime": "phase13-runtime",
        "14-callgraph": "phase14-callgraph",
        "README.md": "documentation",
    }.get(first, "other")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument(
        "--overwrite",
        action="store_true",
        help="replace an existing artifacts.json (the gate report is excluded)",
    )
    args = ap.parse_args()
    root = args.root.resolve()
    output = args.output.resolve()
    if output.exists() and not args.overwrite:
        raise SystemExit("artifacts.json already exists; pass --overwrite to regenerate")
    entries = []
    total = 0
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path == output:
            continue
        # gate-results.json is generated from this manifest; including it here
        # would make the manifest depend on its own consumer.
        if path.relative_to(root).as_posix() == "gate-results.json":
            continue
        rel = path.relative_to(root).as_posix()
        sha, size = digest(path)
        total += size
        entries.append({"path": rel, "category": category(rel), "bytes": size, "sha256": sha})
    doc = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "generated_by": "scripts/codex-full-restore-manifest-20261103.py",
        "host": {"platform": platform.platform(), "machine": platform.machine(), "python": platform.python_version()},
        "root": str(root),
        "total_files": len(entries),
        "total_bytes": total,
        "excluded": ["gate-results.json", "artifacts.json"],
        "entries": entries,
    }
    output.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"files": len(entries), "bytes": total, "output": str(output)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
