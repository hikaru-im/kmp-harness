from pathlib import Path
from datetime import datetime, timezone
import hashlib, importlib.util, json, shutil

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
ROOT = Path('/tmp/harness-terminal-font-evidence-20261010')
BASE = Path('/tmp/harness-terminal-font-baseline-20261010')
ARCHIVE = REPO / 'docs/desktop-ui/verification/terminal-font-consumer-2026-10-10/font-r1'
DOCS = ['docs/desktop-ui/replication-plan-2026-10-07.md', 'docs/desktop-ui/thread-panels-readiness-2026-10-07.md']
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')

def prepare():
    manifest = json.loads((ROOT / 'source-manifest.json').read_text())
    baseline = json.loads((ROOT / 'baseline.json').read_text())
    delivery = json.loads((ROOT / 'delivery.json').read_text())
    tests = json.loads((ROOT / 'test-results.json').read_text())
    build = json.loads((ROOT / 'jvm-app-build.json').read_text())
    visual = json.loads((ROOT / 'visual-review.json').read_text())
    assert tests['status'] == 'passed' and tests['tests'] == 30 and len(tests['suites']) == 6
    totals = {key: sum(s['totals'][key] for s in tests['suites']) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals == dict(tests=30, failures=0, errors=0, skipped=0) and build['exit'] == 0
    assert len(baseline['inputs']) == 2253 and len(manifest['inputs']) == 2254
    assert manifest['model'] == delivery['model'] == 'SHUAIAPI/gpt-6.1-sol'
    assert manifest['provider'] == delivery['provider'] == 'opencodex'
    changed = sorted(name for name in manifest['inputs'] if baseline['inputs'].get(name) != manifest['inputs'][name])
    assert changed == sorted(r['path'] for r in delivery['files']) and len(changed) == 3
    for name, expected in manifest['inputs'].items():
        assert sha(REPO / name) == sha(Path(manifest['source']) / name) == expected, name
    assert visual['passed'] and visual['font_full_page_png_count'] == 4
    acceptance = dict(status='passed', accepted_at=datetime.now(timezone.utc).isoformat(),
        batch='terminal-font-consumer-r1', model=manifest['model'], provider=manifest['provider'],
        worker_exit=0, worker_write_set_released=True, parent_frozen_verification=True,
        source_inputs=2254, changed_existing_inputs=2, added_inputs=1, unchanged_inputs=2251,
        test_suites=6, totals=totals, jvm_app_build_exit=0, modified_files=delivery['files'],
        font_full_page_png_count=4, visual_review='visual-review.json',
        source_manifest='source-manifest.json', source_snapshot=manifest['source'], build_directory=manifest['build'],
        appearance_font_consumer=True, fixed_bounds_refit=True, real_kernel_resize=True,
        native_window=False, native_ime=False, remote=False, original_pixel_parity=False, overall_goal='active')
    write(ROOT / 'acceptance.json', acceptance)
    (ROOT / 'acceptance.md').write_text('''# 终端字体消费者 R1：已验收

实施：SHUAIAPI/gpt-6.1-sol / opencodex。工作者退出并释放写集后，父线程冻结源码，独立串行验收。

| 项目 | 结果 |
| --- | --- |
| 编译输入 | 2,254 项；2 个已有文件变化、1 个新增测试文件；其余 2,251 项不变 |
| 定向回归 | 6 类 30/30；失败、错误、跳过均 0 |
| 完整 JVM 应用构建 | exit 0 |
| 字体页面 | 宽浅与窄深字号变化前后，共 4 张完整页面 PNG |

空视口与正文消费共享代码字体样式，字体变化在固定面板内重新计算行列，并经现有 controller 同步真实 PTY/kernel size。普通字形保留代码字体配置，ANSI 强调保持；字体变化不创建或关闭终端。UI 字号不额外缩放代码字号。新增测试使用真实 PTY fixture，既有页面、controller、grid、输入和连接回归保留。具体截图、独立预期和验收范围见 visual-review.json 与 XML。

行高沿用 Harness 共享代码文本样式策略。没有原版同状态截图；原版像素、原生窗口/系统 IME、Remote 和其他平台 launch 未验收。历史 184/184 和 35/35 为独立批次，不累加。整体目标继续 active。
''')
    for name in DOCS:
        target = ROOT / 'documentation-before' / name
        assert not target.exists()
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, target)
    print('Prepared acceptance and documentation snapshots')

def archive():
    assert not ARCHIVE.exists()
    shutil.copytree(ROOT, ARCHIVE, ignore=shutil.ignore_patterns('native-tmp', 'worker-jsonl'))
    manifest = json.loads((ARCHIVE / 'source-manifest.json').read_text())
    delivery = json.loads((ARCHIVE / 'delivery.json').read_text())
    for row in delivery['files']:
        name = row['path']
        target = ARCHIVE / 'source-after' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path(manifest['source']) / name, target)
        assert sha(target) == row['after_sha256']
        if row['before_sha256'] is not None:
            target = ARCHIVE / 'source-before' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(BASE / 'before' / name, target)
            assert sha(target) == row['before_sha256']
    refs = REPO / 'docs/desktop-ui/reverse-engineering'
    for name in ('terminal-font-consumer-analysis-2026-10-10.md', 'terminal-font-consumer-contract-2026-10-10.md'):
        shutil.copy2(refs / name, ARCHIVE / name)
    shutil.copy2(refs / 'terminal-local-visual-source-2026-10-10/manifest.json', ARCHIVE / 'asar-source-manifest.json')
    shutil.copy2(Path(__file__), ARCHIVE / 'finalize.py')
    for name in DOCS:
        target = ARCHIVE / 'documentation-after' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, target)
    paths = sorted(p for p in ARCHIVE.rglob('*') if p.is_file())
    write(ARCHIVE / 'archive-sha256.json', dict(files=[dict(path=str(p.relative_to(ARCHIVE)), bytes=p.stat().st_size, sha256=sha(p)) for p in paths]))
    print('Archived', len(paths), 'files')

if __name__ == '__main__':
    import sys
    globals()[sys.argv[1]]()
