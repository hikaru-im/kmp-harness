from pathlib import Path
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
BASE = Path('/tmp/harness-thread-message-final-baseline-20261010')
DELIVERY = Path('/tmp/harness-thread-message-final-delivery-20261010.json')
PREFIX = '/tmp/harness-thread-message-final-'
SOURCE = Path(PREFIX + 'source-20261010')
BUILD = Path(PREFIX + 'build-20261010')
EVIDENCE = Path(PREFIX + 'evidence-20261010')
TEST = 'apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt'
SUITES = [('ThreadMessageVisualInteractionTest', 14), ('LiveConversationInteractionTest', 11), ('HarnessAppThreadPanelsShellTest', 8)]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + chr(10))

spec = importlib.util.spec_from_file_location('terminal_parent', REPO / 'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parent)

def protected(text):
    start = text.index('        // getBoundingBox()')
    end = text.index('        val glyphs', start)
    assert all(line.startswith('        //') for line in text[start:end].splitlines())
    return text[:start] + text[end:]

def freeze():
    exit_record = json.loads(Path(PREFIX + 'worker-20261010.exit.json').read_text())
    assert exit_record['exit'] == 0
    base = json.loads((BASE / 'baseline.json').read_text())
    delivery = json.loads(DELIVERY.read_text())
    assert delivery['model'] == 'SHUAIAPI/gpt-6.1-sol' and delivery['provider'] == 'opencodex'
    assert delivery['baseline_sha256'] == sha(BASE / 'baseline.json')
    assert delivery['contract_sha256'] == base['contract_sha256'] == sha(BASE / 'contract.md')
    assert all(delivery[field] is False for field in ('tests_run', 'builds_run', 'screenshots_run'))
    assert all(delivery[field] is True for field in ('write_set_released', 'source_writes_stopped'))
    records = delivery.get('files') or delivery.get('changed_files')
    allowed = {row['path']: row['before_sha256'] for row in base['files']}
    assert len(records) == 3 and {row['path'] for row in records} == set(allowed)
    for row in records:
        assert row['before_sha256'] == allowed[row['path']]
        assert row['after_sha256'] == sha(REPO / row['path'])
        if row['path'] != TEST:
            assert row['before_sha256'] == row['after_sha256']
    before = (BASE / 'before' / TEST).read_text()
    after = (REPO / TEST).read_text()
    assert before != after and protected(before) == protected(after)
    assert all(word in after.lower() for word in ('strut', 'tight', 'raster', 'baseline'))
    methods = re.findall(r'@Test\s+fun\s+(\w+)\s*\(', after)
    assert len(methods) == 14 and methods == re.findall(r'@Test\s+fun\s+(\w+)\s*\(', before)
    parent.check(REPO, base['inputs'], [TEST])
    parent.check(Path(base['base_source']), base['inputs'])
    assert not any(p.exists() for p in (SOURCE, BUILD, EVIDENCE))
    shutil.copytree(base['base_source'], SOURCE, symlinks=True, ignore=shutil.ignore_patterns('build', '.git', 'node_modules', '__pycache__', 'captures'))
    shutil.copy2(REPO / TEST, SOURCE / TEST)
    inputs = dict(base['inputs']); inputs[TEST] = sha(SOURCE / TEST)
    parent.check(SOURCE, inputs)
    shutil.copytree(base['base_build'], BUILD, symlinks=True, ignore=shutil.ignore_patterns('docs'))
    (BUILD / 'docs').symlink_to(SOURCE / 'docs', target_is_directory=True)
    EVIDENCE.mkdir()
    manifest = dict(source=str(SOURCE), build=str(BUILD), inputs=inputs, baseline_sha256=sha(BASE / 'baseline.json'), delivery_sha256=sha(DELIVERY), suites=SUITES)
    write(EVIDENCE / 'source-manifest.json', manifest)
    for src, target in [(BASE / 'baseline.json', 'baseline.json'), (BASE / 'contract.md', 'contract.md'), (DELIVERY, 'delivery.json'), (DELIVERY.with_suffix('.md'), 'delivery.md'), (Path(__file__), 'parent-verify.py')]:
        shutil.copy2(src, EVIDENCE / target)
    for suffix in ('dispatch.json', 'exit.json', 'jsonl', 'stderr', 'md'):
        shutil.copy2(Path(PREFIX + 'worker-20261010.' + suffix), EVIDENCE / ('worker-' + suffix))
    shutil.copy2(REPO / 'docs/desktop-ui/verification/thread-message-2026-10-08/parent-compose-strut-bytecode.txt', EVIDENCE / 'compose-strut-bytecode.txt')
    write(EVIDENCE / 'protected-source-review.json', dict(inputs=len(inputs), unchanged_inputs=len(inputs)-1, production_unchanged=True, executable_test_bytes_unchanged=True, methods=methods, old_provisional_production_delta='private MessageCard -> internal MessageCard for subagent reuse', historical_33_reused=False))
    print('FROZEN', len(inputs), 'inputs; comment-only edit', flush=True)

def run():
    manifest = json.loads((EVIDENCE / 'source-manifest.json').read_text())
    env = os.environ.copy()
    env['KOTLIN_CLI_JAVA_OPTIONS'] = '-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
    native = EVIDENCE / 'native-tmp'; native.mkdir()
    env['JAVA_TOOL_OPTIONS'] = '-Djava.awt.headless=true -Djava.io.tmpdir=' + str(native)
    env['HARNESS_THREAD_MESSAGE_CAPTURE_DIR'] = str(EVIDENCE / 'message-captures')
    env['HARNESS_APP_THREAD_SHELL_CAPTURE_DIR'] = str(EVIDENCE / 'shell-captures')
    write(EVIDENCE / 'environment.json', {key: env[key] for key in ('KOTLIN_CLI_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', 'HARNESS_THREAD_MESSAGE_CAPTURE_DIR', 'HARNESS_APP_THREAD_SHELL_CAPTURE_DIR')})
    results = []
    for short, expected in SUITES:
        parent.check(SOURCE, manifest['inputs'])
        name = 'im.hikaru.harness.desktop.' + short
        folder = EVIDENCE / short; folder.mkdir()
        command = ['./kotlin', 'test', '--build-dir=' + str(BUILD), '-p', 'jvm', '-m', 'jvm-app', '--include-classes=' + name]
        started = time.time()
        print('START', short, flush=True)
        with (folder / 'command.log').open('w') as log:
            result = subprocess.run(command, cwd=SOURCE, env=env, stdout=log, stderr=subprocess.STDOUT)
        totals = dict(tests=0, failures=0, errors=0, skipped=0); methods=[]
        for xml in BUILD.rglob('TEST-*.xml'):
            if xml.stat().st_mtime < started: continue
            suite = ET.parse(xml).getroot()
            assert suite.tag == 'testsuite'
            cases = list(suite.iter('testcase'))
            assert len(cases) == int(suite.get('tests', 0))
            assert all(case.get('classname') == name for case in cases)
            shutil.copy2(xml, folder / xml.name)
            methods.extend(case.get('name') for case in cases)
            for field in totals: totals[field] += int(suite.get(field, 0))
            for case in cases:
                for issue in case:
                    if issue.tag in ('failure','error'): print('FAIL',case.get('name'),issue.get('message','')[:2500],flush=True)
        summary=dict(class_name=name,command=command,exit=result.returncode,expected=expected,totals=totals,methods=methods,elapsed_seconds=round(time.time()-started,2))
        write(folder/'summary.json',summary);results.append(summary)
        write(EVIDENCE/'test-results.json',dict(status='in-progress',suites=results))
        print('RESULT',short,totals,'exit',result.returncode,flush=True)
        if result.returncode or totals != dict(tests=expected,failures=0,errors=0,skipped=0) or len(set(methods)) != expected:
            print(chr(10).join((folder/'command.log').read_text().splitlines()[-45:]),flush=True)
            raise SystemExit(1)
    parent.check(SOURCE,manifest['inputs']);parent.check(REPO,manifest['inputs'])
    write(EVIDENCE/'test-results.json',dict(status='passed',suites=results,tests=33,failures=0,errors=0,skipped=0))
    print('PASSED current source 33/33; captures pending review',flush=True)

if __name__ == '__main__':
    freeze();run()
