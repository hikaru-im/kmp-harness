#!/usr/bin/env python3
"""Census of module wrapping formats in the extracted Codex archive.

The probe script uses line-anchored ESM regexes, which undercount single-line
minified chunks. This census complements it with plain lexical signatures read
from the head and tail of every JS/MJS file. It is evidence of format, not of
runtime behaviour: nothing here executes archive code.

Outputs a JSON document with per-directory counts and concrete examples.
"""
from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

HEAD = 64 * 1024
TAIL = 64 * 1024

SIGNATURES = {
    "webpack_chunk_ids": re.compile(rb"^exports\.ids=\["),
    "webpack_modules": re.compile(rb"exports\.modules=\{"),
    "webpack_require": re.compile(rb"__webpack_require__"),
    "esm_export": re.compile(rb"(?:^|[;}\n])export\s*\{"),
    "esm_export_default": re.compile(rb"(?:^|[;}\n])export\s+default\b"),
    "esm_import": re.compile(rb"(?:^|[;}\n])import\s*(?:[\w${*]|\{)"),
    "rolldown_runtime": re.compile(rb"rolldown-runtime|__commonJS\b|__esm\b"),
    "vite_preload": re.compile(rb"__vite__mapDeps|__vitePreload"),
    "cjs_exports": re.compile(rb"\b(?:module\.exports|exports\.[A-Za-z_$])"),
}


def classify(hits: dict[str, bool]) -> str:
    if hits["webpack_chunk_ids"] or (hits["webpack_modules"] and hits["webpack_require"]):
        return "webpack-cjs-chunk"
    if hits["rolldown_runtime"] and hits["esm_export"]:
        return "rolldown-esm-chunk"
    if hits["esm_export"] or hits["esm_export_default"] or hits["esm_import"]:
        return "esm"
    if hits["cjs_exports"]:
        return "cjs"
    return "unknown-or-json-ish"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--original", type=Path, required=True)
    ap.add_argument("--source-files", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    original = args.original.resolve()
    files = json.loads(args.source_files.read_text(encoding="utf-8"))["files"]
    js = [f for f in files if re.search(r"\.(?:js|mjs|cjs)$", f["path"], re.I)]

    per_dir: dict[str, Counter] = defaultdict(Counter)
    sig_totals: Counter = Counter()
    class_totals: Counter = Counter()
    examples: dict[str, list[str]] = defaultdict(list)
    bytes_total = 0
    unreadable: list[dict] = []

    for item in js:
        path = original / item["path"]
        try:
            size = path.stat().st_size
            with path.open("rb") as stream:
                head = stream.read(HEAD)
                if size > HEAD:
                    stream.seek(max(0, size - TAIL))
                    tail = stream.read(TAIL)
                else:
                    tail = b""
        except OSError as error:
            unreadable.append({"path": item["path"], "error": str(error)})
            continue
        bytes_total += size
        window = head + b"\n" + tail
        hits = {name: regex.search(window) is not None for name, regex in SIGNATURES.items()}
        for name, hit in hits.items():
            if hit:
                sig_totals[name] += 1
                if len(examples[name]) < 5:
                    examples[name].append(item["path"])
        kind = classify(hits)
        class_totals[kind] += 1
        top = ".vite" if item["path"].startswith(".vite/") else item["path"].split("/", 1)[0]
        per_dir[top][kind] += 1

    report = {
        "generated_by": "scripts/codex-full-restore-module-census-20261103.py",
        "inputs": {
            "original": str(original),
            "sourceFiles": str(args.source_files.resolve()),
            "jsFiles": len(js),
            "jsBytes": bytes_total,
            "window": {"head": HEAD, "tail": TAIL},
        },
        "signatureFiles": dict(sig_totals),
        "classification": dict(class_totals),
        "byTopDirectory": {key: dict(value) for key, value in sorted(per_dir.items())},
        "examples": dict(examples),
        "unreadable": unreadable,
        "note": "Lexical signatures only; no archive code was executed. webpack markers are read from the head, ESM export markers from head+tail.",
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "jsFiles": len(js),
        "classification": dict(class_totals),
        "signatureFiles": dict(sig_totals),
        "unreadable": len(unreadable),
        "output": str(args.output),
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
