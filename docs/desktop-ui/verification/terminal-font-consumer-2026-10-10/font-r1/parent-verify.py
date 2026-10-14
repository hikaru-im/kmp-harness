from pathlib import Path
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET
REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-terminal-font-'
BASE = Path(PREFIX + 'baseline-20261010')
SOURCE = Path(PREFIX + 'source-20261010')
BUILD = Path(PREFIX + 'build-20261010')
EVIDENCE = Path(PREFIX + 'evidence-20261010')
DELIVERY = Path(PREFIX + 'delivery-20261010.json')
CONTRACT = REPO / 'docs/desktop-ui/reverse-engineering/terminal-font-consumer-contract-2026-10-10.md'
ALLOWED = ['apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt',
 'apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt',
 'apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalFontInteractionTest.kt']
FONT_SUITE = 'im.hikaru.harness.desktop.PtyTerminalFontInteractionTest'
FONT_CAPTURES = Path('/tmp/harness-terminal-font-captures-20261010')
SUITES = [('jvm-app',FONT_SUITE,3),
 ('jvm-app','im.hikaru.harness.desktop.PtyTerminalPageInteractionTest',7),
 ('shared','im.hikaru.harness.client.panels.PtyTerminalControllerTest',8),
 ('shared','im.hikaru.harness.client.panels.PtyTerminalGridTest',4),
 ('shared','im.hikaru.harness.client.panels.TerminalTextInputBridgeTest',4),
 ('jvm-app','im.hikaru.harness.desktop.terminal.PtyTerminalConnectionIntegrationTest',4)]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):
 p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+chr(10))
