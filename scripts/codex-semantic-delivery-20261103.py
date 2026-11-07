#!/usr/bin/env python3
"""Seal and verify the local bounded study output, without executing members."""
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
    args = parser.parse_args()
    root = args.root.resolve()
    scripts = Path(__file__).resolve().parent
    names = ['codex-semantic-v2-20261103.py', 'codex-semantic-slice-20261103.py',
             'codex-semantic-bindings-20261103.mjs', 'codex-semantic-evidence-20261103.mjs',
             'codex-semantic-audit-20261103.mjs', 'codex-semantic-delivery-20261103.py']
    provenance = {
        'scripts': {name: sha((scripts / name).read_bytes()) for name in names},
        'tools_package_lock_sha256': sha((args.tools / 'package-lock.json').read_bytes()),
        'runtime_confirmed': False,
        'scope': 'semantic-restore-06 local study files only; excludes this manifest itself'
    }
    (root / 'delivery-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    rows = []
    for file in sorted(root.rglob('*')):
        if not file.is_file() or file == root / 'artifacts.json':
            continue
        data = file.read_bytes()
        rows.append({'path': file.relative_to(root).as_posix(), 'bytes': len(data), 'sha256': sha(data)})
    (root / 'artifacts.json').write_text(json.dumps(rows, indent=2) + '\n')
    for row in json.loads((root / 'artifacts.json').read_text()):
        data = (root / row['path']).read_bytes()
        if len(data) != row['bytes'] or sha(data) != row['sha256']:
            raise SystemExit('Artifact changed: ' + row['path'])
    print(json.dumps({'artifact_files': len(rows), 'artifact_hashes_match': True}))


if __name__ == '__main__':
    main()
