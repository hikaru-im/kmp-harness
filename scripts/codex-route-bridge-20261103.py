#!/usr/bin/env python3
"""Extract the exact route bridge separately; analyze bytes without executing them."""
import argparse
import hashlib
import json
import struct
import subprocess
from pathlib import Path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--app', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--expected-sha256', required=True)
    p.add_argument('--prettier', type=Path, required=True)
    p.add_argument('--member', default='webview/assets/primary-app-route-components.electron-2b41ea988b65.js')
    p.add_argument('--readable', default='primary-app-route-components.js')
    a = p.parse_args()
    archive = a.app / 'Contents/Resources/app.asar'
    if sha(archive.read_bytes()) != a.expected_sha256:
        raise SystemExit('Source archive hash mismatch')
    packed = a.member
    if packed.startswith('/') or '..' in Path(packed).parts or Path(a.readable).name != a.readable:
        raise SystemExit('Expected archive-relative member and plain output filename')
    with archive.open('rb') as f:
        _, header_size, _, json_size = struct.unpack('<4I', f.read(16))
        entry = json.loads(f.read(json_size))
        for part in packed.split('/'):
            entry = entry['files'][part]
        if entry.get('unpacked') or 'link' in entry:
            raise SystemExit('Expected packed member')
        f.seek(8 + header_size + int(entry['offset']))
        data = f.read(int(entry['size']))
    if len(data) != int(entry['size']) or sha(data) != entry['integrity']['hash']:
        raise SystemExit('Member integrity mismatch')
    a.output.mkdir(parents=True, exist_ok=False)
    original = a.output / 'original' / packed
    original.parent.mkdir(parents=True)
    original.write_bytes(data)
    original.chmod(0o444)
    formatted = a.output / 'formatted' / a.readable
    formatted.parent.mkdir()
    formatted.write_bytes(data)
    subprocess.run(['node', str(a.prettier), '--write', '--ignore-path', '/dev/null',
                    '--no-config', '--parser', 'babel', str(formatted)], check=True)
    row = {'source_asar': str(archive.resolve()), 'source_asar_sha256': a.expected_sha256,
           'original_path': packed, 'original_sha256': sha(data),
           'original_bytes': len(data), 'runtime_confirmed': False}
    (a.output / 'selection.json').write_text(json.dumps(row, indent=2) + '\n')
    if sha(archive.read_bytes()) != a.expected_sha256:
        raise SystemExit('Source archive changed')
    print(json.dumps(row))


if __name__ == '__main__':
    main()
