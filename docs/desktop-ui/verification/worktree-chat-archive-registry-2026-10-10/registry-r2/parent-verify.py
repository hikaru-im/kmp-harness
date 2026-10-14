from pathlib import Path
import argparse, hashlib, importlib.util, json, os, re, shutil, subprocess, time
import xml.etree.ElementTree as ET

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
PREFIX = '/tmp/harness-worktree-chat-archive-registry-'
BASE = Path(PREFIX + 'baseline-20261010')
CONTRACT = REPO/'docs/desktop-ui/reverse-engineering/worktree-chat-archive-registry-contract-2026-10-10.md'
PREVIOUS = REPO/'docs/desktop-ui/verification/worktree-chat-archive-inbox-2026-10-10/inbox-r2'
DELIVERY = Path('/tmp/harness-worktree-chat-archive-registry-delivery-20261010.json')
WORKER = '/tmp/harness-worktree-chat-archive-registry-worker-20261010.'
ALLOWED = [
    'modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt',
    'modules/agent/test/im/hikaru/harness/agent/AgentRegistryAdmissionContractTest.kt',
]
NEW_METHODS = [
    'closingRejectsCreateAndRestoreBeforeFactoryOrReplay',
    'catalogPermitCoversCreateFactoryPublicationAndNotices',
    'catalogPermitCoversRestoreListenerAndRollback',
    'cancelledCreateAndRestoreRemoveOnlyTheirOwnPublishedIdentity',
    'restoreSetupCancellationReleasesIdWithoutClosingUnadoptedSession',
    'closingHandleRemainsRetryableAndStaleIdentityCannotDetachReplacement',
]
SUITES = [
    ('agent', 'im.hikaru.harness.agent.AgentRegistryAdmissionContractTest'),
    ('agent', 'im.hikaru.harness.agent.InboxAdmissionContractTest'),
    ('session', 'im.hikaru.harness.session.SessionAdmissionContractTest'),
    ('session', 'im.hikaru.harness.session.SessionContractTest'),
    ('agent', 'im.hikaru.harness.agent.AgentContractTest'),
    ('agent-loop', 'im.hikaru.harness.agent.loop.AgentLoopContractTest'),
    ('llm-retry', 'im.hikaru.harness.llm.retry.LlmRetryContractTest'),
    ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceContractTest'),
]
DOCS = [
    'docs/desktop-ui/replication-plan-2026-10-07.md',
    'docs/desktop-ui/git-worktrees-page-readiness-2026-10-08.md',
]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
def write(p, value):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n')
spec = importlib.util.spec_from_file_location('source_checker', REPO/'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10/parent-verify.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
def methods(root, module, name):
    p=root/'modules'/module/'test'/Path(*name.split('.')).with_suffix('.kt')
    return re.findall(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(', p.read_text())
def paths(attempt):
    assert re.fullmatch(r'registry-r[0-9]+',attempt)
    return tuple(Path(PREFIX+kind+'-20261010-'+attempt) for kind in ('source','build','evidence'))
def prepare():
    assert not BASE.exists()
    previous = json.loads((PREVIOUS / 'acceptance.json').read_text())
    assert previous['status'] == 'passed' and previous['totals']['tests'] == 54 and previous['jvm_app_build_exit'] == 0
    analysis = json.loads(Path('/tmp/harness-worktree-chat-archive-owner-followup-analysis-20261010.exit.json').read_text())
    assert analysis['exit'] == 0 and analysis['source_unchanged']
    old = json.loads((PREVIOUS / 'source-manifest.json').read_text())
    checker.check(REPO, old['inputs'])
    checker.check(Path(old['source']), old['inputs'])
    BASE.mkdir()
    shutil.copy2(CONTRACT, BASE / 'contract.md')
    rows = []
    for name in ALLOWED:
        p = REPO / name
        rows.append(dict(path=name, before_sha256=sha(p) if p.exists() else None))
        if p.exists():
            dst = BASE / 'before' / name
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dst)
    assert rows[0]['before_sha256'] and rows[1]['before_sha256'] is None
    fixed = {SUITES[0][1]: NEW_METHODS}
    for module, name in SUITES[1:]:
        fixed[name] = methods(REPO, module, name)
        assert fixed[name]
    assert len(old['inputs']) == 2262 and sum(len(v) for v in fixed.values()) == 60
    write(BASE / 'baseline.json', dict(inputs=old['inputs'], files=rows, contract_sha256=sha(CONTRACT), fixed_methods=fixed, base_source=old['source'], base_build=old['build'], model='SHUAIAPI/gpt-6.1-sol', provider='opencodex'))
    for name in DOCS:
        dst = BASE / 'documentation-before' / name
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / name, dst)
    prompt = '你是用户指定的 SHUAIAPI/gpt-6.1-sol / opencodex 实施工作者。父线程已验收 Session/Inbox，已独立审查生命周期只读分析和 Registry 生产源码，选定 Registry 两文件窄合同。\n'
    prompt += '必须完整读取合同 ' + str(CONTRACT) + '、只读报告 /tmp/harness-worktree-chat-archive-owner-followup-analysis-20261010.md 的第2节、基线 ' + str(BASE / 'baseline.json') + '。唯一写集为合同两文件。先分析后实施；理解代码先 CodeGraph，核对生产路径，不用 verification 同名快照。\n'
    prompt += '保留全部 dirty/untracked；禁止 reset/stash/clean/checkout/commit/push、修改用户配置、杀既有进程或派其他工作者。持续任务 Comet probe 已 none，不重跑。只实施及静态检查，禁止 test/build/screenshot/native app/PTY/资源运行；父线程等你 exit 后独立冻结串行验收。任何既有测试和 fixture 字节不得改。\n'
    prompt += '无新公共 API，不改 owner dispose、driver、Runtime。全程 catalog permit 在 mutex 前取得，覆盖 setup/factory/publication/listener/notices/最后取消检查/NonCancellable rollback；creating 与 entry 精确 identity，loaded Session attach 前后所有权和转移间隙正确。普通 Handle 拒绝 Closing 后可重试，旧 identity 不获取许可或触碰 replacement。严格按合同六个新方法，有实质并发/取消断言。\n'
    prompt += '如合同无法满足，报告具体事实并停止，不扩大写集、不弱化固定方法。交付 ' + str(DELIVERY) + ' 和同名.md，含 model/provider、baseline_sha256、contract_sha256、files(两项 path/before_sha256/after_sha256)、tests_run=false/builds_run=false/screenshots_run=false/native_execution_run=false、write_set_released=true/source_writes_stopped=true，记录限制。交付后不再写入并 exit。\n'
    Path(WORKER + 'prompt').write_text(prompt)
    print('PREPARED', len(old['inputs']), 'inputs; 2 files; 60 fixed methods', flush=True)
def dispatch():
    argv=['/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex','exec','--ephemeral','-C',str(REPO),'--sandbox','danger-full-access','-c','approval_policy="never"','-c','model_provider="opencodex"','-c','model_reasoning_effort="high"','--model','SHUAIAPI/gpt-6.1-sol','--json','--output-last-message',WORKER+'md','-']
    assert not Path(WORKER+'dispatch.json').exists()
    with Path(WORKER+'prompt').open('rb') as inp,Path(WORKER+'jsonl').open('wb') as out,Path(WORKER+'stderr').open('wb') as err:
        proc=subprocess.Popen(argv,cwd=REPO,stdin=inp,stdout=out,stderr=err)
        write(Path(WORKER+'dispatch.json'),dict(pid=proc.pid,argv=argv,time=time.time(),model='SHUAIAPI/gpt-6.1-sol',provider='opencodex'))
        print('WORKER',proc.pid,'SHUAIAPI/gpt-6.1-sol / opencodex',flush=True)
        code=proc.wait()
    baseline=json.loads((BASE/'baseline.json').read_text())
    checker.check(REPO,baseline['inputs'],ALLOWED)
    for name in DOCS:
        assert sha(REPO/name)==sha(BASE/'documentation-before'/name),name
    write(Path(WORKER+'exit.json'),dict(pid=proc.pid,exit=code,protected_inputs_unchanged=True,time=time.time()))
    print('WORKER EXIT',code,flush=True)
    raise SystemExit(code)
def freeze(attempt):
    exit_record=json.loads(Path(WORKER+'exit.json').read_text())
    assert exit_record['exit']==0 and exit_record['protected_inputs_unchanged']
    baseline=json.loads((BASE/'baseline.json').read_text());delivery=json.loads(DELIVERY.read_text())
    assert delivery['model']=='SHUAIAPI/gpt-6.1-sol' and delivery['provider']=='opencodex'
    assert delivery['baseline_sha256']==sha(BASE/'baseline.json')
    assert delivery['contract_sha256']==baseline['contract_sha256']==sha(CONTRACT)
    assert all(delivery[k] is False for k in ('tests_run','builds_run','screenshots_run','native_execution_run'))
    assert all(delivery[k] is True for k in ('write_set_released','source_writes_stopped'))
    rows=delivery['files'];before={r['path']:r['before_sha256'] for r in baseline['files']}
    assert len(rows)==len(ALLOWED) and {r['path'] for r in rows}==set(ALLOWED)
    for row in rows:
        assert row['before_sha256']==before[row['path']] and row['after_sha256']==sha(REPO/row['path']),row
    checker.check(REPO,baseline['inputs'],ALLOWED)
    source,build,evidence=paths(attempt)
    assert not any(p.exists() for p in (source,build,evidence))
    shutil.copytree(baseline['base_source'],source,symlinks=True,ignore=shutil.ignore_patterns('build','.git','node_modules','__pycache__','docs','captures'))
    inputs=dict(baseline['inputs'])
    for name in ALLOWED:
        dst=source/name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(REPO/name,dst);inputs[name]=sha(dst)
    checker.check(source,inputs)
    for module,name in SUITES:
        actual=methods(source,module,name)
        assert len(actual)==len(baseline['fixed_methods'][name]) and set(actual)==set(baseline['fixed_methods'][name]),name
    shutil.copytree(baseline['base_build'],build,symlinks=True,ignore=shutil.ignore_patterns('docs'))
    (source/'docs').mkdir();(build/'docs').symlink_to(source/'docs',target_is_directory=True);evidence.mkdir()
    write(evidence/'source-manifest.json',dict(source=str(source),build=str(build),inputs=inputs,overlays=ALLOWED,suites=SUITES,model=delivery['model'],provider=delivery['provider']))
    for src,name in [(BASE/'baseline.json','baseline.json'),(CONTRACT,'contract.md'),(DELIVERY,'delivery.json'),(DELIVERY.with_suffix('.md'),'delivery.md'),(Path(__file__),'parent-verify.py')]:
        shutil.copy2(src,evidence/name)
    for suffix in ('dispatch.json','exit.json','jsonl','stderr','md','prompt'):
        shutil.copy2(Path(WORKER+suffix),evidence/('worker-'+suffix))
        shutil.copy2(Path('/tmp/harness-worktree-chat-archive-owner-followup-analysis-20261010.'+suffix),evidence/('analysis-worker-'+suffix))
    shutil.copytree(BASE/'documentation-before',evidence/'documentation-before')
    for folder,root in [('source-before',BASE/'before'),('source-after',REPO)]:
        for name in ALLOWED:
            if not (root/name).exists():continue
            dst=evidence/folder/name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/name,dst)
    with (evidence/'reviewed.diff').open('w') as log:
        for name in ALLOWED:
            bp=BASE/'before'/name
            r=subprocess.run(['diff','-u',str(bp) if bp.exists() else '/dev/null',str(REPO/name)],capture_output=True,text=True)
            assert r.returncode in (0,1);log.write(r.stdout)
    print('FROZEN',len(inputs),'inputs; 2-file overlay',flush=True)