spec=importlib.util.spec_from_file_location('terminal_parent',REPO/'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent=importlib.util.module_from_spec(spec);spec.loader.exec_module(parent)

def prepare():
 assert not BASE.exists()
 old=json.loads((REPO/'docs/desktop-ui/verification/terminal-local-visual-2026-10-10/local-visual-r1/source-manifest.json').read_text())
 parent.check(REPO,old['inputs']);parent.check(Path(old['source']),old['inputs'])
 BASE.mkdir();shutil.copy2(CONTRACT,BASE/'contract.md')
 rows=[]
 for name in ALLOWED:
  original=REPO/name
  digest=sha(original) if original.exists() else None
  if original.exists():
   target=BASE/'before'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(original,target)
  rows.append(dict(path=name,before_sha256=digest))
 write(BASE/'baseline.json',dict(inputs=old['inputs'],files=rows,contract_sha256=sha(CONTRACT),base_source=old['source'],base_build=old['build'],model='SHUAIAPI/gpt-6.1-sol',provider='opencodex'))
 prompt='你是实施 worker，实际使用 SHUAIAPI/gpt-6.1-sol / opencodex。先读取并遵循两份分析和合同：\n'+str(CONTRACT.with_name('terminal-font-consumer-analysis-2026-10-10.md'))+'\n'+str(CONTRACT)+'\n'
 prompt+='当前同一授权任务继续，Comet probe 已 none，不重新启动流程。保留所有已有 dirty/untracked。只写合同中的3源码路径，不修改任何其他源码、fixture或文档。工作者只实施与静态检查，禁止一切动态 build/test/screenshot/PTY/native 执行；父线程在你退出后独立验证。\n'
 prompt+='基线目录 '+str(BASE)+'；baseline_sha256 '+sha(BASE/'baseline.json')+'；contract_sha256 '+sha(CONTRACT)+'。交付路径和字段按合同，交付后停止源码写入并退出。\n'
 Path(PREFIX+'worker-20261010.prompt').write_text(prompt)
 print('PREPARED',len(old['inputs']),'inputs; bounded 3-file write set',flush=True)

def dispatch():
 binary='/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
 argv=[binary,'exec','--ephemeral','-C',str(REPO),'--sandbox','danger-full-access','-c','approval_policy="never"','-c','model_provider="opencodex"','-c','model_reasoning_effort="high"','--model','SHUAIAPI/gpt-6.1-sol','--json','--output-last-message',PREFIX+'worker-20261010.md','-']
 with Path(PREFIX+'worker-20261010.prompt').open('rb') as prompt,Path(PREFIX+'worker-20261010.jsonl').open('wb') as log,Path(PREFIX+'worker-20261010.stderr').open('wb') as err:
  proc=subprocess.Popen(argv,cwd=REPO,stdin=prompt,stdout=log,stderr=err)
  write(Path(PREFIX+'worker-20261010.dispatch.json'),dict(pid=proc.pid,argv=argv,model='SHUAIAPI/gpt-6.1-sol',provider='opencodex',time=time.time()))
  print('WORKER',proc.pid,'SHUAIAPI/gpt-6.1-sol / opencodex',flush=True);result=proc.wait()
 write(Path(PREFIX+'worker-20261010.exit.json'),dict(pid=proc.pid,exit=result,time=time.time()))
 print('WORKER EXIT',result,flush=True);raise SystemExit(result)

def freeze():
 assert json.loads(Path(PREFIX+'worker-20261010.exit.json').read_text())['exit']==0
 baseline=json.loads((BASE/'baseline.json').read_text());delivery=json.loads(DELIVERY.read_text())
 assert delivery['model']=='SHUAIAPI/gpt-6.1-sol' and delivery['provider']=='opencodex'
 assert delivery['baseline_sha256']==sha(BASE/'baseline.json')
 assert delivery['contract_sha256']==baseline['contract_sha256']==sha(CONTRACT)
 assert all(delivery[field] is False for field in ('tests_run','builds_run','screenshots_run','native_execution_run'))
 assert all(delivery[field] is True for field in ('write_set_released','source_writes_stopped'))
 records=delivery['files'];assert len(records)==3 and {r['path'] for r in records}==set(ALLOWED)
 before={r['path']:r['before_sha256'] for r in baseline['files']}
 for r in records: assert r['before_sha256']==before[r['path']] and r['after_sha256']==sha(REPO/r['path'])
 parent.check(REPO,baseline['inputs'],ALLOWED);parent.check(Path(baseline['base_source']),baseline['inputs'])
 assert not any(p.exists() for p in (SOURCE,BUILD,EVIDENCE))
 shutil.copytree(baseline['base_source'],SOURCE,symlinks=True,ignore=shutil.ignore_patterns('build','.git','node_modules','__pycache__','captures'))
 inputs=dict(baseline['inputs'])
 for name in ALLOWED: shutil.copy2(REPO/name,SOURCE/name);inputs[name]=sha(SOURCE/name)
 parent.check(SOURCE,inputs)
 shutil.copytree(baseline['base_build'],BUILD,symlinks=True,ignore=shutil.ignore_patterns('docs'))
 (BUILD/'docs').symlink_to(SOURCE/'docs',target_is_directory=True);EVIDENCE.mkdir()
 write(EVIDENCE/'source-manifest.json',dict(source=str(SOURCE),build=str(BUILD),inputs=inputs,overlays=ALLOWED,suites=SUITES,model=delivery['model'],provider=delivery['provider']))
 for src,name in [(BASE/'baseline.json','baseline.json'),(BASE/'contract.md','contract.md'),(DELIVERY,'delivery.json'),(DELIVERY.with_suffix('.md'),'delivery.md'),(Path(__file__),'parent-verify.py')]: shutil.copy2(src,EVIDENCE/name)
 for suffix in ('dispatch.json','exit.json','jsonl','stderr','md','prompt'): shutil.copy2(Path(PREFIX+'worker-20261010.'+suffix),EVIDENCE/('worker-'+suffix))
 for name in ALLOWED:
  before_path=BASE/'before'/name
  result=subprocess.run(['diff','-u',str(before_path) if before_path.exists() else '/dev/null',str(REPO/name)],capture_output=True,text=True);assert result.returncode in (0,1)
  with (EVIDENCE/'reviewed.diff').open('a') as log: log.write(result.stdout)
 print('FROZEN',len(inputs),'inputs, limited 3-file overlay',flush=True)

def run():
 manifest=json.loads((EVIDENCE/'source-manifest.json').read_text())
 env=os.environ.copy();env.pop('JAVA_TOOL_OPTIONS',None);env['KOTLIN_CLI_JAVA_OPTIONS']='-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
 native=EVIDENCE/'native-tmp';native.mkdir(exist_ok=True);native_options='-Djava.awt.headless=true -Djava.io.tmpdir='+str(native)
 env['HARNESS_APP_THREAD_SHELL_CAPTURE_DIR']=str(EVIDENCE/'shell-captures')
 write(EVIDENCE/'environment.json',dict(KOTLIN_CLI_JAVA_OPTIONS=env['KOTLIN_CLI_JAVA_OPTIONS'],native_JAVA_TOOL_OPTIONS=native_options))
 results=[]
 for module,name,expected in SUITES:
  parent.check(SOURCE,manifest['inputs']);folder=EVIDENCE/name.rsplit('.',1)[-1];folder.mkdir()
  command=['./kotlin','test','--build-dir='+str(BUILD),'-p','jvm','-m',module,'--include-classes='+name];te=env.copy()
  if name in parent.NATIVE_SUITES or name==FONT_SUITE: te['JAVA_TOOL_OPTIONS']=native_options
  if name==parent.PAGE_CAPTURE_SUITE: assert not parent.PAGE_CAPTURE_DIRECTORY.exists()
  if name==FONT_SUITE: assert not FONT_CAPTURES.exists()
  started=time.time();print('START',name,flush=True)
  try:
   with (folder/'command.log').open('w') as log: result=subprocess.run(command,cwd=SOURCE,env=te,stdout=log,stderr=subprocess.STDOUT)
  finally:
   if name==parent.PAGE_CAPTURE_SUITE and parent.PAGE_CAPTURE_DIRECTORY.exists(): shutil.move(str(parent.PAGE_CAPTURE_DIRECTORY),str(folder/'captures'))
   if name==FONT_SUITE and FONT_CAPTURES.exists(): shutil.move(str(FONT_CAPTURES),str(folder/'captures'))
  totals=dict(tests=0,failures=0,errors=0,skipped=0);methods=[]
  for xml in BUILD.rglob('TEST-*.xml'):
   if xml.stat().st_mtime<started: continue
   suite=ET.parse(xml).getroot();cases=list(suite.iter('testcase'));assert len(cases)==int(suite.get('tests',0))
   assert all(case.get('classname')==name for case in cases);shutil.copy2(xml,folder/xml.name);methods.extend(case.get('name') for case in cases)
   for field in totals: totals[field]+=int(suite.get(field,0))
   for case in cases:
    for issue in case:
     if issue.tag in ('failure','error'): print('FAIL',case.get('name'),issue.get('message','')[:2500],flush=True)
  summary=dict(class_name=name,command=command,exit=result.returncode,expected=expected,totals=totals,methods=methods,elapsed_seconds=round(time.time()-started,2))
  write(folder/'summary.json',summary);results.append(summary);write(EVIDENCE/'test-results.json',dict(status='in-progress',suites=results));print('RESULT',name,totals,'exit',result.returncode,flush=True)
  if result.returncode or totals!=dict(tests=expected,failures=0,errors=0,skipped=0) or len(set(methods))!=expected:
   print(chr(10).join((folder/'command.log').read_text().splitlines()[-45:]),flush=True);raise SystemExit(1)
 write(EVIDENCE/'test-results.json',dict(status='passed',suites=results,tests=30))
 command=['./kotlin','build','-m','jvm-app','-p','jvm','--build-dir='+str(BUILD)];print('START JVM build',flush=True)
 with (EVIDENCE/'jvm-app-build.log').open('w') as log: result=subprocess.run(command,cwd=SOURCE,env=env,stdout=log,stderr=subprocess.STDOUT)
 write(EVIDENCE/'jvm-app-build.json',dict(command=command,exit=result.returncode))
 if result.returncode: print(chr(10).join((EVIDENCE/'jvm-app-build.log').read_text().splitlines()[-45:]),flush=True);raise SystemExit(1)
 parent.check(SOURCE,manifest['inputs']);parent.check(REPO,manifest['inputs']);print('PASSED 30/30 and JVM build; visual review pending',flush=True)

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=('prepare','dispatch','freeze','run'));args=parser.parse_args();globals()[args.action]()
