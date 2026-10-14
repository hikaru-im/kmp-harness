from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, importlib.util, json, shutil
REPO=Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX='/tmp/harness-worktree-chat-pin-'
BASE=Path(PREFIX+'baseline-20261010')
DOCS=['docs/desktop-ui/replication-plan-2026-10-07.md','docs/desktop-ui/git-worktrees-page-readiness-2026-10-08.md']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')
def finalize(attempt):
 root=Path(PREFIX+'evidence-20261010-'+attempt)
 archive=REPO/'docs/desktop-ui/verification/worktree-chat-pin-2026-10-10'/attempt
 assert not archive.exists()
 manifest=json.loads((root/'source-manifest.json').read_text());baseline=json.loads((root/'baseline.json').read_text());delivery=json.loads((root/'delivery.json').read_text());tests=json.loads((root/'test-results.json').read_text());build=json.loads((root/'jvm-app-build.json').read_text());visual=json.loads((root/'visual-review.json').read_text())
 assert tests['status']=='passed' and tests['tests']==56 and len(tests['suites'])==10 and build['exit']==0
 totals={k:sum(s['totals'][k] for s in tests['suites']) for k in ('tests','failures','errors','skipped')};assert totals==dict(tests=56,failures=0,errors=0,skipped=0)
 assert manifest['model']==delivery['model']=='SHUAIAPI/gpt-6.1-sol' and manifest['provider']==delivery['provider']=='opencodex'
 for name,h in manifest['inputs'].items():assert sha(REPO/name)==sha(Path(manifest['source'])/name)==h,name
 assert visual['passed'] and visual['pin_png_count']>=4
 changed=[n for n,h in manifest['inputs'].items() if baseline['inputs'].get(n)!=h];assert set(changed)=={r['path'] for r in delivery['files']}
 added=[n for n in changed if n not in baseline['inputs']]
 acceptance=dict(status='passed',accepted_at=datetime.now(timezone.utc).isoformat(),batch='worktree-chat-pin-'+attempt,model=manifest['model'],provider=manifest['provider'],worker_exit=0,worker_write_set_released=True,parent_frozen_verification=True,source_inputs=len(manifest['inputs']),added_inputs=len(added),changed_existing_inputs=len(changed)-len(added),unchanged_inputs=len(manifest['inputs'])-len(changed),test_suites=10,totals=totals,jvm_app_build_exit=0,modified_files=delivery['files'],pin_png_count=visual['pin_png_count'],visual_review='visual-review.json',source_manifest='source-manifest.json',source_snapshot=manifest['source'],build_directory=manifest['build'],fresh_reader=True,fresh_bootstrap=True,real_runtime_room_git=True,native_window=False,remote=False,archive_lifecycle=False,destructive_protection=False,original_pixel_parity=False,overall_goal='active')
 write(root/'acceptance.json',acceptance)
 (root/'acceptance.md').write_text('# Worktrees 关联聊天固定：定向验收通过\n\n实施 SHUAIAPI/gpt-6.1-sol / opencodex；工作者退出释放写集后父线程冻结全部输入并独立串行验收。10 类 56/56，失败/错误/跳过均0；完整 JVM app build exit0。真实临时 Runtime/Room/Git/JSON、fresh reader/bootstrap 重读、鼠标固定/取消固定和宽窄浅深离屏页面已验证。详细方法与几何见 XML、source-manifest 和 visual-review。\n\n仅当前自有固定存储与关联观察能力得到验证。两个独立会话目录读不提供 absence proof；归档/恢复生命周期、删除/清理完整保护、Remote、原生窗口、原版同状态像素仍未验收。整体目标 active；历史回归不累加覆盖率。\n')
 shutil.copytree(root,archive,ignore=shutil.ignore_patterns('native-tmp','worker-jsonl'))
 for row in delivery['files']:
  name=row['path'];target=archive/'source-after'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(Path(manifest['source'])/name,target);assert sha(target)==row['after_sha256']
  if row['before_sha256'] is not None:
   target=archive/'source-before'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(BASE/'before'/name,target);assert sha(target)==row['before_sha256']
 refs=REPO/'docs/desktop-ui/reverse-engineering'
 for name in ('worktree-chat-pin-analysis-2026-10-10.md','worktree-chat-pin-contract-2026-10-10.md'):shutil.copy2(refs/name,archive/name)
 shutil.copytree(refs/'worktree-chat-pin-evidence-2026-10-10',archive/'asar-evidence')
 shutil.copy2(Path(__file__),archive/'finalize.py')
 for name in DOCS:
  target=archive/'documentation-after'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(REPO/name,target)
 files=sorted(p for p in archive.rglob('*') if p.is_file());write(archive/'archive-sha256.json',dict(files=[dict(path=str(p.relative_to(archive)),bytes=p.stat().st_size,sha256=sha(p)) for p in files]))
 recorded=json.loads((archive/'archive-sha256.json').read_text())['files']
 for r in recorded:
  p=archive/r['path'];assert p.stat().st_size==r['bytes'] and sha(p)==r['sha256']
 print('Archived and verified',len(files),'files;',sum(r['bytes'] for r in recorded),'bytes;',archive)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--attempt',default='pin-r1');args=parser.parse_args();finalize(args.attempt)
