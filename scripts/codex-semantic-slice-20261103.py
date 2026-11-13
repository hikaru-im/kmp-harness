#!/usr/bin/env python3
"""Reproducible bounded Codex shell/navigation extraction and binding analysis."""
import argparse
import hashlib
import json
import runpy
import struct
import subprocess
from pathlib import Path


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--tools', type=Path, required=True)
    parser.add_argument('--expected-sha256', required=True)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    files = runpy.run_path(str(here / 'codex-semantic-v2-20261103.py'))['FILES']
    archive = args.app / 'Contents/Resources/app.asar'
    if sha(archive.read_bytes()) != args.expected_sha256:
        raise SystemExit('ASAR does not match selected build')
    args.output.mkdir(parents=True, exist_ok=False)
    rows = []
    with archive.open('rb') as stream:
        _, header_size, _, json_size = struct.unpack('<4I', stream.read(16))
        tree = json.loads(stream.read(json_size))
        base = 8 + header_size
        for packed, readable in files.items():
            entry = tree
            for part in packed.split('/'):
                entry = entry['files'][part]
            if entry.get('unpacked') or 'link' in entry:
                raise ValueError('Expected packed member')
            stream.seek(base + int(entry['offset']))
            data = stream.read(int(entry['size']))
            if len(data) != int(entry['size']) or sha(data) != entry['integrity']['hash']:
                raise ValueError('Member integrity failure: ' + packed)
            original = args.output / 'original' / packed
            original.parent.mkdir(parents=True, exist_ok=True)
            original.write_bytes(data)
            original.chmod(0o444)
            formatted = args.output / 'formatted' / readable
            formatted.parent.mkdir(parents=True, exist_ok=True)
            formatted.write_bytes(data)
            rows.append({'original_path': packed, 'readable_path': readable,
                         'original_sha256': sha(data), 'original_bytes': len(data),
                         'original_read_only_mode': '0444'})
    selection = {'source_asar': str(archive.resolve()),
                 'source_asar_sha256': args.expected_sha256,
                 'files': rows, 'runtime_confirmed': False}
    (args.output / 'selection.json').write_text(json.dumps(selection, indent=2) + '\n')
    prettier = Path('/Users/hikaru/.npm/_npx/7e4347a8d51f6fca/node_modules/prettier/bin/prettier.cjs')
    # Explicitly disable project ignores/config; source is never loaded as code.
    subprocess.run(['node', str(prettier), '--write', '--ignore-path', '/dev/null',
                    '--no-config', '--parser', 'babel',
                    *map(str, (args.output / 'formatted').rglob('*.js'))], check=True)
    subprocess.run(['node', str(here / 'codex-semantic-bindings-20261103.mjs'),
                    str(args.tools.resolve()), str(args.output.resolve())], check=True)
    verification = json.loads((args.output / 'binding-verification.json').read_text())
    verification['all_original_hashes_match'] = all(
        sha((args.output / 'original' / row['original_path']).read_bytes()) == row['original_sha256']
        for row in rows)
    verification['source_asar_hash_unchanged'] = sha(archive.read_bytes()) == args.expected_sha256
    (args.output / 'binding-verification.json').write_text(json.dumps(verification, indent=2) + '\n')
    # Hash reproducibility inputs and pinned tool lock without copying package code.
    provenance = {'tools_package_lock_sha256': sha((args.tools / 'package-lock.json').read_bytes()),
                  'scripts': {name: sha((here / name).read_bytes()) for name in
                              ['codex-semantic-v2-20261103.py', 'codex-semantic-slice-20261103.py',
                               'codex-semantic-bindings-20261103.mjs']}}
    (args.output / 'tool-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print(json.dumps(verification, indent=2))


if __name__ == '__main__':
    main()
