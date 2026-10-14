from pathlib import Path
import argparse, hashlib, importlib.util, json, shutil, subprocess, time

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
ORIGINAL = Path('/tmp/harness-worktree-chat-archive-driver-parent-20261010.py')
PARENT = Path('/tmp/harness-worktree-chat-archive-driver-repair-parent-20261010.py')
PREFIX = '/tmp/harness-worktree-chat-archive-driver-repair-worker-20261010.'
DELIVERY = Path('/tmp/harness-worktree-chat-archive-driver-repair-delivery-20261010.json')
REPAIR = Path('/tmp/harness-worktree-chat-archive-driver-repair-baseline-20261010')
TEST = 'modules/agent-loop/test/im/hikaru/harness/agent/loop/AgentDriverAdmissionContractTest.kt'
TARGET = 'cancelledDriverAndDisposedScopeNeverRestartQueuedTurns'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(p, value):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, indent=2, ensure_ascii=False) + chr(10))

def parent():
    spec = importlib.util.spec_from_file_location('driver_parent', ORIGINAL)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

def blocks(source):
    chunks = source.split('    @Test' + chr(10))[1:]
    return {chunk.split('fun ', 1)[1].split('(', 1)[0]: chunk for chunk in chunks}

def stage():
    mod = parent()
    evidence = mod.paths('driver-r1')[2]
    assert (evidence / 'parent-review.json').exists()
    review = json.loads((evidence / 'parent-review.json').read_text())
    assert not review['accepted']
    assert not REPAIR.exists() and not PARENT.exists()
    baseline = json.loads((mod.BASE / 'baseline.json').read_text())
    mod.checker.check(REPO, baseline['inputs'], mod.ALLOWED)
    inputs = dict(baseline['inputs'])
    for name in mod.ALLOWED:
        inputs[name] = sha(REPO / name)
    REPAIR.mkdir()
    shutil.copy2(REPO / TEST, REPAIR / 'test-before.kt')
    write(REPAIR / 'baseline.json', {'inputs': inputs, 'allowed': [TEST], 'target_method': TARGET})
    archive = REPO / 'docs/desktop-ui/verification/worktree-chat-archive-driver-2026-10-10/driver-r1-static-rejected'
    assert not archive.exists()
    shutil.copytree(evidence, archive)
    write(archive / 'acceptance.json', {'status': 'static-rejected', 'tests_run': False, 'builds_run': False, 'reason': review['findings'], 'overall_goal': 'active'})
    files = sorted(p for p in archive.rglob('*') if p.is_file())
    write(archive / 'archive-sha256.json', {'files': [{'path': str(p.relative_to(archive)), 'bytes': p.stat().st_size, 'sha256': sha(p)} for p in files]})
    text = ORIGINAL.read_text().replace("DELIVERY = Path('/tmp/harness-worktree-chat-archive-driver-delivery-20261010.json')", "DELIVERY = Path('/tmp/harness-worktree-chat-archive-driver-repair-delivery-20261010.json')")
    text = text.replace("WORKER = '/tmp/harness-worktree-chat-archive-driver-worker-20261010.'", "WORKER = '/tmp/harness-worktree-chat-archive-driver-repair-worker-20261010.'")
    assert str(DELIVERY) in text and PREFIX in text
    PARENT.write_text(text)
    prompt = f'''你是用户持续指定的 SHUAIAPI/gpt-6.1-sol / opencodex 工作者。父线程已完成 driver 生产实现静态审查；初次交付被静态拒绝，未运行 test/build。现在仅修新增测试，不改生产。
唯一写集：{TEST}。修复基线 {REPAIR / 'baseline.json'}，原合同 {mod.CONTRACT}，父审查 {evidence / 'parent-review.json'}。使用 CodeGraph 定位后核对生产路径，避免 verification 副本。持续任务 probe 已 none，不重跑。不重做架构分析，不派工作者，不改旧测试、配置或源码，不 reset/stash/clean/checkout/commit/push。
两个确定问题：
1. {TARGET} 完成 agent.context.dispose 后，其 SessionHandle 已 retire 精确 Session。resumeAttempt 首先 acquireOperation，正确抛 SessionException，code=SessionErrorCode.CLOSED_LIFECYCLE；reserve 正确返回 Rejected(SessionAdmissionRejection.CLOSED_SESSION)。必须准确验证拒绝、调用次数/保留队列不变，禁止把已关闭 Session 的 reservation 写为 Reserved。不得为测试改生产 gate。
2. 两个测试已完整 dispose Agent Context，Fixture.dispose 再 handle.dispose 时 Registry.detach 的普通 Agent.cancel 会传播上述 CLOSED_LIFECYCLE；原夹具跳过 runtime.context.dispose。夹具应准确区分有意提前完整关闭和普通路径，在前者明确断言公开 context.isDisposed 与精确错误 code；总是释放 Runtime，保留并传播任何非预期错误。不能广泛吞异常，不得声称 owner drain 已完成。
允许改 TEST imports、Fixture.dispose 和上述一个方法；其余八个 @Test 方法字节保持，固定九方法和全部行为覆盖保持。无需新生产 hook、反射、sleep 或弱化断言。源码只做静态核对；禁止 test/build/screenshot/native app/PTY。
交付 {DELIVERY} 及同名.md。字段和原交付一致：model=SHUAIAPI/gpt-6.1-sol、provider=opencodex、baseline_sha256 为原 {mod.BASE / 'baseline.json'} SHA、contract_sha256、files 两项且 path 为原两文件；before_sha256 仍使用原合同 baseline 的 before（新测试 None），after_sha256 为当前 bytes。四运行字段 false，write_set_released/source_writes_stopped true。额外记录 repair_baseline_sha256、repair_before/after test SHA 和修复说明。交付后停止写入并立即退出。避免重复无关读与复杂自造静态解析器。
'''
    Path(PREFIX + 'prompt').write_text(prompt)
    print('STAGED test-only repair; rejected evidence archived; production frozen', flush=True)

