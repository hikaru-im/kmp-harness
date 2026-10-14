from pathlib import Path
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET
REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-a1-'
BASE = Path(PREFIX + 'baseline-20261010')
DELIVERY = Path(PREFIX + 'delivery-20261010.json')
CONTRACT = REPO/'docs/desktop-ui/reverse-engineering/worktree-chat-archive-session-gate-contract-2026-10-10.md'
OLD = Path('/tmp/harness-worktree-chat-pin-evidence-20261010-pin-r2/source-manifest.json')
ALLOWED = [
 'modules/session/src/im/hikaru/harness/session/SessionStore.kt',
 'modules/session/src/im/hikaru/harness/session/Session.kt',
 'modules/session/src/im/hikaru/harness/session/SessionAdmission.kt',
 'modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt']
NEW = [
 'inFlightAppendMakesReservationBusyThroughNotice',
 'reservedSessionRejectsAppendRestoreAndLifecycleWithoutMutation',
 'catalogPermitProtectsCreateLoadAndFlush',
 'callbackReservationAndIndependentMutationDoNotHoldStateLock',
 'cancellationAndFailuresReleaseOnlyOwnPermits',
 'releasedParentPermitCannotAuthorizeInheritedChild',
 'staleHandleOrReservationCannotAffectSameIdReplacement',
 'ownerDisposeInvalidatesReservationAndKeepsOldReferencesClosed']
SUITES = [
 ('session','im.hikaru.harness.session.SessionAdmissionContractTest',8),
 ('session','im.hikaru.harness.session.SessionContractTest',14),
 ('agent','im.hikaru.harness.agent.AgentContractTest',6),
 ('session-persistence','im.hikaru.harness.session.persistence.SessionPersistenceContractTest',7)]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):
 p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')