def run(attempt):
    source,build,evidence=paths(attempt)
    manifest=json.loads((evidence/'source-manifest.json').read_text());baseline=json.loads((evidence/'baseline.json').read_text())
    review=json.loads((evidence/'parent-review.json').read_text())
    assert review['accepted'] and review['scope']=='registry-only' and review['source_review_complete']
    env=os.environ.copy();env.pop('JAVA_TOOL_OPTIONS',None);env['KOTLIN_CLI_JAVA_OPTIONS']='-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
    write(evidence/'environment.json',dict(JAVA_TOOL_OPTIONS='unset',KOTLIN_CLI_JAVA_OPTIONS=env['KOTLIN_CLI_JAVA_OPTIONS']))
    results=[]
    for module,name in SUITES:
        checker.check(source,manifest['inputs'])
        folder=evidence/name.rsplit('.',1)[-1];folder.mkdir();started=time.time()
        command=['./kotlin','test','--build-dir='+str(build),'-p','jvm','-m',module,'--include-classes='+name]
        print('START',name,flush=True)
        with (folder/'command.log').open('w') as log:r=subprocess.run(command,cwd=source,env=env,stdout=log,stderr=subprocess.STDOUT)
        totals=dict(tests=0,failures=0,errors=0,skipped=0);seen=[];raw=[]
        for xml in build.rglob('TEST-*.xml'):
            if xml.stat().st_mtime<started:continue
            suite=ET.parse(xml).getroot();cases=list(suite.iter('testcase'))
            assert len(cases)==int(suite.get('tests',0)) and all(c.get('classname')==name for c in cases)
            shutil.copy2(xml,folder/xml.name)
            raw.extend(c.get('name') for c in cases);seen.extend(c.get('name').removesuffix('()') for c in cases)
            for field in totals:totals[field]+=int(suite.get(field,0))
            for c in cases:
                for issue in c:
                    if issue.tag in ('failure','error'):print('FAIL',c.get('name'),issue.get('message','')[:2200],flush=True)
        n=len(baseline['fixed_methods'][name])
        result=dict(class_name=name,command=command,exit=r.returncode,expected=n,totals=totals,methods=seen,raw_methods=raw,elapsed_seconds=round(time.time()-started,2))
        write(folder/'summary.json',result);results.append(result);write(evidence/'test-results.json',dict(status='in-progress',suites=results))
        print('RESULT',name,totals,'exit',r.returncode,flush=True)
        if r.returncode or totals!=dict(tests=n,failures=0,errors=0,skipped=0) or len(seen)!=n or set(seen)!=set(baseline['fixed_methods'][name]):
            print('\n'.join((folder/'command.log').read_text().splitlines()[-65:]),flush=True);raise SystemExit(1)
    write(evidence/'test-results.json',dict(status='passed',suites=results,tests=sum(r['totals']['tests'] for r in results)))
    command=['./kotlin','build','-m','jvm-app','-p','jvm','--build-dir='+str(build)];print('START JVM build',flush=True)
    with (evidence/'jvm-app-build.log').open('w') as log:r=subprocess.run(command,cwd=source,env=env,stdout=log,stderr=subprocess.STDOUT)
    write(evidence/'jvm-app-build.json',dict(command=command,exit=r.returncode))
    if r.returncode:print('\n'.join((evidence/'jvm-app-build.log').read_text().splitlines()[-65:]),flush=True);raise SystemExit(1)
    checker.check(source,manifest['inputs']);checker.check(REPO,manifest['inputs'])
    print('PASSED',sum(r['totals']['tests'] for r in results),'tests and JVM build',flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=('prepare','dispatch','freeze','run'));p.add_argument('--attempt',default='registry-r1');a=p.parse_args()
    if a.action in ('prepare','dispatch'):globals()[a.action]()
    else:globals()[a.action](a.attempt)
