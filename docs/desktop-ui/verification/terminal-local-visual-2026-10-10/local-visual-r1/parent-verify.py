from pathlib import Path
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET
REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-terminal-local-visual-'
BASE = Path(PREFIX + 'baseline-20261010')
SOURCE = Path(PREFIX + 'source-20261010')
BUILD = Path(PREFIX + 'build-20261010')
EVIDENCE = Path(PREFIX + 'evidence-20261010')
DELIVERY = Path(PREFIX + 'delivery-20261010.json')
CONTRACT = REPO / 'docs/desktop-ui/reverse-engineering/terminal-local-visual-contract-2026-10-10.md'
ALLOWED = ['apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt',
 'apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt',
 'apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt']
SUITES = [('jvm-app','im.hikaru.harness.desktop.PtyTerminalPageInteractionTest',7),
 ('shared','im.hikaru.harness.client.panels.PtyTerminalControllerTest',8),
 ('shared','im.hikaru.harness.client.panels.PtyTerminalGridTest',4),
 ('shared','im.hikaru.harness.client.panels.TerminalTextInputBridgeTest',4),
 ('jvm-app','im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest',8),
 ('jvm-app','im.hikaru.harness.desktop.terminal.PtyTerminalConnectionIntegrationTest',4)]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,value):
 p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(json.dumps(value,indent=2,ensure_ascii=False)+chr(10))
spec=importlib.util.spec_from_file_location('terminal_parent',REPO/'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
parent=importlib.util.module_from_spec(spec);spec.loader.exec_module(parent)

def prepare():
 assert not BASE.exists()
 old=json.loads(Path('/tmp/harness-thread-message-final-evidence-20261010/source-manifest.json').read_text())
 parent.check(REPO,old['inputs']);parent.check(Path(old['source']),old['inputs'])
 assets=Path('/tmp/harness-terminal-visual-asar-20261010')
 out=REPO/'docs/desktop-ui/reverse-engineering/terminal-local-visual-source-2026-10-10'
 assert not out.exists()
 manifest=json.loads((assets/'manifest.json').read_text());archive=Path(manifest['archive_path'])
 assert archive.stat().st_size==manifest['archive_size'];out.mkdir()
 with archive.open('rb') as stream:
  for row in manifest['assets']:
   original=Path(row['extracted_path'])
   assert original.stat().st_size==row['size'] and sha(original)==row['sha256']
   stream.seek(row['absolute_offset']);assert stream.read(row['size'])==original.read_bytes()
   shutil.copy2(original,out/original.name);row['evidence_path']=original.name
 write(out/'manifest.json',manifest);BASE.mkdir();shutil.copy2(CONTRACT,BASE/'contract.md')
 rows=[]
 for name in ALLOWED:
  target=BASE/'before'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(REPO/name,target)
  rows.append(dict(path=name,before_sha256=sha(target)))
 write(BASE/'baseline.json',dict(inputs=old['inputs'],files=rows,contract_sha256=sha(CONTRACT),base_source=old['source'],base_build=old['build'],model='SHUAIAPI/gpt-6.1-sol',provider='opencodex'))
 prompt='''你是实施worker。使用 SHUAIAPI/gpt-6.1-sol，provider opencodex。正在继续同一个已授权任务，Comet probe先前none，不重启流程。用户要求先分析再委托，分析已经完成。
仓库 /Volumes/Data/IdeaProjects/kmp-harness。保留全部dirty/untracked；禁止reset/stash/clean/checkout/commit/push、修改用户配置、干预其他进程。理解代码先CodeGraph，可用文件名定位；被裁切的capture/helper代码再定向查询。

先完整读取以下两份文档：
docs/desktop-ui/reverse-engineering/terminal-local-visual-analysis-2026-10-10.md
docs/desktop-ui/reverse-engineering/terminal-local-visual-contract-2026-10-10.md
六个当前app.asar asset和manifest已经归档在 terminal-local-visual-source-2026-10-10/，可复核。

只允许修改合同列出的3个文件。真实PTY R5已通过30类184测试，消息展示最终33测试已经释放；本批改紧凑操作栏、主题scrollbar和必要的菜单测试路径，不能改服务/controller/公共组件/其他页面。
旧六个测试保留名字、业务/字节/身份/计数/kernel resize/history独立预期与断言；原10完整页面PNG保持。新增一项terminalActionMenuUsesRealPointerKeyboardAndScope及1440浅色、600深色菜单图geometry；capture完整页面，不能只截图popup根。
使用typed HarnessMenu和HarnessIconButton。menu作用域覆盖bindingEpoch/selectionEpoch/selectedKey。多项disabled检查后关闭menu再检查input焦点。scope切换时关闭菜单并废弃pending动作。
只能静态检查，禁止运行build/test/kotlin/gradle/amper/screenshot/PTY fixture/native app。父线程在你退出释放写集后冻结源码，独立跑35项及JVM build。

基线目录 BASE_PLACEHOLDER，before源码在before/。baseline_sha256 BASE_SHA_PLACEHOLDER，contract_sha256 CONTRACT_SHA_PLACEHOLDER。
交付 /tmp/harness-terminal-local-visual-delivery-20261010.json 和.md，JSON按合同包含3路径before/after SHA；动态执行字段全部false、write_set_released/source_writes_stopped true。交付后立刻停止写源码并退出。不要声称动态通过或原版像素一致。
'''
 prompt=prompt.replace('BASE_PLACEHOLDER',str(BASE)).replace('BASE_SHA_PLACEHOLDER',sha(BASE/'baseline.json')).replace('CONTRACT_SHA_PLACEHOLDER',sha(CONTRACT))
 Path(PREFIX+'worker-20261010.prompt').write_text(prompt)
 print('PREPARED',len(old['inputs']),'inputs, 6 verified assets, 3-file write set',flush=True)

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
  result=subprocess.run(['diff','-u',str(BASE/'before'/name),str(REPO/name)],capture_output=True,text=True);assert result.returncode in (0,1)
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
  if name in parent.NATIVE_SUITES: te['JAVA_TOOL_OPTIONS']=native_options
  if name==parent.PAGE_CAPTURE_SUITE: assert not parent.PAGE_CAPTURE_DIRECTORY.exists()
  started=time.time();print('START',name,flush=True)
  try:
   with (folder/'command.log').open('w') as log: result=subprocess.run(command,cwd=SOURCE,env=te,stdout=log,stderr=subprocess.STDOUT)
  finally:
   if name==parent.PAGE_CAPTURE_SUITE and parent.PAGE_CAPTURE_DIRECTORY.exists(): shutil.move(str(parent.PAGE_CAPTURE_DIRECTORY),str(folder/'captures'))
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
 write(EVIDENCE/'test-results.json',dict(status='passed',suites=results,tests=35))
 command=['./kotlin','build','-m','jvm-app','-p','jvm','--build-dir='+str(BUILD)];print('START JVM build',flush=True)
 with (EVIDENCE/'jvm-app-build.log').open('w') as log: result=subprocess.run(command,cwd=SOURCE,env=env,stdout=log,stderr=subprocess.STDOUT)
 write(EVIDENCE/'jvm-app-build.json',dict(command=command,exit=result.returncode))
 if result.returncode: print(chr(10).join((EVIDENCE/'jvm-app-build.log').read_text().splitlines()[-45:]),flush=True);raise SystemExit(1)
 parent.check(SOURCE,manifest['inputs']);parent.check(REPO,manifest['inputs']);print('PASSED 35/35 and JVM build; visual review pending',flush=True)

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=('prepare','dispatch','freeze','run'));args=parser.parse_args();globals()[args.action]()
