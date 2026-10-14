from pathlib import Path
import argparse, hashlib, importlib.util, json, shutil, subprocess, time

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-registry-'
REPAIR = PREFIX + 'repair-worker-20261010.'
RBASE = Path(PREFIX + 'repair-baseline-20261010')
RDELIVERY = Path(PREFIX + 'repair-delivery-20261010.json')
NDELIVERY = Path(PREFIX + 'repair-delivery-normalized-20261010.json')
COMBINED = Path(PREFIX + 'delivery-20261010-registry-r2.json')
TEST = 'modules/agent/test/im/hikaru/harness/agent/AgentRegistryAdmissionContractTest.kt'
EVIDENCE = Path(PREFIX + 'evidence-20261010-registry-r1')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
spec = importlib.util.spec_from_file_location('registry_parent', PREFIX + 'parent-20261010.py')
parent = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parent)

def write(p, value):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def archive_failed():
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-registry-2026-10-10/registry-r1'
    assert not archive.exists()
    result = json.loads((EVIDENCE / 'test-results.json').read_text())
    first = result['suites'][0]
    assert first['exit'] == 1 and first['totals']['tests'] == 0
    assert "it is internal in 'im.hikaru.harness.runtime.Context'" in (EVIDENCE / 'AgentRegistryAdmissionContractTest/command.log').read_text()
    shutil.copytree(EVIDENCE, archive)
    write(archive / 'failure.json', dict(status='failed', phase='test-compilation', tests_executed=0, failing_file=TEST, failing_line=204, cause='Cross-module internal Context.isDisposing access in new test', production_acceptance=False, jvm_app_build_run=False, original_baseline_preserved=True))
    files = sorted(p for p in archive.rglob('*') if p.is_file())
    write(archive / 'archive-sha256.json', dict(files=[dict(path=str(p.relative_to(archive)), bytes=p.stat().st_size, sha256=sha(p)) for p in files]))
    for row in json.loads((archive / 'archive-sha256.json').read_text())['files']:
        p = archive / row['path']
        assert p.stat().st_size == row['bytes'] and sha(p) == row['sha256']
    print('FAILED ARCHIVE verified', len(files), archive, flush=True)

def prepare():
    assert not RBASE.exists()
    manifest = json.loads((EVIDENCE / 'source-manifest.json').read_text())
    parent.checker.check(REPO, manifest['inputs'])
    RBASE.mkdir()
    shutil.copy2(REPO / TEST, RBASE / 'test-before.kt')
    write(RBASE / 'baseline.json', dict(inputs=manifest['inputs'], allowed=[TEST], original_baseline_sha256=sha(parent.BASE / 'baseline.json'), contract_sha256=sha(parent.CONTRACT)))
    prompt = f'''你是用户指定的 SHUAIAPI/gpt-6.1-sol / opencodex 实施工作者，继续同一 Registry 合同的一处窄修。
唯一允许修改文件：{REPO / TEST}。
首轮父线程冻结验收在该文件204行编译失败：assertTrue(published.context.isDisposing) 访问 runtime 模块 internal 属性。生产 Registry 已源审，不允许修改任何生产、旧测试、fixture、合同或计划。
先读 {parent.CONTRACT}、该新增测试 catalogPermitCoversRestoreListenerAndRollback 和 {RBASE / 'baseline.json'}。理解代码先 CodeGraph并核对生产路径。
只修跨模块可见性问题。cleanupEntered 真实 effect gate 已证明 dispose 进入且 releaseCleanup 未释放。可把该行改为 assertFalse(published.context.isDisposed)，清楚表达进入cleanup但未完成dispose；保留 catalog busy、entry移除、loaded Session仍在、释放后已disposed与Session关闭、suppressed failure以及其他全部实质断言。不要新增 public isDisposing、反射或生产测试钩子，不弱化断言，不扩大修复。
保留全部dirty/untracked；禁止reset/stash/clean/checkout/commit/push、改用户配置、杀既有进程、派其他工作者。持续任务Comet probe已none，不重跑。只实施与静态检查，禁止test/build/screenshot/native app/PTY或资源运行；父线程在你exit释放写集后独立冻结验收。
交付 {RDELIVERY} 和同名.md：model/provider，repair_baseline_sha256，original_baseline_sha256，contract_sha256，files(唯一test path/before_sha256/after_sha256)，tests_run=false/builds_run=false/screenshots_run=false/native_execution_run=false，write_set_released=true/source_writes_stopped=true，说明修复及限制。交付后不再写入并exit。
'''
    Path(REPAIR + 'prompt').write_text(prompt)
    print('REPAIR PREPARED; one new test only', flush=True)

