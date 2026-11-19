#!/usr/bin/env python3
"""Extract a new bounded navigation study; archive members remain unexecuted data."""
import argparse
import hashlib
import json
import struct
import subprocess
from pathlib import Path

FILES = {
    'webview/assets/app-initial-18b189e867e7.js': 'shell/app-initial.js',
    'webview/assets/app-shared-601019a330d7.js': 'shell/app-shared.js',
    'webview/assets/app-primary-75d53eef2374.js': 'shell/app-primary.js',
    'webview/assets/app-runtime-b179428fcc1e.js': 'shell/app-runtime.js',
    'webview/assets/rolldown-runtime-428824ca0286.js': 'shell/rolldown-runtime.js',
    'webview/assets/fd9b7fd80db1.css': 'styles/base.css',
    'webview/assets/b74e19ff7ff4.css': 'styles/app.css',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--app', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--prettier', type=Path, required=True)
    p.add_argument('--expected-sha256', required=True)
    p.add_argument('--member', action='append', default=[], metavar='PACKED=READABLE',
                   help='Extract only these explicit members instead of the default set')
    a = p.parse_args()
    files = dict(pair.split('=', 1) for pair in a.member) if a.member else FILES
    for packed, readable in files.items():
        if any(Path(value).is_absolute() or '..' in Path(value).parts
               for value in (packed, readable)):
            raise SystemExit('Expected safe relative member and readable paths')
    archive = a.app / 'Contents/Resources/app.asar'
    if sha(archive.read_bytes()) != a.expected_sha256:
        raise SystemExit('Source archive hash mismatch')
    a.output.mkdir(parents=True, exist_ok=False)
    rows = []
    with archive.open('rb') as f:
        _, header_size, _, json_size = struct.unpack('<4I', f.read(16))
        tree = json.loads(f.read(json_size))
        for packed, readable in files.items():
            entry = tree
            for part in packed.split('/'):
                entry = entry['files'][part]
            if entry.get('unpacked') or 'link' in entry:
                raise SystemExit('Expected packed member: ' + packed)
            f.seek(8 + header_size + int(entry['offset']))
            data = f.read(int(entry['size']))
            if len(data) != int(entry['size']) or sha(data) != entry['integrity']['hash']:
                raise SystemExit('Member integrity mismatch: ' + packed)
            original = a.output / 'original' / packed
            original.parent.mkdir(parents=True, exist_ok=True)
            original.write_bytes(data)
            original.chmod(0o444)
            formatted = a.output / 'formatted' / readable
            formatted.parent.mkdir(parents=True, exist_ok=True)
            formatted.write_bytes(data)
            rows.append({'original_path': packed, 'readable_path': readable,
                         'original_sha256': sha(data), 'original_bytes': len(data)})
    (a.output / 'selection.json').write_text(json.dumps({
        'source_asar': str(archive.resolve()), 'source_asar_sha256': a.expected_sha256,
        'files': rows, 'runtime_confirmed': False}, indent=2) + '\n')
    subprocess.run(['node', str(a.prettier), '--write', '--ignore-path', '/dev/null',
                    '--no-config', *[str(a.output / 'formatted' / x) for x in files.values()]], check=True)
    if sha(archive.read_bytes()) != a.expected_sha256:
        raise SystemExit('Source archive changed')
    print(json.dumps({'members': len(rows), 'source_asar_hash_matches': True}))


if __name__ == '__main__':
    main()