spec=importlib.util.spec_from_file_location('existing_parent',REPO/'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent=importlib.util.module_from_spec(spec);spec.loader.exec_module(parent)
def paths(attempt):
 return tuple(Path(PREFIX+kind+'-20261010-'+attempt) for kind in ('source','build','evidence'))
def methods(module,name,root=REPO):
 p=root/'modules'/module/'test'/Path(*name.split('.')).with_suffix('.kt')
 return re.findall(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(',p.read_text())
def prepare():
 assert not BASE.exists()
 old=json.loads(OLD.read_text()); parent.check(REPO,old['inputs']);parent.check(Path(old['source']),old['inputs'])
 BASE.mkdir();shutil.copy2(CONTRACT,BASE/'contract.md'); rows=[]
 for name in ALLOWED:
  p=REPO/name;digest=sha(p) if p.exists() else None
  if p.exists():
   target=BASE/'before'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target)
  rows.append(dict(path=name,before_sha256=digest))
 fixed={SUITES[0][1]:NEW}
 for module,name,n in SUITES[1:]:
  ms=methods(module,name); assert len(ms)==n;fixed[name]=ms
 write(BASE/'baseline.json',dict(inputs=old['inputs'],files=rows,contract_sha256=sha(CONTRACT),base_source=old['source'],base_build=old['build'],fixed_methods=fixed,model='SHUAIAPI/gpt-6.1-sol',provider='opencodex'))
 prompt='你是本持续授权任务的实施工作者。宿主请求路由 SHUAIAPI/gpt-6.1-sol / opencodex。用户要求先分析再实施。只读分析已结束退出，父线程已收敛 A1 精确合同。只读提案广泛写集不被批准；本轮唯一权威是下面的四文件合同。\n'
 prompt+='先完整读取 '+str(CONTRACT)+'\n基线 '+str(BASE/'baseline.json')+'\n'
 prompt+='baseline_sha256='+sha(BASE/'baseline.json')+'\ncontract_sha256='+sha(CONTRACT)+'\n'
 prompt+='理解代码先CodeGraph；只查合同四文件及必要直接依赖，不重复扫描所有归档设计。Comet probe已none，本持续任务不重跑。保留全部dirty/untracked。只实施与静态检查；禁止test/build/screenshot/native/PTY，父线程独立冻结串行验收。禁止改旧测试、配置、Git状态、派其他worker。四文件无法满足时给具体阻碍并停止，不能自行扩大。固定8新增方法，交付JSON与MD按合同到指定tmp路径。交付后停止写入并exit。\n'
 Path(PREFIX+'worker-20261010.prompt').write_text(prompt)
 print('PREPARED',len(old['inputs']),'baseline inputs;4 files;35 tests',flush=True)
def dispatch():
 binary='/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
 argv=[binary,'exec','--ephemeral','-C',str(REPO),'--sandbox','danger-full-access','-c','approval_policy="never"','-c','model_provider="opencodex"','-c','model_reasoning_effort="high"','--model','SHUAIAPI/gpt-6.1-sol','--json','--output-last-message',PREFIX+'worker-20261010.md','-']
 with Path(PREFIX+'worker-20261010.prompt').open('rb') as prompt,Path(PREFIX+'worker-20261010.jsonl').open('wb') as log,Path(PREFIX+'worker-20261010.stderr').open('wb') as err:
  proc=subprocess.Popen(argv,cwd=REPO,stdin=prompt,stdout=log,stderr=err)
  write(Path(PREFIX+'worker-20261010.dispatch.json'),dict(pid=proc.pid,argv=argv,model='SHUAIAPI/gpt-6.1-sol',provider='opencodex',time=time.time()))
  print('WORKER',proc.pid,'SHUAIAPI/gpt-6.1-sol / opencodex',flush=True);code=proc.wait()
 write(Path(PREFIX+'worker-20261010.exit.json'),dict(pid=proc.pid,exit=code,time=time.time()));print('WORKER EXIT',code,flush=True);raise SystemExit(code)
def freeze(attempt):
 assert json.loads(Path(PREFIX+'worker-20261010.exit.json').read_text())['exit']==0
 baseline=json.loads((BASE/'baseline.json').read_text());delivery=json.loads(DELIVERY.read_text())
 assert delivery['model']=='SHUAIAPI/gpt-6.1-sol' and delivery['provider']=='opencodex'
 assert delivery['baseline_sha256']==sha(BASE/'baseline.json') and delivery['contract_sha256']==baseline['contract_sha256']==sha(CONTRACT)
 assert all(delivery[f] is False for f in ('tests_run','builds_run','screenshots_run','native_execution_run'))
 assert all(delivery[f] is True for f in ('write_set_released','source_writes_stopped'))
 rows=delivery['files'];assert len(rows)==len(ALLOWED) and {r['path'] for r in rows}==set(ALLOWED)
 before={r['path']:r['before_sha256'] for r in baseline['files']}
 for row in rows: assert row['before_sha256']==before[row['path']] and row['after_sha256']==sha(REPO/row['path'])
 parent.check(REPO,baseline['inputs'],ALLOWED);parent.check(Path(baseline['base_source']),baseline['inputs'])
 source,build,evidence=paths(attempt);assert not any(p.exists() for p in (source,build,evidence))
 shutil.copytree(baseline['base_source'],source,symlinks=True,ignore=shutil.ignore_patterns('build','.git','node_modules','__pycache__','docs','captures'))
 inputs=dict(baseline['inputs'])
 for name in ALLOWED:
  target=source/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(REPO/name,target);inputs[name]=sha(target)
 parent.check(source,inputs)
 for module,name,n in SUITES:
  actual=methods(module,name,source);assert len(actual)==n and set(actual)==set(baseline['fixed_methods'][name]),name
 shutil.copytree(baseline['base_build'],build,symlinks=True,ignore=shutil.ignore_patterns('docs'));(source/'docs').mkdir();(build/'docs').symlink_to(source/'docs',target_is_directory=True);evidence.mkdir()
 write(evidence/'source-manifest.json',dict(source=str(source),build=str(build),inputs=inputs,overlays=ALLOWED,suites=SUITES,model=delivery['model'],provider=delivery['provider']))
 for src,name in [(BASE/'baseline.json','baseline.json'),(BASE/'contract.md','contract.md'),(DELIVERY,'delivery.json'),(DELIVERY.with_suffix('.md'),'delivery.md'),(Path(__file__),'parent-verify.py')]:shutil.copy2(src,evidence/name)
 for suffix in ('dispatch.json','exit.json','jsonl','stderr','md','prompt'):shutil.copy2(Path(PREFIX+'worker-20261010.'+suffix),evidence/('worker-'+suffix))
 with (evidence/'reviewed.diff').open('w') as log:
  for name in ALLOWED:
   bp=BASE/'before'/name;result=subprocess.run(['diff','-u',str(bp) if bp.exists() else '/dev/null',str(REPO/name)],capture_output=True,text=True);assert result.returncode in (0,1);log.write(result.stdout)
 print('FROZEN',len(inputs),'inputs;4-file overlay;35 fixed tests',flush=True)
def run(attempt):
 source,build,evidence=paths(attempt);manifest=json.loads((evidence/'source-manifest.json').read_text());baseline=json.loads((evidence/'baseline.json').read_text())
 env=os.environ.copy();env.pop('JAVA_TOOL_OPTIONS',None);env['KOTLIN_CLI_JAVA_OPTIONS']='-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
 write(evidence/'environment.json',dict(KOTLIN_CLI_JAVA_OPTIONS=env['KOTLIN_CLI_JAVA_OPTIONS'],JAVA_TOOL_OPTIONS='unset'));results=[]
 for module,name,n in SUITES:
  parent.check(source,manifest['inputs']);folder=evidence/name.rsplit('.',1)[-1];folder.mkdir();command=['./kotlin','test','--build-dir='+str(build),'-p','jvm','-m',module,'--include-classes='+name];started=time.time();print('START',name,flush=True)
  with (folder/'command.log').open('w') as log: result=subprocess.run(command,cwd=source,env=env,stdout=log,stderr=subprocess.STDOUT)
  totals=dict(tests=0,failures=0,errors=0,skipped=0);seen=[]
  for xml in build.rglob('TEST-*.xml'):
   if xml.stat().st_mtime<started:continue
   suite=ET.parse(xml).getroot();cases=list(suite.iter('testcase'));assert len(cases)==int(suite.get('tests',0));assert all(c.get('classname')==name for c in cases)
   shutil.copy2(xml,folder/xml.name);seen.extend(c.get('name') for c in cases)
   for field in totals:totals[field]+=int(suite.get(field,0))
   for case in cases:
    for issue in case:
     if issue.tag in ('failure','error'):print('FAIL',case.get('name'),issue.get('message','')[:2200],flush=True)
  summary=dict(class_name=name,command=command,exit=result.returncode,expected=n,totals=totals,methods=seen,elapsed_seconds=round(time.time()-started,2));write(folder/'summary.json',summary);results.append(summary);write(evidence/'test-results.json',dict(status='in-progress',suites=results));print('RESULT',name,totals,'exit',result.returncode,flush=True)
  if result.returncode or totals!=dict(tests=n,failures=0,errors=0,skipped=0) or set(seen)!=set(baseline['fixed_methods'][name]) or len(seen)!=n:
   print('\n'.join((folder/'command.log').read_text().splitlines()[-60:]),flush=True);raise SystemExit(1)
 write(evidence/'test-results.json',dict(status='passed',suites=results,tests=35))
 command=['./kotlin','build','-m','jvm-app','-p','jvm','--build-dir='+str(build)];print('START JVM build',flush=True)
 with (evidence/'jvm-app-build.log').open('w') as log:result=subprocess.run(command,cwd=source,env=env,stdout=log,stderr=subprocess.STDOUT)
 write(evidence/'jvm-app-build.json',dict(command=command,exit=result.returncode))
 if result.returncode:print('\n'.join((evidence/'jvm-app-build.log').read_text().splitlines()[-60:]),flush=True);raise SystemExit(1)
 parent.check(source,manifest['inputs']);parent.check(REPO,manifest['inputs']);print('PASSED 35/35 and JVM build',flush=True)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=('prepare','dispatch','freeze','run'));parser.add_argument('--attempt',default='gate-r1');args=parser.parse_args()
 if args.action in ('prepare','dispatch'):globals()[args.action]()
 else:globals()[args.action](args.attempt)
