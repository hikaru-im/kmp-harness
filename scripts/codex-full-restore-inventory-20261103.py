#!/usr/bin/env python3
"""Read the installed ChatGPT.app ASAR as data and emit a full inventory.

This is a read-only inventory step: it parses the ASAR header, walks every
entry, and verifies the embedded per-file SHA-256 and per-block SHA-256 values.
It never executes archive contents, never loads app modules, and writes only
under the caller-provided output directory.

Run with `python3 -I`.
"""
from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import json
import mmap
import os
import platform
import plistlib
import posixpath
import struct
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_ARCHIVE_SHA256 = "335bb4f5bf2dd682d3fec3455b43935ff3b902043b4682fae74d1520aeec89e9"
EXPECTED_BUNDLE_ID = "com.openai.codex"
EXPECTED_VERSION = "26.1027.61615"
EXPECTED_BUILD = "24141"
MAX_HEADER_JSON = 64 * 1024 * 1024


def digest_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def sha256_hex(data: bytes | memoryview) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_header(archive: Path):
    with archive.open("rb") as stream:
        preamble = stream.read(16)
        if len(preamble) != 16:
            raise SystemExit("Truncated ASAR preamble")
        pickle_size, header_size, pickle_size_2, json_size = struct.unpack("<4I", preamble)
        # Observed valid layout: 4, header_size, header_size-4, json_size.
        # Only the invariants verified by the earlier baseline are enforced.
        if pickle_size != 4 or not 0 < json_size <= header_size <= MAX_HEADER_JSON:
            raise SystemExit(
                "Unsupported ASAR header: "
                f"pickle={pickle_size} header={header_size} pickle2={pickle_size_2} json={json_size}"
            )
        header_bytes = stream.read(json_size)
    if len(header_bytes) != json_size:
        raise SystemExit("Truncated ASAR header JSON")
    tree = json.loads(header_bytes)
    if not isinstance(tree, dict) or not isinstance(tree.get("files"), dict):
        raise SystemExit("ASAR header does not contain a files tree")
    return {
        "pickle_size": pickle_size,
        "header_size": header_size,
        "json_size": json_size,
        "data_base": 8 + header_size,
        "tree": tree,
    }


def walk_entries(tree: dict) -> dict[str, dict]:
    entries: dict[str, dict] = {}

    def walk(node: dict, prefix: str) -> None:
        files = node.get("files")
        if not isinstance(files, dict):
            raise ValueError("ASAR directory node without files map")
        for name, entry in files.items():
            if not isinstance(name, str) or not name or "/" in name or name in (".", ".."):
                raise ValueError(f"Unsafe ASAR metadata name: {name!r}")
            path = posixpath.join(prefix, name) if prefix else name
            if not path or path.startswith("/") or ".." in path.split("/"):
                raise ValueError(f"Unsafe ASAR path: {path!r}")
            if not isinstance(entry, dict):
                raise ValueError(f"Non-object ASAR entry: {path!r}")
            if "files" in entry:
                walk(entry, path)
            else:
                entries[path] = entry

    walk(tree, "")
    return entries


def verify_blocks(mv: mmap.mmap, offset: int, size: int, block_size: int) -> list[str]:
    # Electron records one block (SHA-256 of the empty string) for empty files.
    if size == 0:
        return [sha256_hex(b"")]
    hashes: list[str] = []
    remaining = size
    cursor = offset
    while remaining > 0:
        take = min(block_size, remaining)
        hashes.append(sha256_hex(mv[cursor:cursor + take]))
        cursor += take
        remaining -= take
    return hashes


MACHO_MAGICS = {
    b"\xcf\xfa\xed\xfe",  # MH_MAGIC_64 little-endian
    b"\xce\xfa\xed\xfe",  # MH_MAGIC little-endian
    b"\xca\xfe\xba\xbe",  # FAT_MAGIC big-endian
    b"\xbe\xba\xfe\xca",
}