def dispatch():
    assert not Path(REPAIR + 'dispatch.json').exists()
    argv = ['/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex', 'exec', '--ephemeral', '-C', str(REPO), '--sandbox', 'danger-full-access', '-c', 'approval_policy="never"', '-c', 'model_provider="opencodex"', '-c', 'model_reasoning_effort="high"', '--model', 'SHUAIAPI/gpt-6.1-sol', '--json', '--output-last-message', REPAIR + 'md', '-']
    with Path(REPAIR + 'prompt').open('rb') as inp, Path(REPAIR + 'jsonl').open('wb') as out, Path(REPAIR + 'stderr').open('wb') as err:
        proc = subprocess.Popen(argv, cwd=REPO, stdin=inp, stdout=out, stderr=err)
        write(Path(REPAIR + 'dispatch.json'), dict(pid=proc.pid, argv=argv, model='SHUAIAPI/gpt-6.1-sol', provider='opencodex', time=time.time()))
        print('REPAIR WORKER', proc.pid, 'SHUAIAPI/gpt-6.1-sol / opencodex', flush=True)
        code = proc.wait()
    baseline = json.loads((RBASE / 'baseline.json').read_text())
    parent.checker.check(REPO, baseline['inputs'], [TEST])
    for name in parent.DOCS:
        assert sha(REPO / name) == sha(parent.BASE / 'documentation-before' / name)
    assert sha(parent.BASE / 'baseline.json') == baseline['original_baseline_sha256']
    assert sha(parent.CONTRACT) == baseline['contract_sha256']
    write(Path(REPAIR + 'exit.json'), dict(pid=proc.pid, exit=code, protected_inputs_unchanged=True, time=time.time()))
    print('REPAIR WORKER EXIT', code, flush=True)
    raise SystemExit(code)

def freeze():
    raw = json.loads(RDELIVERY.read_text())
    dispatch = json.loads(Path(REPAIR + 'dispatch.json').read_text())
    assert dispatch['model'] == 'SHUAIAPI/gpt-6.1-sol' and dispatch['provider'] == 'opencodex'
    assert dispatch['argv'][dispatch['argv'].index('--model') + 1] == dispatch['model']
    assert 'model_provider="opencodex"' in dispatch['argv']
    assert raw['model'] == 'gpt-6.1-sol' and raw['provider'] == 'SHUAIAPI' and raw['worker'] == 'opencodex'
    repair = dict(raw, model=dispatch['model'], provider=dispatch['provider'])
    repair['files'] = [dict(row, path=str(Path(row['path']).relative_to(REPO))) for row in raw['files']]
    repair['normalization'] = dict(raw_delivery=str(RDELIVERY), raw_delivery_sha256=sha(RDELIVERY), reported_model=raw['model'], reported_provider=raw['provider'], reported_worker=raw['worker'], routing_source='Exact parent CLI dispatch argv; raw worker delivery preserved')
    write(NDELIVERY, repair)
    baseline = json.loads((RBASE / 'baseline.json').read_text())
    assert json.loads(Path(REPAIR + 'exit.json').read_text())['exit'] == 0
    assert repair['model'] == 'SHUAIAPI/gpt-6.1-sol' and repair['provider'] == 'opencodex'
    assert repair['repair_baseline_sha256'] == sha(RBASE / 'baseline.json')
    assert repair['original_baseline_sha256'] == sha(parent.BASE / 'baseline.json')
    assert repair['contract_sha256'] == sha(parent.CONTRACT)
    assert all(repair[k] is False for k in ('tests_run', 'builds_run', 'screenshots_run', 'native_execution_run'))
    assert repair['write_set_released'] and repair['source_writes_stopped']
    assert len(repair['files']) == 1
    row = repair['files'][0]
    assert row['path'] == TEST and row['before_sha256'] == sha(RBASE / 'test-before.kt') == baseline['inputs'][TEST]
    assert row['after_sha256'] == sha(REPO / TEST) != row['before_sha256']
    parent.checker.check(REPO, baseline['inputs'], [TEST])
    original = json.loads(parent.DELIVERY.read_text())
    assert original['files'][0]['after_sha256'] == sha(REPO / original['files'][0]['path'])
    original['files'][1]['after_sha256'] = row['after_sha256']
    original['repair'] = dict(delivery=str(RDELIVERY), original_test_sha256=row['before_sha256'], repaired_test_sha256=row['after_sha256'], failed_attempt='registry-r1', repaired_attempt='registry-r2')
    original['acceptance_status'] = 'narrow_test_visibility_repair_parent_frozen_acceptance_pending'
    write(COMBINED, original)
    COMBINED.with_suffix('.md').write_text(parent.DELIVERY.with_suffix('.md').read_text() + '\n## Registry r2 test visibility repair\n\n' + RDELIVERY.with_suffix('.md').read_text())
    parent.DELIVERY = COMBINED
    parent.WORKER = REPAIR
    parent.freeze('registry-r2')
    root = parent.paths('registry-r2')[2]
    shutil.copy2(NDELIVERY, root / 'repair-delivery.json')
    shutil.copy2(RDELIVERY, root / 'repair-delivery-raw.json')
    shutil.copy2(RDELIVERY.with_suffix('.md'), root / 'repair-delivery.md')
    shutil.copy2(RBASE / 'baseline.json', root / 'repair-baseline.json')
    shutil.copy2(RBASE / 'test-before.kt', root / 'repair-test-before.kt')
    shutil.copy2(Path(__file__), root / 'repair-verify.py')
    for suffix in ('dispatch.json', 'exit.json', 'jsonl', 'stderr', 'md', 'prompt'):
        shutil.copy2(Path(PREFIX + 'worker-20261010.' + suffix), root / ('original-worker-' + suffix))
    print('REPAIR FROZEN registry-r2; original baseline retained', flush=True)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('action', choices=('archive-failed', 'prepare', 'dispatch', 'freeze'))
    action = p.parse_args().action
    globals()[action.replace('-', '_')]()