def dispatch():
    assert REPAIR.exists() and not Path(PREFIX + 'dispatch.json').exists()
    mod = parent()
    argv = ['/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex', 'exec', '--ephemeral', '-C', str(REPO), '--sandbox', 'danger-full-access', '-c', 'approval_policy="never"', '-c', 'model_provider="opencodex"', '-c', 'model_reasoning_effort="high"', '--model', 'SHUAIAPI/gpt-6.1-sol', '--json', '--output-last-message', PREFIX + 'md', '-']
    with Path(PREFIX + 'prompt').open('rb') as inp, Path(PREFIX + 'jsonl').open('wb') as out, Path(PREFIX + 'stderr').open('wb') as err:
        proc = subprocess.Popen(argv, cwd=REPO, stdin=inp, stdout=out, stderr=err)
        write(Path(PREFIX + 'dispatch.json'), {'pid': proc.pid, 'argv': argv, 'model': 'SHUAIAPI/gpt-6.1-sol', 'provider': 'opencodex', 'time': time.time()})
        print('REPAIR WORKER', proc.pid, 'SHUAIAPI/gpt-6.1-sol / opencodex', flush=True)
        code = proc.wait()
    repair = json.loads((REPAIR / 'baseline.json').read_text())
    mod.checker.check(REPO, repair['inputs'], [TEST])
    for name in mod.DOCS:
        assert sha(REPO / name) == sha(mod.BASE / 'documentation-before' / name), name
    before = blocks((REPAIR / 'test-before.kt').read_text())
    after = blocks((REPO / TEST).read_text())
    assert set(before) == set(after)
    for name in before:
        if name != TARGET:
            assert before[name] == after[name], name
    write(Path(PREFIX + 'exit.json'), {'pid': proc.pid, 'exit': code, 'protected_inputs_unchanged': True, 'production_unchanged': True, 'other_eight_test_methods_unchanged': True, 'time': time.time()})
    print('REPAIR EXIT', code, 'production and eight other methods preserved', flush=True)
    raise SystemExit(code)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('stage', 'dispatch'))
    globals()[parser.parse_args().action]()
