from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import shutil

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
ROOT = Path('/tmp/harness-terminal-local-visual-evidence-20261010')
BASE = Path('/tmp/harness-terminal-local-visual-baseline-20261010')
ARCHIVE = REPO / 'docs/desktop-ui/verification/terminal-local-visual-2026-10-10/local-visual-r1'
DOCS = ['docs/desktop-ui/replication-plan-2026-10-07.md',
        'docs/desktop-ui/thread-panels-readiness-2026-10-07.md']

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def prepare():
    manifest = json.loads((ROOT / 'source-manifest.json').read_text())
    baseline = json.loads((ROOT / 'baseline.json').read_text())
    delivery = json.loads((ROOT / 'delivery.json').read_text())
    static = json.loads(Path('/tmp/harness-terminal-local-visual-static-review-20261010.json').read_text())
    captures = json.loads((ROOT / 'capture-metadata-review.json').read_text())
    shell = json.loads((ROOT / 'shell-capture-review.json').read_text())
    tests = json.loads((ROOT / 'test-results.json').read_text())
    build = json.loads((ROOT / 'jvm-app-build.json').read_text())
    assert manifest['model'] == delivery['model'] == 'SHUAIAPI/gpt-6.1-sol'
    assert manifest['provider'] == delivery['provider'] == 'opencodex'
    assert len(manifest['inputs']) == len(baseline['inputs']) == 2253
    changed = sorted(name for name in baseline['inputs'] if baseline['inputs'][name] != manifest['inputs'][name])
    assert changed == sorted(row['path'] for row in delivery['files'])
    for name, expected in manifest['inputs'].items():
        assert sha(REPO / name) == sha(Path(manifest['source']) / name) == expected, name
    assert all(name not in manifest['inputs'] for name in DOCS)
    assert captures['passed'] and captures['png_count'] == 12 and captures['source_inputs_verified'] == 2253
    assert shell['passed'] and shell['png_count'] == shell['geometry_count'] == 80
    assert len(shell['contact_sheets']) == 8
    assert tests['status'] == 'passed' and tests['tests'] == 35 and len(tests['suites']) == 6
    assert captures['independent_xml_totals'] == dict(tests=35, failures=0, errors=0, skipped=0)
    assert build['exit'] == 0
    assert static['unchanged_assertion_calls'] == 103
    assert static['old_assertion_calls'] == 104 and static['new_assertion_calls'] == 158
    assert len(static['removed_assertions']) == 1
    assert static['old_methods'] == static['new_methods'][:6]
    decision = dict(passed=True, original_page_methods_preserved=6, added_page_methods=1,
        original_assertion_calls=104, preserved_assertion_calls=103, added_assertion_calls=55,
        replaced_capture_geometry_assertions=1,
        replacement_reason='Popup introduces multiple roots. Window coordinates and the full viewport replace a single-root boundary check; business, byte, identity, lifecycle and kernel resize assertions remain.',
        reviewed_changes=changed, source_control_characters='passed in parent static review',
        production_service_changes=False, production_demo_data_added=False)
    write(ROOT / 'static-review-decision.json', decision)
    visual = dict(passed=True, terminal_png_count=12, terminal_review='all 12 viewed at original resolution by parent',
        terminal_files=[dict(name=row['name'], sha256=row['sha256'], dimensions=row['dimensions'],
            state=row['state'], theme=row['theme'], menu=row['menu'],
            geometry='all controls and popup inside full viewport', actual_theme='background pixel verified',
            visible_grid_comparison=row['visible_grid_comparison']) for row in captures['files']],
        shell_png_count=80, shell_geometry_count=80,
        shell_review='all 8 contact sheets viewed by parent; no obvious new clipping or navigation obstruction',
        shell_contact_sheets=shell['contact_sheets'],
        observations=[
            'Real Running and Exited (7) selector labels and final output retained in wide/compact and Light/Dark.',
            'Unknown retains honest receipt and preserved screen; Unavailable has no fabricated terminal grid.',
            'Menu focus, disabled/destructive states and full-scene capture are visible; compact popup remains in viewport.',
            '1440x900 grid rows 38 to 42 and columns remain 74; 600x800 rows 26 to 31 and columns remain 35.',
            'Shell regression screenshots use controlled background-output/subagent fixtures and do not claim real PTY provenance.'
        ],
        original_pixel_parity=False, native_window=False, native_ime=False, remote=False,
        adaptation='Single-line terminal chrome and theme-token mapping are Harness adaptations supported by ASAR structure; no matching original screenshot is available.')
    write(ROOT / 'visual-review.json', visual)
    (ROOT / 'visual-review.md').write_text('''# 本地终端紧凑视觉：父线程审查

验收日期：2026-10-10。12 张终端图全部原尺寸查看，8 张完整壳层 contact sheets 全部查看。PNG 尺寸、geometry、完整 key/revision、实际主题背景像素已独立核对。

- 真实 Running / Exited (7) 在宽窄浅深四组合中保留最终输出。
- Unknown 保留诚实回执与屏幕，Unavailable 没有伪造终端。
- 一行选择器、新建和更多入口均在视口内；菜单的禁用、危险动作与焦点可见。600 深色菜单左边 x=264、右边 x=584，位于完整 600 宽视口内。
- 1440×900 可见行数 38→42、列数 74 不变；600×800 行数 26→31、列数 35 不变。
- 80 张壳层图覆盖后台输出、子 Agent、关闭与恢复、会话及导航组合，采用受控 fixture。8 张 contact sheets 未见明显新增裁切或导航遮挡。

这是 Harness 的本机离屏结构与交互验收。紧凑操作栏及主题 token 为适配；没有原版同状态截图，原版像素、原生窗口、系统 IME、Remote 均未验收。
''')
    acceptance = dict(status='passed', accepted_at=datetime.now(timezone.utc).isoformat(),
        batch='terminal-local-visual-r1', model=manifest['model'], provider=manifest['provider'],
        worker_exit=0, worker_write_set_released=True, parent_frozen_verification=True,
        source_inputs=2253, changed_inputs=3, unchanged_inputs=2250,
        modified_files=delivery['files'], test_suites=6, totals=captures['independent_xml_totals'],
        jvm_app_build_exit=0, terminal_png_count=12, terminal_full_scene=True,
        shell_png_count=80, shell_geometry_count=80, shell_contact_sheets_reviewed=8,
        static_review='static-review-decision.json', visual_review='visual-review.json',
        source_manifest='source-manifest.json',
        source_snapshot=manifest['source'], build_directory=manifest['build'],
        scope='Local PTY compact chrome, theme scrollbar, real action menu and offscreen regression',
        launch_platform='verified LocalConnection / macOS x86_64',
        native_window=False, native_ime=False, remote=False, original_pixel_parity=False,
        appearance_font_consumer=False, other_platform_launch=False, overall_goal='active',
        independent_historical_batch='page-r5 remains 30 suites / 184 tests; counts are not added together')
    write(ROOT / 'acceptance.json', acceptance)
    (ROOT / 'acceptance.md').write_text('''# 本地终端紧凑视觉 R1：已验收

实施：SHUAIAPI/gpt-6.1-sol / opencodex。工作者退出释放写集后，父线程冻结并独立串行验收。

| 项目 | 结果 |
| --- | --- |
| 编译输入 | 2,253 项，仅合同内 3 文件变化，2,250 项不变 |
| 定向回归 | 6 类 35/35，失败、错误、跳过均 0 |
| 完整 JVM 应用构建 | exit 0 |
| 终端页面 | 12 张完整页面 PNG 及 geometry/matrix，全部原尺寸查看 |
| 壳层回归 | 80 张 PNG / geometry，8 张 contact sheets 全部查看 |
| 正文可见行数 | 宽窗口 38→42；窄窗口 26→31；列数保持 |

操作区统一为一行选择器、新建图标和 typed 操作菜单；复用共享 IconButton/Menu/Tooltip，成功回执不占常驻正文，失败与 Unknown/Unavailable 继续展示。滚动条采用主题 Surface/Border/BorderStrong。原六个页面方法及业务、真实字节、生命周期、身份、history 和 kernel resize 断言保留，新增真实指针/键盘及菜单作用域验证。

真实 PTY 功能 R5 的 184/184 保留为独立历史证据，各批测试有重叠，不累加。字体仍为本批合同约定的 13sp/18sp；Appearance 字体消费者另行接入。

原版同状态像素、原生窗口/系统 IME、Remote 与其他平台 launch 尚未验收；整体复刻目标继续 active。
''')
    for name in DOCS:
        target = ROOT / 'documentation-before' / name
        assert not target.exists()
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, target)
    print('Prepared acceptance and documentation-before; no tests rerun')

