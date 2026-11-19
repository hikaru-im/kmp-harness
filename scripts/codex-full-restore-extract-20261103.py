#!/usr/bin/env python3
"""Extract every verified ASAR entry into a fresh original/ tree.

Input is the inventory produced by codex-full-restore-inventory-20261103.py.
Packed entries are copied byte-for-byte from the ASAR data area; unpacked
entries are copied from app.asar.unpacked. Every written file is re-hashed and
compared with the verified inventory hash. Extracted files are made read-only.

The archive is never executed and its contents are never imported.
Run with `python3 -I`.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import mmap
import os
import platform
import posixpath
import shutil
import stat
import struct
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_ARCHIVE_SHA256 = "335bb4f5bf2dd682d3fec3455b43935ff3b902043b4682fae74d1520aeec89e9"
MAX_HEADER_JSON = 64 * 1024 * 1024
CHUNK = 4 * 1024 * 1024


def digest_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(CHUNK), b""):
            h.update(block)
    return h.hexdigest()


def parse_header(archive: Path):
    with archive.open("rb") as stream:
        preamble = stream.read(16)
        pickle_size, header_size, pickle_size_2, json_size = struct.unpack("<4I", preamble)
        if pickle_size != 4 or not 0 < json_size <= header_size <= MAX_HEADER_JSON:
            raise SystemExit(f"Unsupported ASAR header: {pickle_size} {header_size} {pickle_size_2} {json_size}")
        tree = json.loads(stream.read(json_size))
    return tree, 8 + header_size


def walk_tree(tree: dict):
    files: dict[str, dict] = {}
    dirs: list[str] = []

    def walk(node: dict, prefix: str) -> None:
        for name, entry in node.get("files", {}).items():
            if not isinstance(name, str) or not name or "/" in name or name in (".", ".."):
                raise SystemExit(f"Unsafe ASAR metadata name: {name!r}")
            path = posixpath.join(prefix, name) if prefix else name
            if path.startswith("/") or ".." in path.split("/"):
                raise SystemExit(f"Unsafe ASAR path: {path!r}")
            if "files" in entry:
                dirs.append(path)
                walk(entry, path)
            else:
                files[path] = entry

    walk(tree, "")
    return files, dirs


def safe_dest(root: Path, path: str) -> Path:
    dest = (root / path).resolve()
    if root != dest and root not in dest.parents:
        raise SystemExit(f"Refusing path outside original/: {path!r}")
    return dest


def copy_hashed(src_read, dest: Path, expected_size: int) -> tuple[str, int]:
    dest.parent.mkdir(parents=True, exist_ok=True)
    h = hashlib.sha256()
    written = 0
    with dest.open("wb") as out:
        while written < expected_size:
            chunk = src_read(min(CHUNK, expected_size - written))
            if not chunk:
                break
            out.write(chunk)
            h.update(chunk)
            written += len(chunk)
    if written != expected_size:
        raise SystemExit(f"Size mismatch writing {dest}: expected {expected_size}, wrote {written}")
    os.chmod(dest, 0o444)
    return h.hexdigest(), written


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--app", type=Path, default=Path("/Applications/ChatGPT.app"))
    ap.add_argument("--inventory", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True, help="New original/ directory")
    ap.add_argument("--expected-archive-sha256", default=EXPECTED_ARCHIVE_SHA256)
    args = ap.parse_args()

    app = args.app.resolve()
    archive = app / "Contents" / "Resources" / "app.asar"
    unpacked_root = app / "Contents" / "Resources" / "app.asar.unpacked"
    inventory = json.loads(args.inventory.read_text(encoding="utf-8"))
    entries = inventory["entries"]
    if inventory.get("archive_sha256") != args.expected_archive_sha256:
        raise SystemExit("Inventory was produced from a different archive hash")
    output = args.output.resolve()
    if output.exists():
        raise SystemExit("Output directory already exists; refusing to overwrite")
    output.mkdir(parents=True)
    started_at = datetime.now(timezone.utc).isoformat()
    t0 = time.monotonic()

    archive_hash_before = digest_file(archive)
    if archive_hash_before != args.expected_archive_sha256:
        raise SystemExit(f"Archive hash changed: {archive_hash_before}")

    tree, data_base = parse_header(archive)
    header_files, header_dirs = walk_tree(tree)
    if set(header_files) != {e["path"] for e in entries}:
        raise SystemExit("Inventory paths do not match the ASAR header")

    for rel in header_dirs:
        safe_dest(output, rel).mkdir(parents=True, exist_ok=True)

    results: list[dict] = []
    failures: list[dict] = []
    total_written = 0
    with archive.open("rb") as stream:
        with mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as mv:
            for entry in entries:
                path = entry["path"]
                dest = safe_dest(output, path)
                kind = entry["kind"]
                size = entry["size"]
                if kind == "link":
                    target = header_files[path].get("link")
                    if not isinstance(target, str) or target.startswith("/") or ".." in target.split("/"):
                        failures.append({"path": path, "reason": "unsafe or missing link target", "target": target})
                        continue
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    os.symlink(target, dest)
                    results.append({"path": path, "kind": "link", "target": target, "verified": True})
                    continue
                if kind == "packed":
                    offset = int(header_files[path]["offset"])
                    if offset < 0 or offset + size > len(mv) - data_base:
                        failures.append({"path": path, "reason": "packed entry out of bounds"})
                        continue
                    mv.seek(data_base + offset)
                    written_hash, written = copy_hashed(mv.read, dest, size)
                elif kind == "unpacked":
                    source = unpacked_root / path
                    if not source.is_file():
                        failures.append({"path": path, "reason": "unpacked source missing"})
                        continue
                    with source.open("rb") as f:
                        written_hash, written = copy_hashed(f.read, dest, source.stat().st_size)
                else:
                    failures.append({"path": path, "reason": f"unknown kind {kind}"})
                    continue
                verified = written_hash == entry["sha256"]
                if not verified:
                    failures.append({
                        "path": path,
                        "reason": "hash mismatch after extraction",
                        "expected": entry["sha256"],
                        "actual": written_hash,
                    })
                total_written += written
                results.append({
                    "path": path,
                    "kind": kind,
                    "size": written,
                    "sha256": written_hash,
                    "verified": verified,
                })

    archive_hash_after = digest_file(archive)
    if archive_hash_after != archive_hash_before:
        raise SystemExit("Archive changed during extraction")

    expected_bytes = sum(e["size"] for e in entries if e["kind"] != "link")
    report = {
        "generated_at": started_at,
        "generated_by": "scripts/codex-full-restore-extract-20261103.py",
        "host": {"platform": platform.platform(), "machine": platform.machine(), "python": platform.python_version()},
        "source": {
            "archive": str(archive),
            "archive_sha256_before": archive_hash_before,
            "archive_sha256_after": archive_hash_after,
            "inventory": str(args.inventory),
            "unpacked_root": str(unpacked_root),
        },
        "output": {
            "root": str(output),
            "files_extracted": sum(1 for r in results if r.get("kind") != "link"),
            "links_created": sum(1 for r in results if r.get("kind") == "link"),
            "directories_created": len(header_dirs),
            "bytes_written": total_written,
            "expected_bytes": expected_bytes,
            "bytes_match": total_written == expected_bytes,
            "all_hashes_verified": not failures,
            "duration_seconds": round(time.monotonic() - t0, 3),
        },
        "failures": failures,
        "files": results,
    }
    (output.parent / "extraction-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (output.parent / "original-manifest.sha256").open("w", encoding="utf-8") as stream:
        for r in results:
            if r.get("kind") != "link":
                stream.write(f"{r['sha256']}  original/{r['path']}\n")

    print(json.dumps({
        "files": report["output"]["files_extracted"],
        "directories": len(header_dirs),
        "bytes": total_written,
        "bytes_match": report["output"]["bytes_match"],
        "all_hashes_verified": report["output"]["all_hashes_verified"],
        "failures": len(failures),
        "duration_seconds": report["output"]["duration_seconds"],
        "output": str(output),
    }, indent=2))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
