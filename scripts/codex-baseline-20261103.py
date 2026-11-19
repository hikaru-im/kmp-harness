#!/usr/bin/env python3
"""Read a local ASAR as data; emit metadata, never restored application code.

Run with python3 -I. Output must be a new directory. No application modules,
archive scripts, package managers, or archive-provided configs are executed.
Offsets in evidence are zero-based UTF-8 byte offsets of original packed files.
Import and route candidates are lexical hints, not an AST or runtime proof.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import platform
import plistlib
import posixpath
import re
import struct
import subprocess
from collections import Counter, deque
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

IMPORT = re.compile(r"(?:\bfrom\s*|\bimport\s*\(\s*|\bimport\s*)([\"'`])(?P<path>\.{1,2}/[^\"'`\s${}]+)\1")
ROUTE = re.compile(r"\bpath\s*:\s*([\"'`])(?P<value>/[^\"'`\n${}]{0,160})\1")
MOUNT = re.compile(r"createRoot|hydrateRoot|getElementById|\.render\(|\binitializeApp\b|\binitializeDictationSetup\b")
FOCUS = re.compile(r"^(?:app-(?:main|initial|runtime|shared)-|setup-runtime-|sidebar-|projects-index-page-|local-conversation-(?:page|thread)-|thread-app-shell-|settings-(?:page|tabs)|.*(?:theme|tokens|composer|terminal-panel|git-diff|worktree).*)")


def digest_file(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


class HtmlEntries(HTMLParser):
    def __init__(self):
        super().__init__()
        self.entries = []
        self.roots = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'script' and a.get('src'):
            self.entries.append({'kind': 'script', 'specifier': a['src'], 'type': a.get('type')})
        if tag == 'link' and a.get('href'):
            self.entries.append({'kind': a.get('rel'), 'specifier': a['href']})
        if a.get('id'):
            self.roots.append({'tag': tag, 'id': a['id']})


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--app', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--expected-sha256', required=True)
    args = ap.parse_args()
    app = args.app.resolve()
    archive = app / 'Contents/Resources/app.asar'
    archive_hash = digest_file(archive)
    if archive_hash != args.expected_sha256:
        raise SystemExit('Archive hash differs from the selected baseline; refusing to relabel another build.')
    # Never replace old outputs or unpack archive files onto disk.
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output.resolve()
    with (app / 'Contents/Info.plist').open('rb') as stream:
        info = plistlib.load(stream)
    with archive.open('rb') as stream:
        preamble = stream.read(16)
        pickle_size, header_size, _, json_size = struct.unpack('<4I', preamble)
        if pickle_size != 4 or not 0 < json_size <= header_size <= 64 * 1024 * 1024:
            raise SystemExit('Unsupported ASAR header')
        header_bytes = stream.read(json_size)
        tree = json.loads(header_bytes)
        base = 8 + header_size
        entries = {}

        def walk(node, prefix=''):
            for name, entry in node.get('files', {}).items():
                if '/' in name or name in ('.', '..'):
                    raise ValueError('Unsafe ASAR metadata name')
                path = posixpath.join(prefix, name)
                if 'files' in entry:
                    walk(entry, path)
                else:
                    entries[path] = entry

        walk(tree)

        def read(path):
            entry = entries[path]
            if 'offset' not in entry or entry.get('unpacked') or 'link' in entry:
                raise ValueError(f'Not a packed file: {path}')
            size, offset = int(entry['size']), int(entry['offset'])
            if offset < 0 or size < 0 or base + offset + size > archive.stat().st_size:
                raise ValueError(f'Out-of-bounds ASAR entry: {path}')
            stream.seek(base + offset)
            data = stream.read(size)
            if len(data) != size:
                raise ValueError(f'Truncated ASAR entry: {path}')
            return data

        package = json.loads(read('package.json'))
        html = {}
        graph = []
        routes = []
        mounts = []
        focus = []
        integrity = Counter()
        counts = Counter()
        missing = []
        file_hashes = {}
        with (output / 'inventory.tsv').open('w', newline='', encoding='utf-8') as dest:
            writer = csv.writer(dest, delimiter='\t')
            writer.writerow(('path', 'bytes', 'sha256', 'storage', 'integrity'))
            for path, entry in sorted(entries.items()):
                suffix = Path(path).suffix.lower()
                counts[suffix or '(no extension)'] += 1
                if entry.get('unpacked') or 'link' in entry:
                    storage = 'unpacked-not-read' if entry.get('unpacked') else 'link-not-followed'
                    integrity[storage] += 1
                    writer.writerow((path, entry.get('size', ''), '', storage, 'not-verified'))
                    continue
                data = read(path)
                sha = hashlib.sha256(data).hexdigest()
                file_hashes[path] = sha
                expected = entry.get('integrity', {}).get('hash')
                verdict = ('pass' if sha == expected else 'FAIL') if expected else 'no-header-digest'
                integrity[verdict] += 1
                writer.writerow((path, len(data), sha, 'packed', verdict))
                if path.endswith('.html') and path.startswith('webview/'):
                    parser = HtmlEntries()
                    parser.feed(data.decode('utf-8'))
                    html[path] = {'entries': parser.entries, 'dom_ids': parser.roots}
                if not path.endswith(('.js', '.mjs', '.cjs')) or not path.startswith(('webview/', '.vite/')):
                    continue
                text = data.decode('utf-8')
                for match in IMPORT.finditer(text):
                    specifier = match.group('path')
                    target = posixpath.normpath(posixpath.join(posixpath.dirname(path), specifier))
                    edge = {'source': path, 'target': target, 'specifier': specifier,
                            'byte_offset': len(text[:match.start()].encode('utf-8')),
                            'kind': 'dynamic' if re.match(r'import\s*\(', match.group()) else 'static',
                            'target_exists': target in entries}
                    graph.append(edge)
                    if target not in entries:
                        missing.append(edge)
                if path.startswith('webview/assets/') and FOCUS.match(posixpath.basename(path)):
                    focus.append(path)
                    for match in ROUTE.finditer(text):
                        routes.append({'file': path, 'byte_offset': len(text[:match.start()].encode('utf-8')),
                                       'literal': match.group('value'), 'status': 'literal-path-property-not-runtime-proof'})
                    for match in MOUNT.finditer(text):
                        mounts.append({'file': path, 'byte_offset': len(text[:match.start()].encode('utf-8')),
                                       'token': match.group(), 'status': 'lexical-occurrence'})

    incoming = {}
    adjacency = {}
    for edge in graph:
        incoming.setdefault(edge['target'], []).append({'source': edge['source'], 'kind': edge['kind'], 'byte_offset': edge['byte_offset']})
        if edge['target_exists']:
            adjacency.setdefault(edge['source'], set()).add(edge['target'])
    roots = []
    for item in html.get('webview/index.html', {}).get('entries', []):
        if item['kind'] == 'script' and item['specifier'].startswith('./'):
            roots.append(posixpath.normpath(posixpath.join('webview', item['specifier'])))
    previous = {root: None for root in roots}
    queue = deque(roots)
    while queue:
        current = queue.popleft()
        for target in sorted(adjacency.get(current, ())):
            if target not in previous:
                previous[target] = current
                queue.append(target)

    def chain(path):
        if path not in previous:
            return None
        result = []
        while path is not None:
            result.append(path)
            path = previous[path]
        return result[::-1]

    selected = [{'file': path, 'sha256': file_hashes[path], 'reachable_from_main_html': path in previous,
                 'one_lexical_import_chain': chain(path), 'incoming_edges': incoming.get(path, [])}
                for path in focus]
    signing = subprocess.run(['codesign', '-dv', '--verbose=4', str(app)], capture_output=True, text=True)
    # Store identity metadata only, not entitlements or application code.
    signing_lines = [line for line in signing.stderr.splitlines() if line.startswith(('Identifier=', 'Authority=', 'TeamIdentifier=', 'Timestamp='))]
    notice = app / 'Contents/Resources/THIRD_PARTY_NOTICES.txt'
    old = Path(__file__).resolve().parents[1] / 'references/decode-codex/ref/webview/assets'
    old_names = {p.name for p in old.iterdir() if p.is_file()} if old.exists() else set()
    new_names = {posixpath.basename(p) for p in entries if p.startswith('webview/assets/') and '/' not in p[len('webview/assets/'):]}
    stable = lambda name: re.sub(r'-[a-f0-9]{12}(?=\.)', '-<hash>', name)
    old_groups = Counter(map(stable, old_names))
    new_groups = Counter(map(stable, new_names))
    comparison = {'old_reference_git_commit': 'a3cd34ee91fc72535b1a0eb239ac575571040969',
                  'warning': 'Git commit identifies reference tooling, not the extracted app build. Old ref provenance is not cryptographically bound to that commit.',
                  'old_asset_files': len(old_names), 'new_asset_files': len(new_names),
                  'new_filename_groups': sorted(new_groups.keys() - old_groups.keys()),
                  'absent_filename_groups': sorted(old_groups.keys() - new_groups.keys()),
                  'grouping': 'filename suffix normalization only; no semantic equivalence asserted'}
    archive_hash_after = digest_file(archive)
    verification = {'archive_hash_unchanged_during_run': archive_hash_after == archive_hash,
                    'packed_integrity': dict(integrity), 'inventory_entries': len(entries),
                    'graph_edges': len(graph), 'missing_import_targets': len(missing),
                    'lexically_reachable_from_main_html': len(previous),
                    'source_map_files': sum(p.endswith('.map') for p in entries),
                    'entry_script_paths_exist': bool(roots) and all(p in entries for p in roots),
                    'scope': 'static metadata only; no runtime/visual/license authorization assertion',
                    'limitations': ['Regex import graph can miss computed specifiers and produce false positives in strings/comments.',
                                    'Dynamic import reachability does not prove route activation, feature availability, or actual rendering.',
                                    'Unpacked files and symbolic links are not read or followed.',
                                    'No deobfuscation, semantic renaming, TSX reconstruction, or source redistribution.',
                                    'No application launch, account interaction, or runtime geometry measurement.']}
    manifest = {'schema': 'codex-static-baseline.v1', 'recorded_at': datetime.now(timezone.utc).isoformat(),
                'source_kind': 'locally-installed-application', 'source_path': str(app),
                'download_url': None, 'installer_package': None, 'distribution_channel': 'not-established',
                'version': info['CFBundleShortVersionString'], 'build': info['CFBundleVersion'],
                'bundle_identifier': info['CFBundleIdentifier'], 'display_name': info.get('CFBundleDisplayName'),
                'package_name': package.get('name'), 'package_version': package.get('version'),
                'package_product_name': package.get('productName'), 'package_license': package.get('license'),
                'package_main': package.get('main'), 'codesign_identity': signing_lines,
                'archive_path': str(archive), 'archive_bytes': archive.stat().st_size,
                'archive_sha256': archive_hash, 'asar_header_sha256': hashlib.sha256(header_bytes).hexdigest(),
                'third_party_notices_sha256': digest_file(notice),
                'host': {'system': platform.system(), 'release': platform.release(), 'machine': platform.machine()},
                'extensions': dict(counts), 'input_snapshot_copied': False,
                'tool_sha256': digest_file(Path(__file__).resolve()),
                'license_status': 'No first-party source license grant established; third-party notices do not license the whole app.'}
    def save(name, data):
        (output / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    save('manifest.json', manifest)
    save('html-entrypoints.json', html)
    save('import-graph.json', {'method': 'lexical-relative-js-imports', 'edges': graph, 'missing': missing})
    save('component-index.json', selected)
    save('route-candidates.json', routes)
    save('mount-token-index.json', mounts)
    save('old-reference-comparison.json', comparison)
    save('verification.json', verification)
    artifacts = [{'file': p.name, 'bytes': p.stat().st_size, 'sha256': digest_file(p)} for p in sorted(output.iterdir()) if p.is_file()]
    save('artifacts.json', artifacts)
    print(json.dumps({'output': str(output), 'version': manifest['version'], 'build': manifest['build'],
                      'archive_sha256': archive_hash, **verification}, ensure_ascii=False, indent=2))
    if not verification['archive_hash_unchanged_during_run'] or integrity['FAIL'] or not verification['entry_script_paths_exist']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
