#!/usr/bin/env python3
"""Independently check evidence source spans, record tools, and seal study files."""
import argparse
import hashlib
import json
from pathlib import Path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--tools', type=Path, required=True)
    parser.add_argument('--prettier', type=Path, required=True)
    args = parser.parse_args()
    root = args.root
    counts = {'javascript_spans': 0, 'css_declarations': 0}

    def check(text, offset, byte_offset, line, column, source):
        # All source offsets use JS UTF-16 units; compare UTF-16 encoded bytes.
        encoded = text.encode('utf-16-le')
        prefix = encoded[:offset * 2].decode('utf-16-le')
        actual = encoded[offset * 2:offset * 2 + len(source.encode('utf-16-le'))].decode('utf-16-le')
        assert actual == source, 'Source span mismatch'
        assert len(prefix.encode()) == byte_offset, 'Byte offset mismatch'
        lines = prefix.split('\n')
        assert len(lines) == line, 'Line mismatch'
        assert len(lines[-1].encode('utf-16-le')) // 2 == column, 'Column mismatch'

    evidence = json.loads((root / 'source-evidence.json').read_text())
    for file in evidence['files']:
        text = (root / 'original' / file['file']).read_text()

        def walk(value):
            if isinstance(value, list):
                for child in value:
                    walk(child)
            elif isinstance(value, dict):
                if all(key in value for key in ('start', 'end', 'source', 'byte_offset', 'line', 'column')):
                    check(text, value['start'], value['byte_offset'], value['line'], value['column'], value['source'])
                    assert value['end'] - value['start'] == len(value['source'].encode('utf-16-le')) // 2
                    counts['javascript_spans'] += 1
                for child in value.values():
                    walk(child)
        walk(file['requests'])

    for base in (root, root / 'scoped-css'):
        selection = json.loads((base / 'selection.json').read_text())
        for row in selection['files']:
            original = base / 'original' / row['original_path']
            assert sha(original.read_bytes()) == row['original_sha256']
            assert original.stat().st_mode & 0o222 == 0
        css = json.loads((base / 'css-evidence.json').read_text())
        for file in css['files']:
            text = (base / 'original' / file['original_path']).read_text()
            for row in file['evidence']:
                check(text, row['utf16_offset'], row['byte_offset'], row['line'], row['column'], row['source'])
                counts['css_declarations'] += 1

    scripts = Path(__file__).resolve().parent
    provenance = {
        'scope': 'navigation-restore-09 local static study; no app code execution',
        'scripts': {p.name: sha(p.read_bytes()) for p in sorted(scripts.glob('codex-navigation-*-20261103.*')) if p.is_file()},
        'tools_package_lock_sha256': sha((args.tools / 'package-lock.json').read_bytes()),
        'prettier_package_sha256': sha((args.prettier / 'package.json').read_bytes()),
        'prettier_version': json.loads((args.prettier / 'package.json').read_text())['version'],
        'evidence_span_checks': counts,
        'original_hashes_and_readonly_modes_match': True,
        'runtime_confirmed': False,
    }
    (root / 'delivery-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    rows = []
    for file in sorted(root.rglob('*')):
        if file.is_file() and file != root / 'artifacts.json':
            data = file.read_bytes()
            rows.append({'path': file.relative_to(root).as_posix(), 'bytes': len(data), 'sha256': sha(data)})
    (root / 'artifacts.json').write_text(json.dumps(rows, indent=2) + '\n')
    for row in json.loads((root / 'artifacts.json').read_text()):
        data = (root / row['path']).read_bytes()
        assert len(data) == row['bytes'] and sha(data) == row['sha256'], row['path']
    print(json.dumps({'artifact_files': len(rows), 'artifact_hashes_match': True, **counts}))


if __name__ == '__main__':
    main()
