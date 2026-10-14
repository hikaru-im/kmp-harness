from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, importlib.util, json, shutil, subprocess, time

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-a1-'
R1 = Path(PREFIX + 'evidence-20261010-gate-r1')
BASE = Path(PREFIX + 'repair-baseline-20261010')
CONTRACT = Path(PREFIX + 'repair-contract-20261010.md')
WORKER = PREFIX + 'repair-worker-20261010.'
TARGET = 'modules/session/src/im/hikaru/harness/session/SessionAdmission.kt'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def prepare():
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-session-gate-2026-10-10/gate-r1-failed'
    assert not archive.exists() and not BASE.exists() and not CONTRACT.exists()
    manifest = json.loads((R1 / 'source-manifest.json').read_text())
    baseline = json.loads((R1 / 'baseline.json').read_text())
    delivery = json.loads((R1 / 'delivery.json').read_text())
    spec = importlib.util.spec_from_file_location('gate_r1', R1 / 'parent-verify.py')
    parent = importlib.util.module_from_spec(spec); spec.loader.exec_module(parent)
    parent.parent.check(REPO, manifest['inputs'])
    parent.parent.check(Path(manifest['source']), manifest['inputs'])
    summary = json.loads((R1 / 'SessionAdmissionContractTest/summary.json').read_text())
    assert summary['exit'] == 1 and summary['totals']['tests'] == 0
    assert not (R1 / 'jvm-app-build.json').exists()
    assert "Operator '!==' cannot be applied to 'SessionAdmission' and 'CoroutineScope'." in (R1 / 'SessionAdmissionContractTest/command.log').read_text()
    shutil.copytree(R1, archive)
    for row in delivery['files']:
        name = row['path']
        target = archive / 'source-after' / name; target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path(manifest['source']) / name, target)
        assert sha(target) == row['after_sha256']
        if row['before_sha256'] is not None:
            target = archive / 'source-before' / name; target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(Path(PREFIX + 'baseline-20261010') / 'before' / name, target)
            assert sha(target) == row['before_sha256']
    write(archive / 'failure.json', dict(status='compile-failed', attempt='gate-r1', archived_at=datetime.now(timezone.utc).isoformat(), tests_executed=0, first_suite_exit=1, subsequent_suites_run=False, jvm_app_build_run=False, error_file=TARGET, error_line=165, error="Operator '!==' cannot be applied to 'SessionAdmission' and 'CoroutineScope'.", source_inputs=len(manifest['inputs']), acceptance=False, overall_goal='active'))
    (archive / 'failure.md').write_text('# A1 gate-r1 编译失败记录\n\nSessionAdmission.kt:165 的 this 绑定到了 withContext 的 CoroutineScope，比较类型不兼容。实际测试执行0项；其余三类及完整JVM构建未运行。本目录保留失败源码、原始日志及完整输入清单，不作为通过证据。\n')
    rows = [dict(path=str(p.relative_to(archive)), bytes=p.stat().st_size, sha256=sha(p)) for p in sorted(archive.rglob('*')) if p.is_file()]
    write(archive / 'archive-sha256.json', dict(files=rows))
    for row in rows:
        p = archive / row['path']; assert p.stat().st_size == row['bytes'] and sha(p) == row['sha256']
    BASE.mkdir()
    shutil.copy2(REPO / TARGET, BASE / 'SessionAdmission.before.kt')
    write(BASE / 'baseline.json', dict(inputs=manifest['inputs'], source_manifest_sha256=sha(R1 / 'source-manifest.json'), allowed_source_files=[TARGET], before_sha256=sha(REPO / TARGET), source_inputs=2261, unchanged_required=2260, original_delivery_sha256=sha(Path(PREFIX + 'delivery-20261010.json')), original_baseline_sha256=delivery['baseline_sha256'], original_contract_sha256=delivery['contract_sha256'], model='SHUAIAPI/gpt-6.1-sol', provider='opencodex'))
    CONTRACT.write_text('# A1 gate-r1 接收者窄修合同\n\n路由 SHUAIAPI/gpt-6.1-sol / opencodex。父线程已保存真实失败证据。\n\n唯一源码写集：' + TARGET + '\n\n将 release(permit) 内 permit.admission !== this 修改为 permit.admission !== this@SessionAdmission。只读核对本文件类似接收者绑定；不修改API、逻辑、测试、其它源码或配置。保持其余2260输入字节不变。若出现其它明确问题，报告并停止，不扩写集。\n\n只实施和静态检查，禁止test/build/截图/native/PTY、修改Git状态、派其它worker。保留全部dirty/untracked。CodeGraph先于源码定位；本持续任务Comet probe已none，不重跑。\n\n交付 ' + PREFIX + 'repair-delivery-20261010.json 及同名.md。复制原delivery结构，保留原baseline_sha256/contract_sha256及4行before_sha256，仅更新Admission after_sha256。增加repair_baseline_sha256、repair_contract_sha256、repair_files（单行path/before_sha256/after_sha256）、其余2260输入检查结果；tests_run/builds_run/screenshots_run/native_execution_run/pty_execution_run均false，write_set_released/source_writes_stopped均true。交付后停止写入并exit。父线程另冻gate-r2重新串行35项及完整JVM构建。\n')
    prompt = '持续授权的窄修任务。先完整读取 ' + str(CONTRACT) + '\n基线 ' + str(BASE / 'baseline.json') + '\n原交付 ' + PREFIX + 'delivery-20261010.json\n'
    prompt += 'repair_baseline_sha256=' + sha(BASE / 'baseline.json') + '\nrepair_contract_sha256=' + sha(CONTRACT) + '\n按合同一处接收者修复、静态核验、交付并退出。禁止运行动态验收。\n'
    Path(WORKER + 'prompt').write_text(prompt)
    print('ARCHIVED gate-r1 failure:', len(rows), 'files; repair baseline:2261 inputs,1 writable,2260 unchanged', flush=True)

def dispatch():
    binary = '/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
    argv = [binary, 'exec', '--ephemeral', '-C', str(REPO), '--sandbox', 'danger-full-access', '-c', 'approval_policy="never"', '-c', 'model_provider="opencodex"', '-c', 'model_reasoning_effort="high"', '--model', 'SHUAIAPI/gpt-6.1-sol', '--json', '--output-last-message', WORKER + 'md', '-']
    assert not Path(WORKER + 'dispatch.json').exists()
    with Path(WORKER + 'prompt').open('rb') as prompt, Path(WORKER + 'jsonl').open('wb') as log, Path(WORKER + 'stderr').open('wb') as err:
        proc = subprocess.Popen(argv, cwd=REPO, stdin=prompt, stdout=log, stderr=err)
        write(Path(WORKER + 'dispatch.json'), dict(pid=proc.pid, argv=argv, model='SHUAIAPI/gpt-6.1-sol', provider='opencodex', time=time.time()))
        print('REPAIR WORKER', proc.pid, 'SHUAIAPI/gpt-6.1-sol / opencodex', flush=True)
        code = proc.wait()
    write(Path(WORKER + 'exit.json'), dict(pid=proc.pid, exit=code, time=time.time()))
    print('REPAIR WORKER EXIT', code, flush=True)
    raise SystemExit(code)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=('prepare', 'dispatch')); args = parser.parse_args()
    globals()[args.action]()
