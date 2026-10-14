from pathlib import Path
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET
REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-pin-'
BASE = Path(PREFIX + 'baseline-20261010')
DELIVERY = Path(PREFIX + 'delivery-20261010.json')
CONTRACT = REPO/'docs/desktop-ui/reverse-engineering/worktree-chat-pin-contract-2026-10-10.md'
ALLOWED = [
 'apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt',
 'apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt',
 'apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt',
 'apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt',
 'apps/shared/src/im/hikaru/harness/client/git/GitPages.kt',
 'apps/jvm-app/src/main.kt',
 'apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreePinStore.kt',
 'apps/shared/test/im/hikaru/harness/client/git/ManagedWorktreePinControllerTest.kt',
 'apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinStoreTest.kt',
 'apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinIntegrationTest.kt',
 'apps/jvm-app/test/im/hikaru/harness/desktop/WorktreePinInteractionTest.kt']
SUITES = [
 ('shared','im.hikaru.harness.client.git.ManagedWorktreePinControllerTest',5),
 ('jvm-app','im.hikaru.harness.desktop.git.DesktopWorktreePinStoreTest',7),
 ('jvm-app','im.hikaru.harness.desktop.git.DesktopWorktreePinIntegrationTest',6),
 ('jvm-app','im.hikaru.harness.desktop.WorktreePinInteractionTest',3),
 ('jvm-app','im.hikaru.harness.desktop.git.DesktopWorktreeSessionObserverTest',11),
 ('jvm-app','im.hikaru.harness.desktop.git.DesktopManagedWorktreeIntegrationTest',8),
 ('shared','im.hikaru.harness.client.git.ManagedWorktreeControllerTest',4),
 ('jvm-app','im.hikaru.harness.desktop.WorktreeSessionObservationInteractionTest',4),
 ('jvm-app','im.hikaru.harness.desktop.ManagedWorktreeInteractionTest',3),
 ('jvm-app','im.hikaru.harness.desktop.git.DesktopManagedWorktreeStoreTest',5)]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')
