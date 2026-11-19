#!/usr/bin/env python3
"""Summarize the mechanical, semantic and runtime gates from the reports.

This script never upgrades a failed or missing report into a pass. Each gate
keeps its raw evidence numbers and an explicit status.
"""
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return None
    except json.JSONDecodeError as error:
        return {"_error": f"invalid JSON: {error}"}


def status(passed: bool | None, note: str = "") -> dict:
    if passed is None:
        return {"status": "not_run", "note": note}
    return {"status": "pass" if passed else "fail", "note": note}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--root", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    root = args.root.resolve()
    baseline = root / "02-baseline"

    audit_old = (root / "01-pipeline-audit" / "old-pipeline.md").is_file()
    audit_cmp = (root / "01-pipeline-audit" / "old-vs-round09.md").is_file()

    inventory = load(baseline / "inventory" / "summary.json")
    extraction = load(baseline / "extraction-report.json")
    formatting = load(baseline / "format-report.json")
    verification = load(baseline / "verification.json")
    probes = load(baseline / "probes" / "probe-summary.json")
    artifacts = load(root / "artifacts.json")
    semantic = load(root / "03-semantic" / "verification.json")
    binding_map = load(root / "03-semantic" / "binding-map.json")
    ui_evidence = load(root / "03-semantic" / "ui-evidence.json")
    semantic4 = load(root / "04-semantic" / "verification.json")
    binding_map4 = load(root / "04-semantic" / "binding-map.json")
    ui_evidence4 = load(root / "04-semantic" / "ui-evidence.json")
    semantic5 = load(root / "05-semantic" / "verification.json")
    binding_map5 = load(root / "05-semantic" / "binding-map.json")
    ui_evidence5 = load(root / "05-semantic" / "ui-evidence.json")
    semantic6 = load(root / "06-semantic" / "verification.json")
    binding_map6 = load(root / "06-semantic" / "binding-map.json")
    ui_evidence6 = load(root / "06-semantic" / "ui-evidence.json")
    semantic7 = load(root / "07-semantic" / "verification.json")
    binding_map7 = load(root / "07-semantic" / "binding-map.json")
    ui_evidence7 = load(root / "07-semantic" / "ui-evidence.json")
    semantic8 = load(root / "08-semantic" / "verification.json")
    binding_map8 = load(root / "08-semantic" / "binding-map.json")
    ui_evidence8 = load(root / "08-semantic" / "ui-evidence.json")
    semantic9 = load(root / "09-semantic" / "verification.json")
    binding_map9 = load(root / "09-semantic" / "binding-map.json")
    ui_evidence9 = load(root / "09-semantic" / "ui-evidence.json")
    semantic10 = load(root / "10-semantic" / "verification.json")
    binding_map10 = load(root / "10-semantic" / "binding-map.json")
    ui_evidence10 = load(root / "10-semantic" / "ui-evidence.json")
    semantic11 = load(root / "11-semantic" / "verification.json")
    binding_map11 = load(root / "11-semantic" / "binding-map.json")
    ui_evidence11 = load(root / "11-semantic" / "ui-evidence.json")
    semantic12 = load(root / "12-semantic" / "verification.json")
    binding_map12 = load(root / "12-semantic" / "binding-map.json")
    ui_evidence12 = load(root / "12-semantic" / "ui-evidence.json")

    inv_status = None
    inv_detail = {}
    if inventory:
        integrity = inventory.get("integrity", {})
        counts = inventory.get("counts", {})
        entries = inventory.get("archive", {}).get("entries")
        signed = integrity.get("unpacked_codesigned_detail", [])
        # verified_files counts "hash match OR codesign verified"; the signed
        # Mach-O binaries are included through codesign, not through hash.
        inv_detail = {
            "entries": entries,
            "by_kind": counts.get("by_kind"),
            "source_files": counts.get("source_files"),
            "integrity_verified": integrity.get("verified_files"),
            "pack_time_hash_matches": (entries or 0) - len(signed),
            "hash_mismatches": integrity.get("hash_mismatches"),
            "block_mismatches": integrity.get("block_mismatches"),
            "unpacked_codesigned_mismatches": len(signed),
            "unpacked_codesigned_all_verified": bool(signed) and all(item.get("codesign_verified") is True for item in signed),
            "multi_block_files": integrity.get("multi_block_files"),
            "archive_sha256_stable": inventory.get("archive", {}).get("sha256_stable"),
        }
        inv_status = (
            entries == 22534
            and integrity.get("hash_mismatches") == 0
            and integrity.get("block_mismatches") == 0
            and inventory.get("archive", {}).get("sha256_stable") is True
            and integrity.get("verified_files") == entries
            and len(signed) == 7
            and all(item.get("codesign_verified") is True for item in signed)
        )

    ext_status = None
    ext_detail = {}
    if extraction:
        out = extraction.get("output", {})
        signed = []
        if inventory:
            signed = inventory.get("integrity", {}).get("unpacked_codesigned_detail", [])
        signed_delta = sum(item.get("disk_size", 0) - item.get("header_size", 0) for item in signed)
        written = out.get("bytes_written")
        expected = out.get("expected_bytes")
        reconciled = isinstance(written, int) and isinstance(expected, int) and written - expected == signed_delta
        ext_detail = {
            "files": out.get("files_extracted"),
            "bytes": written,
            "header_bytes_expected": expected,
            "signed_unpacked_delta": signed_delta,
            "bytes_reconciled": reconciled,
            "bytes_match_raw": out.get("bytes_match"),
            "all_hashes_verified": out.get("all_hashes_verified"),
            "failures": len(extraction.get("failures", [])),
        }
        ext_status = (
            out.get("files_extracted") == 22534
            and out.get("all_hashes_verified") is True
            and len(extraction.get("failures", [])) == 0
            and reconciled
        )

    fmt_status = None
    fmt_detail = {}
    if formatting:
        fmt_detail = {
            "complete": formatting.get("complete"),
            "prettier": formatting.get("prettier"),
            "files": formatting.get("output", {}).get("files"),
            "failures": len(formatting.get("failures", [])),
        }
        fmt_status = formatting.get("complete") is True and len(formatting.get("failures", [])) == 0

    ver_status = None
    ver_detail = {}
    if verification:
        totals = verification.get("totals", {})
        ver_detail = {
            "files": totals.get("files"),
            "js": totals.get("js"),
            "css": totals.get("css"),
            "html": totals.get("html"),
            "js_ast_equal": totals.get("jsAstEqual"),
            "css_canonical_equal": totals.get("cssCanonicalEqual"),
            "html_parsed": totals.get("htmlParsed"),
            "hash_verified": totals.get("hashVerified"),
            "failures": totals.get("failures"),
            "failure_reasons": sorted({f.get("reason", "?") for f in verification.get("failures", [])}),
        }
        # Hash and parse are the hard gates. CSS canonical equality is reported separately
        # because Prettier can legitimately normalize spelling; a false value is not hidden.
        ver_status = (
            totals.get("files") == 16742
            and totals.get("hashVerified") == totals.get("files")
            and totals.get("jsAstEqual") == totals.get("js")
            and totals.get("htmlParsed") == totals.get("html")
            and totals.get("cssCanonicalEqual") == totals.get("css")
        )

    probe_detail = {}
    if probes:
        # The probe's `sourcemapUsable` means "found a map entry OR a reference";
        # this build has references but zero .map files, so report both facts.
        sourcemaps = probes.get("sourcemaps", {})
        map_files = sourcemaps.get("mapEntries", [])
        url_hits = sourcemaps.get("urlHits", [])
        probe_detail = {
            "sourcemap_map_files": len(map_files),
            "sourcemap_reference_hits": len(url_hits),
            "sourcemap_usable_map_files": len(map_files) > 0,
            "obfuscation_evidence": probes.get("probeSummary", {}).get("obfuscationEvidence"),
            "react_compiler_cache_calls": probes.get("probeSummary", {}).get("reactCompilerCacheCalls"),
            "react_compiler_useMemoCache_calls": probes.get("probeSummary", {}).get("reactCompilerUseMemoCacheCalls"),
            "text_files": probes.get("probeSummary", {}).get("textFiles"),
        }

    artifact_detail = {}
    if artifacts:
        artifact_detail = {"files": artifacts.get("total_files"), "bytes": artifacts.get("total_bytes")}

    sem_status = None
    sem_detail = {}
    if semantic:
        summary = semantic.get("summary", {})
        bindings = semantic.get("bindings", [])
        # targetChecks covers references and constant violations; the declaration
        # itself is covered separately by ownBindingChecks.
        expected_targets = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings
        )
        expected_edits = sum(
            item.get("editCount", 0) for item in semantic.get("files", [])
        )
        sem_detail = {
            "scope": semantic.get("scope"),
            "reports": summary.get("reports"),
            "renames": summary.get("renames"),
            "edits": expected_edits,
            "evidence_checks": summary.get("evidenceChecks"),
            "own_binding_checks": summary.get("ownBindingChecks"),
            "target_checks": summary.get("targetChecks"),
            "expected_target_checks": expected_targets,
            "inverse_byte_equal": summary.get("inverseByteEqual"),
            "semantic_parses": summary.get("semanticParses"),
            "css_checks": summary.get("cssChecks"),
            "css_passed": summary.get("cssPassed"),
            "formatted_copies": summary.get("formattedCopies"),
            "formatted_copies_equal": summary.get("formattedCopiesEqual"),
            "failures": summary.get("failures"),
            "binding_map_verified": (binding_map or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence or {}).get("summary", {}).get("failed"),
        }
        sem_status = (
            summary.get("reports") is not None
            and summary.get("failures") == 0
            and summary.get("inverseByteEqual") is True
            and summary.get("semanticParses") is True
            and (summary.get("renames") or 0) > 0
            and summary.get("ownBindingChecks") == summary.get("renames")
            and summary.get("targetChecks") == expected_targets
            and summary.get("cssPassed") == summary.get("cssChecks")
            and summary.get("formattedCopiesEqual") == summary.get("formattedCopies")
            and (binding_map or {}).get("totals", {}).get("failed") == 0
            and (binding_map or {}).get("totals", {}).get("verified") == summary.get("renames")
            and (ui_evidence or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence or {}).get("summary", {}).get("passed")
            == (ui_evidence or {}).get("summary", {}).get("claims")
        )

    sem4_status = None
    sem4_detail = {}
    if semantic4:
        summary4 = semantic4.get("summary", {})
        bindings4 = semantic4.get("bindings", [])
        expected_targets4 = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings4
        )
        expected_edits4 = sum(
            item.get("editCount", 0) for item in semantic4.get("files", [])
        )
        sem4_detail = {
            "scope": semantic4.get("scope"),
            "reports": summary4.get("reports"),
            "renames": summary4.get("renames"),
            "edits": expected_edits4,
            "evidence_checks": summary4.get("evidenceChecks"),
            "own_binding_checks": summary4.get("ownBindingChecks"),
            "target_checks": summary4.get("targetChecks"),
            "expected_target_checks": expected_targets4,
            "inverse_byte_equal": summary4.get("inverseByteEqual"),
            "semantic_parses": summary4.get("semanticParses"),
            "css_checks": summary4.get("cssChecks"),
            "css_passed": summary4.get("cssPassed"),
            "formatted_copies": summary4.get("formattedCopies"),
            "formatted_copies_equal": summary4.get("formattedCopiesEqual"),
            "failures": summary4.get("failures"),
            "binding_map_verified": (binding_map4 or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map4 or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence4 or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence4 or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence4 or {}).get("summary", {}).get("failed"),
        }
        sem4_status = (
            summary4.get("reports") is not None
            and summary4.get("failures") == 0
            and summary4.get("inverseByteEqual") is True
            and summary4.get("semanticParses") is True
            and (summary4.get("renames") or 0) > 0
            and summary4.get("ownBindingChecks") == summary4.get("renames")
            and summary4.get("targetChecks") == expected_targets4
            and summary4.get("cssPassed") == summary4.get("cssChecks")
            and summary4.get("formattedCopiesEqual") == summary4.get("formattedCopies")
            and (binding_map4 or {}).get("totals", {}).get("failed") == 0
            and (binding_map4 or {}).get("totals", {}).get("verified") == summary4.get("renames")
            and (ui_evidence4 or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence4 or {}).get("summary", {}).get("passed")
            == (ui_evidence4 or {}).get("summary", {}).get("claims")
        )

    sem5_status = None
    sem5_detail = {}
    if semantic5:
        summary5 = semantic5.get("summary", {})
        bindings5 = semantic5.get("bindings", [])
        expected_targets5 = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings5
        )
        expected_edits5 = sum(
            item.get("editCount", 0) for item in semantic5.get("files", [])
        )
        sem5_detail = {
            "scope": semantic5.get("scope"),
            "reports": summary5.get("reports"),
            "renames": summary5.get("renames"),
            "edits": expected_edits5,
            "evidence_checks": summary5.get("evidenceChecks"),
            "own_binding_checks": summary5.get("ownBindingChecks"),
            "target_checks": summary5.get("targetChecks"),
            "expected_target_checks": expected_targets5,
            "inverse_byte_equal": summary5.get("inverseByteEqual"),
            "semantic_parses": summary5.get("semanticParses"),
            "css_checks": summary5.get("cssChecks"),
            "css_passed": summary5.get("cssPassed"),
            "formatted_copies": summary5.get("formattedCopies"),
            "formatted_copies_equal": summary5.get("formattedCopiesEqual"),
            "failures": summary5.get("failures"),
            "binding_map_verified": (binding_map5 or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map5 or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence5 or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence5 or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence5 or {}).get("summary", {}).get("failed"),
        }
        sem5_status = (
            summary5.get("reports") is not None
            and summary5.get("failures") == 0
            and summary5.get("inverseByteEqual") is True
            and summary5.get("semanticParses") is True
            and (summary5.get("renames") or 0) > 0
            and summary5.get("ownBindingChecks") == summary5.get("renames")
            and summary5.get("targetChecks") == expected_targets5
            and summary5.get("cssPassed") == summary5.get("cssChecks")
            and summary5.get("formattedCopiesEqual") == summary5.get("formattedCopies")
            and (binding_map5 or {}).get("totals", {}).get("failed") == 0
            and (binding_map5 or {}).get("totals", {}).get("verified") == summary5.get("renames")
            and (ui_evidence5 or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence5 or {}).get("summary", {}).get("passed")
            == (ui_evidence5 or {}).get("summary", {}).get("claims")
        )

    sem6_status = None
    sem6_detail = {}
    if semantic6:
        summary6 = semantic6.get("summary", {})
        bindings6 = semantic6.get("bindings", [])
        expected_targets6 = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings6
        )
        expected_edits6 = sum(
            item.get("editCount", 0) for item in semantic6.get("files", [])
        )
        sem6_detail = {
            "scope": semantic6.get("scope"),
            "reports": summary6.get("reports"),
            "renames": summary6.get("renames"),
            "edits": expected_edits6,
            "evidence_checks": summary6.get("evidenceChecks"),
            "own_binding_checks": summary6.get("ownBindingChecks"),
            "target_checks": summary6.get("targetChecks"),
            "expected_target_checks": expected_targets6,
            "inverse_byte_equal": summary6.get("inverseByteEqual"),
            "semantic_parses": summary6.get("semanticParses"),
            "css_checks": summary6.get("cssChecks"),
            "css_passed": summary6.get("cssPassed"),
            "formatted_copies": summary6.get("formattedCopies"),
            "formatted_copies_equal": summary6.get("formattedCopiesEqual"),
            "failures": summary6.get("failures"),
            "binding_map_verified": (binding_map6 or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map6 or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence6 or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence6 or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence6 or {}).get("summary", {}).get("failed"),
        }
        sem6_status = (
            summary6.get("reports") is not None
            and summary6.get("failures") == 0
            and summary6.get("inverseByteEqual") is True
            and summary6.get("semanticParses") is True
            and (summary6.get("renames") or 0) > 0
            and summary6.get("ownBindingChecks") == summary6.get("renames")
            and summary6.get("targetChecks") == expected_targets6
            and summary6.get("cssPassed") == summary6.get("cssChecks")
            and summary6.get("formattedCopiesEqual") == summary6.get("formattedCopies")
            and (binding_map6 or {}).get("totals", {}).get("failed") == 0
            and (binding_map6 or {}).get("totals", {}).get("verified") == summary6.get("renames")
            and (ui_evidence6 or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence6 or {}).get("summary", {}).get("passed")
            == (ui_evidence6 or {}).get("summary", {}).get("claims")
        )

    sem7_status = None
    sem7_detail = {}
    if semantic7:
        summary7 = semantic7.get("summary", {})
        bindings7 = semantic7.get("bindings", [])
        expected_targets7 = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings7
        )
        expected_edits7 = sum(
            item.get("editCount", 0) for item in semantic7.get("files", [])
        )
        sem7_detail = {
            "scope": semantic7.get("scope"),
            "reports": summary7.get("reports"),
            "renames": summary7.get("renames"),
            "edits": expected_edits7,
            "evidence_checks": summary7.get("evidenceChecks"),
            "own_binding_checks": summary7.get("ownBindingChecks"),
            "target_checks": summary7.get("targetChecks"),
            "expected_target_checks": expected_targets7,
            "inverse_byte_equal": summary7.get("inverseByteEqual"),
            "semantic_parses": summary7.get("semanticParses"),
            "css_checks": summary7.get("cssChecks"),
            "css_passed": summary7.get("cssPassed"),
            "formatted_copies": summary7.get("formattedCopies"),
            "formatted_copies_equal": summary7.get("formattedCopiesEqual"),
            "failures": summary7.get("failures"),
            "binding_map_verified": (binding_map7 or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map7 or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence7 or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence7 or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence7 or {}).get("summary", {}).get("failed"),
        }
        sem7_status = (
            summary7.get("reports") is not None
            and summary7.get("failures") == 0
            and summary7.get("inverseByteEqual") is True
            and summary7.get("semanticParses") is True
            and (summary7.get("renames") or 0) > 0
            and summary7.get("ownBindingChecks") == summary7.get("renames")
            and summary7.get("targetChecks") == expected_targets7
            and summary7.get("cssPassed") == summary7.get("cssChecks")
            and summary7.get("formattedCopiesEqual") == summary7.get("formattedCopies")
            and (binding_map7 or {}).get("totals", {}).get("failed") == 0
            and (binding_map7 or {}).get("totals", {}).get("verified") == summary7.get("renames")
            and (ui_evidence7 or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence7 or {}).get("summary", {}).get("passed")
            == (ui_evidence7 or {}).get("summary", {}).get("claims")
        )

    sem8_status = None
    sem8_detail = {}
    if semantic8:
        summary8 = semantic8.get("summary", {})
        bindings8 = semantic8.get("bindings", [])
        expected_targets8 = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings8
        )
        expected_edits8 = sum(
            item.get("editCount", 0) for item in semantic8.get("files", [])
        )
        sem8_detail = {
            "scope": semantic8.get("scope"),
            "reports": summary8.get("reports"),
            "renames": summary8.get("renames"),
            "edits": expected_edits8,
            "evidence_checks": summary8.get("evidenceChecks"),
            "own_binding_checks": summary8.get("ownBindingChecks"),
            "target_checks": summary8.get("targetChecks"),
            "expected_target_checks": expected_targets8,
            "inverse_byte_equal": summary8.get("inverseByteEqual"),
            "semantic_parses": summary8.get("semanticParses"),
            "css_checks": summary8.get("cssChecks"),
            "css_passed": summary8.get("cssPassed"),
            "formatted_copies": summary8.get("formattedCopies"),
            "formatted_copies_equal": summary8.get("formattedCopiesEqual"),
            "failures": summary8.get("failures"),
            "binding_map_verified": (binding_map8 or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map8 or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence8 or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence8 or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence8 or {}).get("summary", {}).get("failed"),
        }
        sem8_status = (
            summary8.get("reports") is not None
            and summary8.get("failures") == 0
            and summary8.get("inverseByteEqual") is True
            and summary8.get("semanticParses") is True
            and (summary8.get("renames") or 0) > 0
            and summary8.get("ownBindingChecks") == summary8.get("renames")
            and summary8.get("targetChecks") == expected_targets8
            and summary8.get("cssPassed") == summary8.get("cssChecks")
            and summary8.get("formattedCopiesEqual") == summary8.get("formattedCopies")
            and (binding_map8 or {}).get("totals", {}).get("failed") == 0
            and (binding_map8 or {}).get("totals", {}).get("verified") == summary8.get("renames")
            and (ui_evidence8 or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence8 or {}).get("summary", {}).get("passed")
            == (ui_evidence8 or {}).get("summary", {}).get("claims")
        )

    def slice_verdict(semantic_doc, binding_doc, evidence_doc):
        if not semantic_doc:
            return None, {}
        s = semantic_doc.get("summary", {})
        binds = semantic_doc.get("bindings", [])
        expected_targets = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in binds
        )
        expected_edits = sum(
            item.get("editCount", 0) for item in semantic_doc.get("files", [])
        )
        detail = {
            "scope": semantic_doc.get("scope"),
            "reports": s.get("reports"),
            "renames": s.get("renames"),
            "edits": expected_edits,
            "evidence_checks": s.get("evidenceChecks"),
            "own_binding_checks": s.get("ownBindingChecks"),
            "target_checks": s.get("targetChecks"),
            "expected_target_checks": expected_targets,
            "inverse_byte_equal": s.get("inverseByteEqual"),
            "semantic_parses": s.get("semanticParses"),
            "css_checks": s.get("cssChecks"),
            "css_passed": s.get("cssPassed"),
            "formatted_copies": s.get("formattedCopies"),
            "formatted_copies_equal": s.get("formattedCopiesEqual"),
            "failures": s.get("failures"),
            "binding_map_verified": (binding_doc or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_doc or {}).get("totals", {}).get("failed"),
            "ui_claims": (evidence_doc or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (evidence_doc or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (evidence_doc or {}).get("summary", {}).get("failed"),
        }
        ok = (
            s.get("failures") == 0
            and s.get("inverseByteEqual") is True
            and s.get("semanticParses") is True
            and s.get("ownBindingChecks") == s.get("renames")
            and s.get("targetChecks") == expected_targets
            and s.get("cssPassed") == s.get("cssChecks")
            and s.get("formattedCopiesEqual") == s.get("formattedCopies")
            and (binding_doc or {}).get("totals", {}).get("failed") == 0
            and (binding_doc or {}).get("totals", {}).get("verified") == s.get("renames")
            and (evidence_doc or {}).get("summary", {}).get("failed") == 0
            and (evidence_doc or {}).get("summary", {}).get("passed")
            == (evidence_doc or {}).get("summary", {}).get("claims")
        )
        return ok, detail

    sem9_status = None
    sem9_detail = {}
    if semantic9:
        summary9 = semantic9.get("summary", {})
        bindings9 = semantic9.get("bindings", [])
        expected_targets9 = sum(
            item.get("referenceCount", 0) + item.get("constantViolationCount", 0)
            for item in bindings9
        )
        expected_edits9 = sum(
            item.get("editCount", 0) for item in semantic9.get("files", [])
        )
        sem9_detail = {
            "scope": semantic9.get("scope"),
            "reports": summary9.get("reports"),
            "renames": summary9.get("renames"),
            "edits": expected_edits9,
            "evidence_checks": summary9.get("evidenceChecks"),
            "own_binding_checks": summary9.get("ownBindingChecks"),
            "target_checks": summary9.get("targetChecks"),
            "expected_target_checks": expected_targets9,
            "inverse_byte_equal": summary9.get("inverseByteEqual"),
            "semantic_parses": summary9.get("semanticParses"),
            "css_checks": summary9.get("cssChecks"),
            "css_passed": summary9.get("cssPassed"),
            "formatted_copies": summary9.get("formattedCopies"),
            "formatted_copies_equal": summary9.get("formattedCopiesEqual"),
            "failures": summary9.get("failures"),
            "binding_map_verified": (binding_map9 or {}).get("totals", {}).get("verified"),
            "binding_map_failed": (binding_map9 or {}).get("totals", {}).get("failed"),
            "ui_claims": (ui_evidence9 or {}).get("summary", {}).get("claims"),
            "ui_claims_passed": (ui_evidence9 or {}).get("summary", {}).get("passed"),
            "ui_claims_failed": (ui_evidence9 or {}).get("summary", {}).get("failed"),
        }
        sem9_status = (
            summary9.get("reports") is not None
            and summary9.get("failures") == 0
            and summary9.get("inverseByteEqual") is True
            and summary9.get("semanticParses") is True
            and (summary9.get("renames") or 0) > 0
            and summary9.get("ownBindingChecks") == summary9.get("renames")
            and summary9.get("targetChecks") == expected_targets9
            and summary9.get("cssPassed") == summary9.get("cssChecks")
            and summary9.get("formattedCopiesEqual") == summary9.get("formattedCopies")
            and (binding_map9 or {}).get("totals", {}).get("failed") == 0
            and (binding_map9 or {}).get("totals", {}).get("verified") == summary9.get("renames")
            and (ui_evidence9 or {}).get("summary", {}).get("failed") == 0
            and (ui_evidence9 or {}).get("summary", {}).get("passed")
            == (ui_evidence9 or {}).get("summary", {}).get("claims")
        )

    sem10_status, sem10_detail = slice_verdict(
        semantic10, binding_map10, ui_evidence10
    )
    sem11_status, sem11_detail = slice_verdict(
        semantic11, binding_map11, ui_evidence11
    )
    sem12_status, sem12_detail = slice_verdict(
        semantic12, binding_map12, ui_evidence12
    )

    # ------------------------------------------------------------------
    # Phase 13 — runtime. The runtime gate is deliberately NOT a boolean in
    # the same sense as the static gates: a read-only pass can only ever
    # cover part of the app, so it reports the coverage it actually reached
    # and never rounds that up to a pass. `runtime` stays False; the honest
    # value lives in `coverage_status`.
    # ------------------------------------------------------------------
    runtime_dir = root / "13-runtime"
    runtime = load(runtime_dir / "runtime-evidence.json")
    runtime_crosscheck = load(runtime_dir / "crosscheck.json")
    runtime_sweep = load(runtime_dir / "sweep-raw.json")
    runtime_shells = load(runtime_dir / "route-stylesheets.json")
    runtime_chunks = load(runtime_dir / "runtime-chunks.json")
    runtime_hints = load(runtime_dir / "gate-hints.json")
    runtime_rail = load(runtime_dir / "settings-rail-inventory.json")
    runtime_readme = (runtime_dir / "README.md").is_file()

    runtime_status = None
    runtime_detail = {}
    if runtime and runtime_sweep:
        coverage = runtime.get("coverage", {}).get("settings", {})
        panels = coverage.get("panelsObserved") or 0
        total = coverage.get("routeKeysTotal") or 0
        screenshots = (runtime.get("artifacts") or {}).get("screenshots") or 0
        dom = (runtime.get("artifacts") or {}).get("domSnapshots") or 0
        consoles = (runtime.get("artifacts") or {}).get("consoleCaptures") or 0
        observed_surfaces = len(runtime_sweep)
        shell_surfaces = len(
            [s for s in runtime_sweep if str(s.get("slug", "")).startswith("shell-")]
        )
        blocked = len(coverage.get("routeKeysUnreached") or [])
        runtime_detail = {
            "attach_method": "isolated_instance_cdp",
            "launch_command": (runtime.get("attach") or {})
            .get("tier2_isolatedInstance", {})
            .get("launchCommand"),
            "asar_sha256_matches_baseline": str(
                (runtime.get("environment") or {}).get("asarSha256", "")
            ).startswith("335bb4f5bf2dd682d3fec3455b43935ff3b902043b4682fae74d1520aeec89e9"),
            "settings_panels_observed": panels,
            "settings_route_keys_total": total,
            "settings_route_keys_blocked": blocked,
            "surfaces_observed": observed_surfaces,
            "shell_surfaces_observed": shell_surfaces,
            "settings_surfaces_observed": panels,
            "screenshots": screenshots,
            "dom_snapshots": dom,
            "console_captures": consoles,
            "console_entries": (runtime.get("console") or {}).get("entries"),
            "console_errors": (runtime.get("console") or {}).get("errors"),
            "scripts_parsed": (runtime.get("runtimeAssets") or {}).get("scriptsParsed"),
            "stylesheets_applied": (runtime.get("runtimeAssets") or {}).get("stylesheetsApplied"),
            "phase12_sheets_applied": (runtime.get("runtimeAssets") or {})
            .get("phase12SheetsAppliedAtRuntime"),
            "phase12_sheets_copied": (runtime.get("runtimeAssets") or {})
            .get("phase12SheetsCopied"),
            "crosscheck_tally": (runtime_crosscheck or {}).get("tally"),
            "discrepancies": len(runtime.get("discrepancies") or []),
            "route_stylesheet_panels": len((runtime_shells or {}).get("panels") or []),
            "chunks_captured": (runtime_chunks or {}).get("count"),
            "gate_hints": len((runtime_hints or {}).get("hints") or {}),
            "rail_is_scrollable": (
                None
                if not runtime_rail
                else runtime_rail.get("scrollHeight") != runtime_rail.get("clientHeight")
            ),
            "readme": runtime_readme,
        }
        # The pass condition for a *partial* runtime round: the evidence has
        # to be internally consistent and complete for the scope it claims.
        # It never asserts that the runtime scope itself is fully covered.
        runtime_consistent = (
            panels > 0
            and panels + blocked == total
            and observed_surfaces > 0
            and screenshots > 0
            and dom > 0
            and (runtime.get("console") or {}).get("errors") == 0
            and bool(runtime_readme)
            and bool((runtime.get("attach") or {}).get("tier2_isolatedInstance"))
            and (runtime.get("privacy") or {}).get("redaction") is not None
        )
        runtime_status = runtime_consistent

    # ------------------------------------------------------------------
    # Phase 14 — call graph. Like the runtime gate, this one reports the
    # scope it actually covered instead of a bare boolean: the scan set is the
    # slice seeds plus their first dependency ring, not the transitive closure.
    # `completion.callgraph` carries that note; `completion.semantic` stays
    # false because a call graph is not a semantic rewrite of the app.
    # ------------------------------------------------------------------
    cg_dir = root / "14-callgraph"
    cg_graph = load(cg_dir / "graph.json")
    cg_verify = load(cg_dir / "verification.json")
    cg_routes = load(cg_dir / "routes-to-panels.json")
    cg_pairs = load(cg_dir / "message-id-pairs.json")
    cg_det = load(cg_dir / "determinism-runs.json")
    cg_readme = (cg_dir / "README.md").is_file()
    cg_gaps = (cg_dir / "gaps.md").is_file()
    cg_chain_files = sorted(p.name for p in (cg_dir / "chains").glob("*.json")) if (cg_dir / "chains").is_dir() else []

    cg_status = None
    cg_detail = {
        "nodes": None,
        "edges": None,
        "node_kinds": None,
        "edge_kinds": None,
        "edge_status": None,
        "route_rows": len((cg_routes or {}).get("rows") or []),
        "routes_runtime_observed": ((cg_routes or {}).get("tally") or {}).get("runtimeObserved"),
        "routes_static_only": ((cg_routes or {}).get("tally") or {}).get("staticOnly"),
        "routes_absent": ((cg_routes or {}).get("tally") or {}).get("absent"),
        # Slice-scoped message-id numbers only: the graph-wide `paired` count
        # has a different denominator and would read as a >100% pairing rate.
        "message_ids_referenced": ((cg_pairs or {}).get("tally") or {}).get("idsReferencedBySlices"),
        "message_ids_referenced_real": ((cg_pairs or {}).get("tally") or {}).get(
            "referencedRealIds"
        ),
        "message_ids_paired": ((cg_pairs or {}).get("tally") or {}).get("referencedPaired"),
        "message_ids_inline": ((cg_pairs or {}).get("tally") or {}).get("referencedInline"),
        "message_ids_producer_only": ((cg_pairs or {}).get("tally") or {}).get(
            "referencedProducerOnly"
        ),
        "message_ids_resolved_of_referenced": ((cg_pairs or {}).get("tally") or {}).get(
            "resolvedOfReferenced"
        ),
        "message_id_pairing_rate": ((cg_pairs or {}).get("tally") or {}).get(
            "pairingRateOfReferenced"
        ),
        "message_id_pairing_rate_strict": ((cg_pairs or {}).get("tally") or {}).get(
            "pairingRateStrict"
        ),
        "reverse_check_sampled": ((cg_verify or {}).get("reverseCheck") or {}).get("sampled"),
        "reverse_check_failures": ((cg_verify or {}).get("reverseCheck") or {}).get("failures"),
        "invented_semantic_names": len(
            ((cg_verify or {}).get("semanticNames") or {}).get("invented") or []
        ),
        "dangling_edges": ((cg_verify or {}).get("integrity") or {}).get("danglingEdges"),
        "parse_failures": len(
            ((cg_verify or {}).get("integrity") or {}).get("parseFailures") or []
        ),
        "determinism_identical": (cg_det or {}).get("identical"),
        "chain_files": len(cg_chain_files),
        "readme": cg_readme,
        "gaps": cg_gaps,
    }
    if cg_graph and cg_verify and cg_routes and cg_pairs and cg_det:
        cg_detail.update(
            {
                "nodes": (cg_graph.get("totals") or {}).get("nodes"),
                "edges": (cg_graph.get("totals") or {}).get("edges"),
                "node_kinds": cg_graph.get("nodeKinds"),
                "edge_kinds": cg_graph.get("edgeKinds"),
                "edge_status": cg_graph.get("edgeStatus"),
            }
        )
        cg_consistent = (
            (cg_graph.get("totals") or {}).get("nodes", 0) > 0
            and (cg_graph.get("totals") or {}).get("edges", 0) > 0
            and (cg_verify.get("semanticNames") or {}).get("pass") is True
            and (cg_verify.get("reverseCheck") or {}).get("failures") == 0
            and ((cg_verify.get("reverseCheck") or {}).get("sampled") or 0) >= 100
            and (cg_verify.get("integrity") or {}).get("danglingEdges") == 0
            and not ((cg_verify.get("integrity") or {}).get("parseFailures") or [])
            and (cg_det.get("identical") is True)
            and (((cg_routes.get("tally") or {}).get("runtimeObserved") or 0) > 0)
            and len(cg_chain_files) >= 5
            and cg_readme
            and cg_gaps
        )
        cg_status = bool(cg_consistent)

    mechanical_ok = all(v is True for v in [inv_status, ext_status, fmt_status, ver_status])
    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "generated_by": "scripts/codex-full-restore-gate-20261103.py",
        "root": str(root),
        "phase1": {
            "old_pipeline_audit": status(audit_old, "01-pipeline-audit/old-pipeline.md"),
            "old_vs_round09_comparison": status(audit_cmp, "01-pipeline-audit/old-vs-round09.md"),
        },
        "phase2": {
            "inventory": {**status(inv_status), **inv_detail},
            "extraction": {**status(ext_status), **ext_detail},
            "formatting": {**status(fmt_status), **fmt_detail},
            "parse_hash_verification": {**status(ver_status), **ver_detail},
            "probes": status(probes is not None, "sourcemaps/obfuscation/module wrapping/react compiler"),
            "probe_detail": probe_detail,
            "artifacts_manifest": status(artifacts is not None, "artifacts.json"),
            "artifact_detail": artifact_detail,
        },
        "phase3": {
            "semantic_slice": {**status(sem_status), "scope": "shell_navigation_slice_only", **sem_detail},
        },
        "phase4": {
            "semantic_slice": {
                **status(sem4_status),
                "scope": "home_sidebar_and_landing_page_container_slice_only",
                **sem4_detail,
            },
        },
        "phase5": {
            "semantic_slice": {
                **status(sem5_status),
                "scope": "conversation_page_message_rendering_and_composer_slice_only",
                **sem5_detail,
            },
        },
        "phase6": {
            "semantic_slice": {
                **status(sem6_status),
                "scope": "settings_shell_settings_pages_and_model_provider_gateways_slice_only",
                **sem6_detail,
            },
        },
        "phase7": {
            "semantic_slice": {
                **status(sem7_status),
                "scope": "git_changes_diff_commit_branch_and_worktree_slice_only",
                **sem7_detail,
            },
        },
        "phase8": {
            "semantic_slice": {
                **status(sem8_status),
                "scope": "scheduled_tasks_automations_page_list_dialog_schedule_targeting_history_and_run_results_slice_only",
                **sem8_detail,
            },
        },
        "phase9": {
            "semantic_slice": {
                **status(sem9_status),
                "scope": "plugins_account_login_remote_connections_marketplace_and_mcp_settings_slice_only",
                **sem9_detail,
            },
        },
        "phase10": {
            "semantic_slice": {
                **status(sem10_status),
                "scope": "responsive_web_layout_and_mobile_breakpoints_slice_only_no_native_mobile_shell",
                **sem10_detail,
            },
        },
        "phase11": {
            "semantic_slice": {
                **status(sem11_status),
                "scope": "terminal_and_file_panel_slices_only_xterm_internals_and_host_file_search_excluded",
                **sem11_detail,
            },
        },
        "phase12": {
            "semantic_slice": {
                **status(sem12_status),
                "scope": "remaining_settings_page_slices_only_bounded_reading_no_runtime_settings_interaction",
                **sem12_detail,
            },
        },
        "phase13": {
            "runtime": {
                **status(
                    runtime_status,
                    "pass here means the Phase 13 evidence is internally consistent and complete "
                    "FOR THE SCOPE IT CLAIMS — it is not a claim that the runtime scope is fully "
                    "covered, which is why completion.runtime stays false and coverage_status is "
                    "runtime_partial.",
                ),
                "coverage_status": "runtime_partial",
                "scope": (
                    "read_only_navigation_of_one_isolated_instance_no_form_input_no_write"
                    if runtime_status is not None
                    else "not_run"
                ),
                **runtime_detail,
            },
        },
        "phase14": {
            "callgraph": {
                **status(
                    cg_status,
                    "pass here means the call graph is internally consistent, its semantic names all "
                    "come from the phase 03-12 binding-maps, its edge sample re-derives from the "
                    "baseline, and two runs hash identically. It does NOT mean the graph is complete: "
                    "the scan set is the 172 slice seeds plus their first dependency ring, not the "
                    "transitive import closure.",
                ),
                "coverage_status": "seed_plus_first_ring",
                "scope": (
                    "static_call_graph_over_complete_chunks_joined_to_phase13_runtime_observations"
                    if cg_status is not None
                    else "not_run"
                ),
                **cg_detail,
            },
        },
        "completion": {
            "mechanical": mechanical_ok,
            "semantic": False,
            "callgraph": {
                "status": bool(cg_status) if cg_status is not None else False,
                "scope": "seed_plus_first_ring",
                "note": (
                    "Phase 14 covered the 172 phase 03-12 slice files and the first dependency ring "
                    "that carries API/message evidence (618 chunks scanned). Edges are complete for "
                    "that set, and absent for anything reached only through deeper imports. This is "
                    "not a whole-app call graph."
                ),
            },
            "semantic_slice": sem_status is True
            and sem4_status is True
            and sem5_status is True
            and sem6_status is True
            and sem7_status is True
            and sem8_status is True
            and sem9_status is True
            and sem10_status is True
            and sem11_status is True
            and sem12_status is True,
            "semantic_scope": "phase3_shell_navigation_phase4_sidebar_landing_phase5_conversation_phase6_settings_phase7_git_worktree_phase8_scheduled_tasks_phase9_plugins_account_phase10_responsive_mobile_and_phase11_terminal_file_panel_and_phase12_remaining_settings_pages_slices",
            "runtime": False,
            "runtime_coverage": "runtime_partial",
            "runtime_scope": (
                "phase13 read-only navigation of one isolated instance: 19 of 55 settings route keys, "
                "6 shell surfaces, 1 conversation, the Git/Changes tab, the scheduled-task page and the "
                "plugin marketplace, plus a 299-claim message-id cross-check. No form input, no save/delete/"
                "run/send, no account switch, no plugin install, no network write. The other 36 settings "
                "routes were absent from the sidebar and are recorded as blocked with their observed reason."
            ),
            "notes": {
                "mechanical": "Inventory + extraction + formatting + parse/hash verification completed with the recorded failures.",
                "semantic": "Whole-application semantic restoration is NOT complete. Only the Phase 3 shell/navigation, Phase 4 home/sidebar landing, Phase 5 conversation/message/composer, Phase 6 settings/model-provider, Phase 7 Git/worktree, Phase 8 scheduled-task/automation, Phase 9 plugin/account/login/connection, Phase 10 responsive/mobile-layout, Phase 11 terminal/file-panel and Phase 12 remaining-settings-page slices are covered.",
                "semantic_slice": "03-semantic covers 5 files/78 bindings; 04-semantic covers 5 files/46 bindings; 05-semantic covers 14 baseline files/57 bindings across 15 slices; 06-semantic covers 13 baseline files/69 bindings across 17 slices; 07-semantic covers 17 baseline files/139 bindings across 25 slices; 08-semantic covers 15 baseline files/156 bindings across 29 slices; 09-semantic covers 27 baseline files/160 bindings across 38 slices; 10-semantic covers 18 baseline files/103 bindings across 25 slices plus 11 CSS copies; 11-semantic covers 27 baseline files/184 bindings across 32 slices plus 4 CSS copies; 12-semantic covers 49 baseline files/243 bindings across 49 slices plus 26 CSS copies; all are bounded reading slices, not the whole application.",
                "phase10": "Phase 10 records responsive web layout only. This Electron bundle has no native mobile shell: data-app-shell-responsive-sidebar, the settings mobile header and touch-overscroll containment are hardcoded off, and browser: variants stay inert because data-codex-window-type=electron.",
                "phase11": "Phase 11 covers terminal and file-panel semantics. Vendored xterm internals, host-side find-in-files results, OS-level file drops and native preview engines stay outside the bounded slices; no PTY, file operation or terminal interaction was run.",
                "phase12": "Phase 12 covers the remaining settings routes: every settings page that has its own lazy route except the five already covered by Phase 6. Routes, page containers, sections/form controls, toggles and selects, save/retry/error states, empty/loading states, destructive confirmations, message ids and the preloaded page stylesheets are recorded as bounded reading slices. Vendored/shared libraries (the 1.7 MB Tailwind utility stylesheet, xterm, Pierre, spreadsheet engines) and runtime behaviour stay outside the slices; no Electron launch, settings interaction, account API call or OS permission prompt was run.",
                "runtime": "Phase 13 DID launch an isolated Electron instance once (open -n with --remote-debugging-port=9333 and its own --user-data-dir) and performed read-only navigation: 19 settings panels, 6 shell surfaces, one existing conversation, the Git/Changes tab, the scheduled-task page and the plugin marketplace were opened and captured, with 29 screenshots, 26 DOM snapshots and 25 console captures. Still NOT performed: any form input, any save/delete/pause/run/send, any settings change, any account switch, any plugin install, any connection authorization, any Git operation, and any login. The user's pre-existing instance was never touched. Coverage is partial by nature — 36 of 55 settings route keys have no sidebar entry in this build and are recorded as blocked, and the run says nothing about interaction behaviour or network round-trips.",
                "callgraph": "Phase 14 parses complete baseline chunks with Babel (never slice text) to build a call graph over 618 chunks: the 172 phase 03-12 slice files plus the first dependency ring that carries API or message-id evidence. Nodes and edges carry baseline file, UTF-16/byte offset and line/column. Semantic names are taken only from the phase 03-12 binding-maps; none are invented. Edge status separates `static-observed` from `runtime-corroborated` (both chunks compiled by V8, which does NOT mean the edge ran) and `runtime-confirmed` (panel rendered or message id matched in DOM). NOT covered: the transitive import closure, so anything reachable only through deeper imports is absent — this is not a whole-app call graph, and `completion.callgraph` says so.",
            },
        },
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "mechanical": mechanical_ok,
        "semantic": False,
        "semantic_slice": sem_status is True
        and sem4_status is True
        and sem5_status is True
        and sem6_status is True
        and sem7_status is True
        and sem8_status is True
        and sem9_status is True
        and sem10_status is True,
        "runtime": False,
        "phase1": report["phase1"],
        "phase2": {k: v.get("status") for k, v in report["phase2"].items() if isinstance(v, dict)},
        "phase3": {"semantic_slice": report["phase3"]["semantic_slice"].get("status")},
        "phase4": {"semantic_slice": report["phase4"]["semantic_slice"].get("status")},
        "phase5": {"semantic_slice": report["phase5"]["semantic_slice"].get("status")},
        "phase6": {"semantic_slice": report["phase6"]["semantic_slice"].get("status")},
        "phase7": {"semantic_slice": report["phase7"]["semantic_slice"].get("status")},
        "phase8": {"semantic_slice": report["phase8"]["semantic_slice"].get("status")},
        "phase9": {"semantic_slice": report["phase9"]["semantic_slice"].get("status")},
        "phase10": {"semantic_slice": report["phase10"]["semantic_slice"].get("status")},
        "phase11": {"semantic_slice": report["phase11"]["semantic_slice"].get("status")},
        "phase12": {"semantic_slice": report["phase12"]["semantic_slice"].get("status")},
        "phase13": {
            "runtime": report["phase13"]["runtime"].get("status"),
            "coverage": report["phase13"]["runtime"].get("coverage_status"),
        },
        "phase14": {
            "callgraph": report["phase14"]["callgraph"].get("status"),
            "coverage": report["phase14"]["callgraph"].get("coverage_status"),
            "nodes": report["phase14"]["callgraph"].get("nodes"),
            "edges": report["phase14"]["callgraph"].get("edges"),
        },
        "output": str(args.output),
    }, indent=2))
    return 0 if mechanical_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