def archive():
    assert not ARCHIVE.exists()
    shutil.copytree(ROOT, ARCHIVE, ignore=shutil.ignore_patterns('native-tmp', 'worker-jsonl'))
    manifest = json.loads((ARCHIVE / 'source-manifest.json').read_text())
    delivery = json.loads((ARCHIVE / 'delivery.json').read_text())
    for row in delivery['files']:
        name = row['path']
        for label, src in [('source-after', Path(manifest['source']) / name), ('source-before', BASE / 'before' / name)]:
            target = ARCHIVE / label / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, target)
        assert sha(ARCHIVE / 'source-after' / name) == row['after_sha256']
        assert sha(ARCHIVE / 'source-before' / name) == row['before_sha256']
    refs = REPO / 'docs/desktop-ui/reverse-engineering'
    for name in ['terminal-local-visual-analysis-2026-10-10.md', 'terminal-local-visual-contract-2026-10-10.md']:
        shutil.copy2(refs / name, ARCHIVE / name)
    shutil.copytree(refs / 'terminal-local-visual-source-2026-10-10', ARCHIVE / 'asar-source')
    scripts = {
        'static-review.py': Path('/tmp/harness-terminal-local-visual-static-review-r1-20261010.py'),
        'capture-metadata-review.py': Path('/tmp/harness-terminal-local-visual-metadata-review-20261010.py'),
        'shell-review.py': Path('/tmp/harness-terminal-local-visual-shell-review-20261010.py'),
        'finalize.py': Path(__file__),
        'renderer-parent-verify.py': REPO / 'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py',
    }
    for name, path in scripts.items():
        shutil.copy2(path, ARCHIVE / name)
    shutil.copy2('/tmp/harness-terminal-local-visual-static-review-20261010.json', ARCHIVE / 'static-review.json')
    docs = []
    for name in DOCS:
        target = ARCHIVE / 'documentation-after' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, target)
        docs.append(dict(path=name, before_sha256=sha(ARCHIVE / 'documentation-before' / name), after_sha256=sha(target)))
    write(ARCHIVE / 'documentation-sync.json', dict(files=docs, source_manifest_unchanged=True))
    (ARCHIVE / 'README.md').write_text('''# 本地终端紧凑视觉 R1 证据

入口：acceptance.md / acceptance.json。6 类 35/35 与完整 JVM build 通过，12 终端完整页面图及 80 壳层图已核验。

source-manifest.json 记录 2,253 编译输入，source-before/source-after 保存本批 3 文件。完整冻结源码及构建仍在 manifest 所列 /tmp 路径；本目录不是完整仓库快照。XML、原始命令和环境记录供核查，脚本保留当时绝对路径及已有 parent helper 依赖。

asar-source 保存 6 个原版 asset 及 size/offset/SHA；分析与合同分清原版证据及 Harness 适配。documentation-before/after 和 documentation-sync.json 保存计划同步前后证据。

不归档 native-tmp 与 worker-jsonl；原始文件仍在 /tmp/harness-terminal-local-visual-evidence-20261010。dispatch、exit、prompt、delivery、stderr 与工作者末消息已归档。

archive-sha256.json 覆盖其生成时的全部归档文件，排除自身及其后生成的 archive-sha256-verification.json，以避免自引用。父线程另行逐文件读取核验。

原生窗口、系统 IME、Remote、原版像素与其他平台 launch 未验收。总目标继续 active，历史 R5 184/184 与本批数量分别记录。
''')
    files = {str(p.relative_to(ARCHIVE)): dict(sha256=sha(p), bytes=p.stat().st_size)
             for p in sorted(ARCHIVE.rglob('*')) if p.is_file()}
    write(ARCHIVE / 'archive-sha256.json', dict(files=files, file_count=len(files),
        excluded=['archive-sha256.json', 'archive-sha256-verification.json']))
    print(json.dumps(dict(archive=str(ARCHIVE), files=len(files)), ensure_ascii=False))

if __name__ == '__main__':
    import sys
    {'prepare': prepare, 'archive': archive}[sys.argv[1]]()