spec=importlib.util.spec_from_file_location('parent',REPO/'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent=importlib.util.module_from_spec(spec);spec.loader.exec_module(parent)
def paths(attempt):
 return tuple(Path(PREFIX+kind+'-20261010-'+attempt) for kind in ('source','build','evidence'))
def methods(name,root=REPO):
 module='apps/shared' if '.client.' in name else 'apps/jvm-app'
 p=root/module/'test'/Path(*name.split('.')).with_suffix('.kt')
 return re.findall(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(',p.read_text())
def prepare():
 assert not BASE.exists()
 old=json.loads((REPO/'docs/desktop-ui/verification/terminal-font-consumer-2026-10-10/font-r1/source-manifest.json').read_text())
 parent.check(REPO,old['inputs']);parent.check(Path(old['source']),old['inputs'])
 BASE.mkdir();shutil.copy2(CONTRACT,BASE/'contract.md');rows=[]
 for name in ALLOWED:
  original=REPO/name;digest=sha(original) if original.exists() else None
  if original.exists():
   target=BASE/'before'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(original,target)
  rows.append(dict(path=name,before_sha256=digest))
 text=CONTRACT.read_text();fixed={}
 for _,name,n in SUITES[:4]:
  block=text.split(name.rsplit('.',1)[-1]+'（',1)[1].split('\n\n',1)[1].split('\n\n',1)[0]
  names=re.findall(r'^- (\w+)$',block,re.M);assert len(names)==n;fixed[name]=names
 for _,name,n in SUITES[4:]:
  names=methods(name);assert len(names)==n;fixed[name]=names
 write(BASE/'baseline.json',dict(inputs=old['inputs'],files=rows,contract_sha256=sha(CONTRACT),base_source=old['source'],base_build=old['build'],fixed_methods=fixed,model='SHUAIAPI/gpt-6.1-sol',provider='opencodex'))
 prompt='你是实施worker，实际模型必须SHUAIAPI/gpt-6.1-sol / opencodex。先读取完整分析、合同、证据：\n'+str(CONTRACT.with_name('worktree-chat-pin-analysis-2026-10-10.md'))+'\n'+str(CONTRACT)+'\n'
 prompt+='当前同一持续授权任务，Comet probe已none。不重新启动workflow。保留全部dirty/untracked。仅写合同11源码文件，旧测试和其余源码不得修改。只实施与静态检查，禁止动态test/build/screenshot/native/PTY执行；父线程独立冻结串行验收。\n'
 prompt+='baseline路径 '+str(BASE/'baseline.json')+'；baseline_sha256 '+sha(BASE/'baseline.json')+'；contract_sha256 '+sha(CONTRACT)+'。严格固定21新增测试方法，交付字段和释放按合同。交付后停止写入并exit。\n'
 Path(PREFIX+'worker-20261010.prompt').write_text(prompt);print('PREPARED',len(old['inputs']),'inputs;11 files;56 tests',flush=True)
def dispatch():
 binary='/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
 argv=[binary,'exec','--ephemeral','-C',str(REPO),'--sandbox','danger-full-access','-c','approval_policy="never"','-c','model_provider="opencodex"','-c','model_reasoning_effort="high"','--model','SHUAIAPI/gpt-6.1-sol','--json','--output-last-message',PREFIX+'worker-20261010.md','-']
 with Path(PREFIX+'worker-20261010.prompt').open('rb') as prompt,Path(PREFIX+'worker-20261010.jsonl').open('wb') as log,Path(PREFIX+'worker-20261010.stderr').open('wb') as err:
  proc=subprocess.Popen(argv,cwd=REPO,stdin=prompt,stdout=log,stderr=err)
  write(Path(PREFIX+'worker-20261010.dispatch.json'),dict(pid=proc.pid,argv=argv,model='SHUAIAPI/gpt-6.1-sol',provider='opencodex',time=time.time()));print('WORKER',proc.pid,'SHUAIAPI/gpt-6.1-sol / opencodex',flush=True);result=proc.wait()
 write(Path(PREFIX+'worker-20261010.exit.json'),dict(pid=proc.pid,exit=result,time=time.time()));print('WORKER EXIT',result,flush=True);raise SystemExit(result)
def freeze(attempt):
 assert json.loads(Path(PREFIX+'worker-20261010.exit.json').read_text())['exit']==0
 baseline=json.loads((BASE/'baseline.json').read_text());delivery=json.loads(DELIVERY.read_text())
 assert delivery['model']=='SHUAIAPI/gpt-6.1-sol' and delivery['provider']=='opencodex'
 assert delivery['baseline_sha256']==sha(BASE/'baseline.json')
 assert delivery['contract_sha256']==baseline['contract_sha256']==sha(CONTRACT)
 assert all(delivery[f] is False for f in ('tests_run','builds_run','screenshots_run','native_execution_run'))
 assert all(delivery[f] is True for f in ('write_set_released','source_writes_stopped'))
 rows=delivery['files'];assert len(rows)==11 and {r['path'] for r in rows}==set(ALLOWED)
 before={r['path']:r['before_sha256'] for r in baseline['files']}
 for r in rows: assert r['before_sha256']==before[r['path']] and r['after_sha256']==sha(REPO/r['path'])
 parent.check(REPO,baseline['inputs'],ALLOWED);parent.check(Path(baseline['base_source']),baseline['inputs'])
 source,build,evidence=paths(attempt);assert not any(p.exists() for p in (source,build,evidence))
 shutil.copytree(baseline['base_source'],source,symlinks=True,ignore=shutil.ignore_patterns('build','.git','node_modules','__pycache__','docs','captures'))
 inputs=dict(baseline['inputs'])
 for name in ALLOWED:
  (source/name).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(REPO/name,source/name);inputs[name]=sha(source/name)
 parent.check(source,inputs)
 for _,name,n in SUITES:
  actual=methods(name,source);assert len(actual)==n and set(actual)==set(baseline['fixed_methods'][name]),name
 shutil.copytree(baseline['base_build'],build,symlinks=True,ignore=shutil.ignore_patterns('docs'));(source/'docs').mkdir();(build/'docs').symlink_to(source/'docs',target_is_directory=True);evidence.mkdir()
 write(evidence/'source-manifest.json',dict(source=str(source),build=str(build),inputs=inputs,overlays=ALLOWED,suites=SUITES,model=delivery['model'],provider=delivery['provider']))
 for src,name in [(BASE/'baseline.json','baseline.json'),(BASE/'contract.md','contract.md'),(DELIVERY,'delivery.json'),(DELIVERY.with_suffix('.md'),'delivery.md'),(Path(__file__),'parent-verify.py')]:shutil.copy2(src,evidence/name)
 for suffix in ('dispatch.json','exit.json','jsonl','stderr','md','prompt'):shutil.copy2(Path(PREFIX+'worker-20261010.'+suffix),evidence/('worker-'+suffix))
 with (evidence/'reviewed.diff').open('w') as log:
  for name in ALLOWED:
   beforepath=BASE/'before'/name;result=subprocess.run(['diff','-u',str(beforepath) if beforepath.exists() else '/dev/null',str(REPO/name)],capture_output=True,text=True);assert result.returncode in (0,1);log.write(result.stdout)
 print('FROZEN',len(inputs),'inputs,11-file overlay, fixed56 tests',flush=True)
def run(attempt):
 source,build,evidence=paths(attempt);manifest=json.loads((evidence/'source-manifest.json').read_text())
 env=os.environ.copy();env.pop('JAVA_TOOL_OPTIONS',None);env['KOTLIN_CLI_JAVA_OPTIONS']='-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
 native=evidence/'native-tmp';native.mkdir(exist_ok=True);nativeopts='-Djava.awt.headless=true -Djava.io.tmpdir='+str(native)
 env['HARNESS_WORKTREE_PIN_CAPTURE_DIR']=str(evidence/'pin-captures');env['HARNESS_WORKTREE_SESSION_OBSERVATION_CAPTURE_DIR']=str(evidence/'observation-captures')
 write(evidence/'environment.json',dict(KOTLIN_CLI_JAVA_OPTIONS=env['KOTLIN_CLI_JAVA_OPTIONS'],native_JAVA_TOOL_OPTIONS=nativeopts));results=[]
 for module,name,expected in SUITES:
  parent.check(source,manifest['inputs']);folder=evidence/name.rsplit('.',1)[-1];folder.mkdir();command=['./kotlin','test','--build-dir='+str(build),'-p','jvm','-m',module,'--include-classes='+name];te=env.copy()
  if name.endswith('InteractionTest'):te['JAVA_TOOL_OPTIONS']=nativeopts
  started=time.time();print('START',name,flush=True)
  with (folder/'command.log').open('w') as log:result=subprocess.run(command,cwd=source,env=te,stdout=log,stderr=subprocess.STDOUT)
  totals=dict(tests=0,failures=0,errors=0,skipped=0);seen=[]
  for xml in build.rglob('TEST-*.xml'):
   if xml.stat().st_mtime<started:continue
   suite=ET.parse(xml).getroot();cases=list(suite.iter('testcase'));assert len(cases)==int(suite.get('tests',0));assert all(c.get('classname')==name for c in cases)
   shutil.copy2(xml,folder/xml.name);seen.extend(c.get('name') for c in cases)
   for field in totals:totals[field]+=int(suite.get(field,0))
   for c in cases:
    for issue in c:
     if issue.tag in ('failure','error'):print('FAIL',c.get('name'),issue.get('message','')[:2500],flush=True)
  summary=dict(class_name=name,command=command,exit=result.returncode,expected=expected,totals=totals,methods=seen,elapsed_seconds=round(time.time()-started,2));write(folder/'summary.json',summary);results.append(summary);write(evidence/'test-results.json',dict(status='in-progress',suites=results));print('RESULT',name,totals,'exit',result.returncode,flush=True)
  if result.returncode or totals!=dict(tests=expected,failures=0,errors=0,skipped=0) or len(set(seen))!=expected:
   print('\n'.join((folder/'command.log').read_text().splitlines()[-60:]),flush=True);raise SystemExit(1)
 write(evidence/'test-results.json',dict(status='passed',suites=results,tests=56))
 command=['./kotlin','build','-m','jvm-app','-p','jvm','--build-dir='+str(build)];print('START JVM build',flush=True)
 with (evidence/'jvm-app-build.log').open('w') as log:result=subprocess.run(command,cwd=source,env=env,stdout=log,stderr=subprocess.STDOUT)
 write(evidence/'jvm-app-build.json',dict(command=command,exit=result.returncode))
 if result.returncode:print('\n'.join((evidence/'jvm-app-build.log').read_text().splitlines()[-60:]),flush=True);raise SystemExit(1)
 parent.check(source,manifest['inputs']);parent.check(REPO,manifest['inputs']);print('PASSED 56/56 and JVM build; visual review pending',flush=True)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=('prepare','dispatch','freeze','run'));parser.add_argument('--attempt',default='pin-r1');args=parser.parse_args()
 if args.action in ('prepare','dispatch'):globals()[args.action]()
 else:globals()[args.action](args.attempt)
