from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, importlib.util, json, shutil, subprocess, time

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-a1-'
R2 = Path(PREFIX + 'evidence-20261010-gate-r2')
BASE = Path(PREFIX + 'test-repair-baseline-20261010')
CONTRACT = Path(PREFIX + 'test-repair-contract-20261010.md')
WORKER = PREFIX + 'test-repair-worker-20261010.'
TARGET = 'modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt'
DELIVERY = Path(PREFIX + 'test-repair-delivery-20261010.json')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
spec = importlib.util.spec_from_file_location('r2_parent', Path(PREFIX + 'parent-r2-20261010.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def expected(before):
    result = before.replace(b'import kotlinx.coroutines.withTimeout\n', b'import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeout\n')
    for signal in (b'noticed', b'reporting'):
        old = b'            withTimeout(10_000) { ' + signal + b'.await() }'
        new = b'            withContext(Dispatchers.Default) { withTimeout(10_000) { ' + signal + b'.await() } }'
        assert before.count(old) == 1
        result = result.replace(old, new)
    return result

def prepare():
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-session-gate-2026-10-10/gate-r2-failed'
    assert not archive.exists() and not BASE.exists() and not CONTRACT.exists()
    manifest = json.loads((R2 / 'source-manifest.json').read_text())
    gate.parent.check(REPO, manifest['inputs'])
    gate.parent.check(Path(manifest['source']), manifest['inputs'])
    summary = json.loads((R2 / 'SessionAdmissionContractTest/summary.json').read_text())
    assert summary['exit'] == 1 and summary['totals'] == dict(tests=8, failures=0, errors=2, skipped=0)
    assert not (R2 / 'jvm-app-build.json').exists()
    shutil.copytree(R2, archive)
    delivery = json.loads((R2 / 'delivery.json').read_text())
    for row in delivery['files']:
        name = row['path']
        out = archive / 'source-after' / name; out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path(manifest['source']) / name, out)
        assert sha(out) == row['after_sha256']
        if row['before_sha256'] is not None:
            out = archive / 'source-before' / name; out.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(Path(PREFIX + 'baseline-20261010/before') / name, out)
            assert sha(out) == row['before_sha256']
    write(archive / 'failure.json', dict(status='test-errors', attempt='gate-r2', archived_at=datetime.now(timezone.utc).isoformat(), tests_executed=8, passed=6, failures=0, errors=2, skipped=0, error_methods=['inFlightAppendMakesReservationBusyThroughNotice', 'cancellationAndFailuresReleaseOnlyOwnPermits'], diagnosis='suspected virtual-time timeout awaiting Default dispatcher signal; not yet confirmed by repaired test', subsequent_suites_run=False, jvm_app_build_run=False, acceptance=False, overall_goal='active', validator_method_name_normalization_needed=True))
    (archive / 'failure.md').write_text('# A1 gate-r2 测试失败记录\n\n编译通过；新增8项实际执行，6通过、2虚拟时间超时。旧三类及完整JVM构建未运行。两处跨Default信号等待需要核对调度；未证明生产逻辑通过。XML原始方法名带()，下一轮验证器保留原名并规范化后核对固定清单。失败输入、日志和验收脚本原样保留。整体目标active。\n')
    files = sorted(p for p in archive.rglob('*') if p.is_file())
    write(archive / 'archive-sha256.json', dict(files=[dict(path=str(p.relative_to(archive)), bytes=p.stat().st_size, sha256=sha(p)) for p in files]))
    for row in json.loads((archive / 'archive-sha256.json').read_text())['files']:
        p = archive / row['path']; assert sha(p) == row['sha256'] and p.stat().st_size == row['bytes']
    BASE.mkdir()
    shutil.copy2(REPO / TARGET, BASE / 'SessionAdmissionContractTest.before.kt')
    write(BASE / 'baseline.json', dict(inputs=manifest['inputs'], before_sha256=sha(REPO / TARGET), target=TARGET, unchanged_required=2260, prior_delivery_sha256=sha(Path(PREFIX + 'repair-delivery-20261010.json'))))
    CONTRACT.write_text('# A1 跨调度等待测试窄修\n\n宿主指定SHUAIAPI/gpt-6.1-sol / opencodex。唯一源码写集：' + TARGET + '\n\n只新增withContext import，并将noticed.await()与reporting.await()的外层withTimeout(10_000)两行包入withContext(Dispatchers.Default)，保持原10秒期限、Deferred屏障、8方法和所有断言，其余2260输入字节不变。精确替换为：\n\n    withContext(Dispatchers.Default) { withTimeout(10_000) { noticed.await() } }\n    withContext(Dispatchers.Default) { withTimeout(10_000) { reporting.await() } }\n\n禁止其它改动、test/build/截图/native/PTY、Git状态写入、派其它worker。只实施与静态检查；保留全部dirty/untracked。Comet已none，本持续任务不重跑；理解代码先CodeGraph。\n\n交付 ' + str(DELIVERY) + ' 与同名.md：复制原repair-delivery结构及全部原始before SHA，更新测试文件after SHA；保留生产repair字段，增加test_repair_baseline_sha256、test_repair_contract_sha256、test_repair_files单行path/before_sha256/after_sha256，以及其余2260输入核对结果。tests_run/builds_run/screenshots_run/native_execution_run/pty_execution_run均false，write_set_released/source_writes_stopped均true。写入交付后停止写入并退出，父线程独立冻结gate-r3串行35项及完整JVM构建。\n')
    prompt = '你是本持续授权任务的测试窄修工作者，指定路由SHUAIAPI/gpt-6.1-sol / opencodex。先完整读取合同 ' + str(CONTRACT) + ' 和基线 ' + str(BASE / 'baseline.json') + '。原交付 ' + PREFIX + 'repair-delivery-20261010.json。合同SHA=' + sha(CONTRACT) + '；基线SHA=' + sha(BASE / 'baseline.json') + '。父线程已保存gate-r2失败证据。只修合同两行及import，不重做生产设计或扩大写集；不运行测试/构建。交付后退出。'
    Path(WORKER + 'prompt').write_text(prompt)
    print('ARCHIVED gate-r2', len(files), 'files; PREPARED test-only repair', flush=True)

def dispatch():
    binary = '/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
    argv = [binary, 'exec', '--ephemeral', '-C', str(REPO), '--sandbox', 'danger-full-access', '-c', 'approval_policy="never"', '-c', 'model_provider="opencodex"', '-c', 'model_reasoning_effort="high"', '--model', 'SHUAIAPI/gpt-6.1-sol', '--json', '--output-last-message', WORKER + 'md', '-']
    with Path(WORKER + 'prompt').open('rb') as prompt, Path(WORKER + 'jsonl').open('wb') as log, Path(WORKER + 'stderr').open('wb') as err:
        proc = subprocess.Popen(argv, cwd=REPO, stdin=prompt, stdout=log, stderr=err)
        write(Path(WORKER + 'dispatch.json'), dict(pid=proc.pid, argv=argv, model='SHUAIAPI/gpt-6.1-sol', provider='opencodex', time=time.time()))
        print('TEST REPAIR WORKER', proc.pid, flush=True); code = proc.wait()
    write(Path(WORKER + 'exit.json'), dict(pid=proc.pid, exit=code, time=time.time()))
    print('TEST REPAIR WORKER EXIT', code, flush=True)
    raise SystemExit(code)

def verify():
    assert json.loads(Path(WORKER + 'exit.json').read_text())['exit'] == 0
    baseline = json.loads((BASE / 'baseline.json').read_text())
    delivery = json.loads(DELIVERY.read_text())
    prior = Path(PREFIX + 'repair-delivery-20261010.json')
    assert sha(prior) == baseline['prior_delivery_sha256']
    assert delivery['test_repair_baseline_sha256'] == sha(BASE / 'baseline.json')
    assert delivery['test_repair_contract_sha256'] == sha(CONTRACT)
    gate.parent.check(REPO, baseline['inputs'], [TARGET])
    assert (REPO / TARGET).read_bytes() == expected((BASE / 'SessionAdmissionContractTest.before.kt').read_bytes())
    assert len(baseline['inputs']) == 2261
    row = delivery['test_repair_files']; assert len(row) == 1 and row[0]['path'] == TARGET
    assert row[0]['before_sha256'] == baseline['before_sha256'] and row[0]['after_sha256'] == sha(REPO / TARGET)
    original = json.loads(prior.read_text())
    for key, value in original.items():
        if key != 'files': assert delivery[key] == value, key
    for row in delivery['files']:
        old = next(r for r in original['files'] if r['path'] == row['path'])
        assert row['before_sha256'] == old['before_sha256'] and row['after_sha256'] == sha(REPO / row['path'])
    print('VERIFIED exact two waits + import; other 2260 inputs unchanged', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['prepare', 'dispatch', 'verify']); args = parser.parse_args(); globals()[args.action]()