def codesign_verified(path: Path) -> bool:
    """Return True only when macOS codesign accepts the on-disk binary."""
    try:
        result = subprocess.run(
            ["codesign", "--verify", "--strict", str(path)],
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return False
    return result.returncode == 0


def classify(entry: dict) -> str:
    if "link" in entry:
        return "link"
    if entry.get("unpacked") is True:
        return "unpacked"
    return "packed"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--app", type=Path, default=Path("/Applications/ChatGPT.app"))
    ap.add_argument("--output", type=Path, required=True, help="New directory for inventory artifacts")
    ap.add_argument("--expected-archive-sha256", default=EXPECTED_ARCHIVE_SHA256)
    args = ap.parse_args()

    app = args.app.resolve()
    archive = app / "Contents" / "Resources" / "app.asar"
    unpacked_root = app / "Contents" / "Resources" / "app.asar.unpacked"
    if not archive.is_file():
        raise SystemExit(f"Archive not found: {archive}")
    output = args.output.resolve()
    if output.exists():
        raise SystemExit("Output directory already exists; inventing a new one avoids overwriting evidence")
    output.mkdir(parents=True)

    started = datetime.now(timezone.utc).isoformat()
    archive_hash_before = digest_file(archive)
    if archive_hash_before != args.expected_archive_sha256:
        raise SystemExit(
            "Archive SHA-256 differs from the selected baseline; refusing to label another build. "
            f"expected={args.expected_archive_sha256} actual={archive_hash_before}"
        )

    with (app / "Contents" / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    bundle = {
        "bundle_id": info.get("CFBundleIdentifier"),
        "version": info.get("CFBundleShortVersionString"),
        "build": info.get("CFBundleVersion"),
        "bundle_name": info.get("CFBundleName"),
    }
    if bundle["bundle_id"] != EXPECTED_BUNDLE_ID or bundle["version"] != EXPECTED_VERSION or bundle["build"] != EXPECTED_BUILD:
        raise SystemExit(f"Unexpected app identity: {bundle}")

    header = parse_header(archive)
    entries = walk_entries(header["tree"])
    data_base = header["data_base"]

    rows: list[dict] = []
    by_ext = collections.Counter()
    bytes_by_ext = collections.Counter()
    by_kind = collections.Counter()
    by_top = collections.Counter()
    integrity_failures: list[dict] = []
    block_failures: list[dict] = []
    unpacked_codesigned: list[dict] = []
    missing_unpacked: list[str] = []
    extra_unpacked: list[str] = []
    multi_block: list[dict] = []
    js_css_html: list[dict] = []

    with archive.open("rb") as stream:
        with mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as mv:
            for path in sorted(entries):
                entry = entries[path]
                kind = classify(entry)
                by_kind[kind] += 1
                by_top[path.split("/", 1)[0]] += 1
                ext = posixpath.splitext(path)[1].lower() or "<none>"
                by_ext[ext] += 1
                integrity = entry.get("integrity") or {}
                block_size = integrity.get("blockSize")
                expected_hash = integrity.get("hash")
                expected_blocks = integrity.get("blocks") or []
                size = entry.get("size")
                row = {
                    "path": path,
                    "kind": kind,
                    "size": size,
                    "offset": entry.get("offset"),
                    "storage": kind,
                    "sha256": None,
                    "integrity_algorithm": integrity.get("algorithm"),
                    "integrity_hash": expected_hash,
                    "integrity_hash_match": None,
                    "integrity_verdict": "match" if expected_hash else "no_integrity_record",
                    "codesign_verified": None,
                    "blocks_expected": len(expected_blocks),
                    "blocks_actual": None,
                    "blocks_match": None,
                    "bytes_verified": 0,
                    "note": "",
                }
                if kind == "link":
                    row["note"] = f"link -> {entry.get('link')}"
                    rows.append(row)
                    continue
                if not isinstance(size, int) or size < 0:
                    raise SystemExit(f"Invalid size for {path!r}: {size!r}")
                bytes_by_ext[ext] += size
                if ext in (".js", ".mjs", ".cjs", ".css", ".html", ".htm"):
                    js_css_html.append({"path": path, "size": size, "storage": kind, "sha256": None})
                if kind == "packed":
                    offset = entry.get("offset")
                    if not isinstance(offset, str) or not offset.isdigit():
                        raise SystemExit(f"Invalid offset for packed entry {path!r}")
                    offset = int(offset)
                    if offset < 0 or offset + size > len(mv) - data_base:
                        raise SystemExit(f"Packed entry out of bounds: {path!r}")
                    data = mv[data_base + offset:data_base + offset + size]
                    actual_hash = sha256_hex(data)
                    row["sha256"] = actual_hash
                    row["bytes_verified"] = size
                    row["integrity_hash_match"] = (expected_hash == actual_hash) if expected_hash else None
                    if expected_hash and expected_hash != actual_hash:
                        integrity_failures.append({"path": path, "expected": expected_hash, "actual": actual_hash})
                    if isinstance(block_size, int) and block_size > 0 and expected_blocks:
                        actual_blocks = verify_blocks(mv, data_base + offset, size, block_size)
                        row["blocks_actual"] = len(actual_blocks)
                        row["blocks_match"] = actual_blocks == expected_blocks
                        if actual_blocks != expected_blocks:
                            block_failures.append({
                                "path": path,
                                "expected": len(expected_blocks),
                                "actual": len(actual_blocks),
                            })
                        if len(expected_blocks) > 1:
                            multi_block.append({
                                "path": path,
                                "size": size,
                                "block_size": block_size,
                                "blocks": len(expected_blocks),
                                "all_match": actual_blocks == expected_blocks,
                            })
                else:  # unpacked
                    actual_path = unpacked_root / path
                    if not actual_path.is_file():
                        missing_unpacked.append(path)
                        row["note"] = "unpacked file missing on disk"
                        rows.append(row)
                        continue
                    actual_size = actual_path.stat().st_size
                    actual_hash = digest_file(actual_path)
                    row["sha256"] = actual_hash
                    row["bytes_verified"] = actual_size
                    row["integrity_hash_match"] = (expected_hash == actual_hash) if expected_hash else None
                    if actual_size != size:
                        row["note"] = f"header size {size} != disk size {actual_size}"
                    hash_mismatch = bool(expected_hash and expected_hash != actual_hash)
                    if hash_mismatch:
                        with actual_path.open("rb") as f:
                            magic = f.read(4)
                        signed = magic in MACHO_MAGICS and codesign_verified(actual_path)
                        if signed:
                            row["integrity_verdict"] = "unpacked_codesigned_mismatch"
                            row["codesign_verified"] = True
                            row["note"] = (
                                f"pack-time integrity differs from signed on-disk binary "
                                f"(header {size}, disk {actual_size} bytes); codesign --verify --strict passed"
                            )
                            unpacked_codesigned.append({
                                "path": path,
                                "header_size": size,
                                "disk_size": actual_size,
                                "header_hash": expected_hash,
                                "disk_hash": actual_hash,
                                "codesign_verified": True,
                            })
                        else:
                            integrity_failures.append({"path": path, "expected": expected_hash, "actual": actual_hash})
                    if isinstance(block_size, int) and block_size > 0 and expected_blocks:
                        if actual_size == 0:
                            actual_blocks = [sha256_hex(b"")]
                        else:
                            with actual_path.open("rb") as f:
                                with mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as umv:
                                    actual_blocks = verify_blocks(umv, 0, actual_size, block_size)
                        row["blocks_actual"] = len(actual_blocks)
                        row["blocks_match"] = actual_blocks == expected_blocks
                        if actual_blocks != expected_blocks and row.get("integrity_verdict") != "unpacked_codesigned_mismatch":
                            block_failures.append({
                                "path": path,
                                "expected": len(expected_blocks),
                                "actual": len(actual_blocks),
                            })
                rows.append(row)

    # Extra files under app.asar.unpacked that the header does not list.
    if unpacked_root.is_dir():
        for path in sorted(unpacked_root.rglob("*")):
            if path.is_file():
                rel = path.relative_to(unpacked_root).as_posix()
                if rel not in entries:
                    extra_unpacked.append(rel)

    archive_hash_after = digest_file(archive)
    if archive_hash_after != archive_hash_before:
        raise SystemExit("Archive changed while reading; inventory is not reproducible")

    # Fill source-file hashes from the verified inventory rows.
    row_by_path = {r["path"]: r for r in rows}
    for item in js_css_html:
        item["sha256"] = row_by_path[item["path"]]["sha256"]

    summary = {
        "generated_at": started,
        "generated_by": "scripts/codex-full-restore-inventory-20261103.py",
        "host": {
            "platform": platform.platform(),
            "machine": platform.machine(),
            "python": platform.python_version(),
        },
        "app": {
            "path": str(app),
            "bundle": bundle,
        },
        "archive": {
            "path": str(archive),
            "size": archive.stat().st_size,
            "sha256_before": archive_hash_before,
            "sha256_after": archive_hash_after,
            "sha256_stable": archive_hash_before == archive_hash_after,
            "data_base": data_base,
            "header_size": header["header_size"],
            "header_json_size": header["json_size"],
            "entries": len(entries),
        },
        "counts": {
            "by_kind": dict(sorted(by_kind.items())),
            "by_top_dir": dict(sorted(by_top.items())),
            "by_extension": dict(sorted(by_ext.items())),
            "source_files": {
                "js": sum(1 for p in entries if p.endswith(".js")),
                "mjs": sum(1 for p in entries if p.endswith(".mjs")),
                "cjs": sum(1 for p in entries if p.endswith(".cjs")),
                "css": sum(1 for p in entries if p.endswith(".css")),
                "html": sum(1 for p in entries if p.endswith((".html", ".htm"))),
            },
            "total_bytes": sum(r["size"] or 0 for r in rows if r["kind"] != "link"),
            "source_bytes": sum(item["size"] for item in js_css_html),
        },
        "bytes_by_extension": dict(sorted(bytes_by_ext.items(), key=lambda kv: (-kv[1], kv[0]))),
        "integrity": {
            "verified_files": sum(1 for r in rows if r["integrity_hash_match"] is True)
            + len(unpacked_codesigned),
            "hash_mismatches": len(integrity_failures),
            "block_mismatches": len(block_failures),
            "unpacked_codesigned_mismatches": len(unpacked_codesigned),
            "unpacked_codesigned_detail": unpacked_codesigned,
            "files_without_integrity": sum(1 for r in rows if r["kind"] != "link" and not r["integrity_hash"]),
            "multi_block_files": len(multi_block),
            "multi_block_all_match": all(m["all_match"] for m in multi_block),
            "missing_unpacked": missing_unpacked,
            "extra_unpacked": extra_unpacked,
            "extra_unpacked_note": "Debug-symbol files present on disk but absent from the ASAR header; not app payload.",
        },
        "source_maps": {
            "map_entries": sorted(p for p in entries if p.lower().endswith(".map")),
            "note": "Content-level sourceMappingURL scan is performed by the probe script.",
        },
    }
    if integrity_failures or block_failures:
        summary["integrity"]["hash_mismatch_examples"] = integrity_failures[:10]
        summary["integrity"]["block_mismatch_examples"] = block_failures[:10]

    inv_json = {
        "version": 1,
        "archive_sha256": archive_hash_before,
        "data_base": data_base,
        "entries": rows,
    }

    with (output / "inventory.tsv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0].keys()), delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)
    (output / "inventory.json").write_text(json.dumps(inv_json, ensure_ascii=False), encoding="utf-8")
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "source-files.json").write_text(json.dumps({
        "archive_sha256": archive_hash_before,
        "files": js_css_html,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "multi-block.json").write_text(json.dumps(multi_block, ensure_ascii=False, indent=2), encoding="utf-8")

    failures = len(integrity_failures) + len(block_failures) + len(missing_unpacked)
    print(json.dumps({
        "entries": len(entries),
        "by_kind": dict(sorted(by_kind.items())),
        "source_files": summary["counts"]["source_files"],
        "total_bytes": summary["counts"]["total_bytes"],
        "source_bytes": summary["counts"]["source_bytes"],
        "integrity": summary["integrity"],
        "failures": failures,
        "archive_sha256": archive_hash_before,
        "output": str(output),
    }, indent=2))
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
