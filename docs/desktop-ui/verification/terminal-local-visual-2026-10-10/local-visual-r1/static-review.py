from pathlib import Path
from collections import Counter
import json, re, subprocess
repo=Path('/Volumes/Data/IdeaProjects/kmp-harness')
base=Path('/tmp/harness-terminal-local-visual-baseline-20261010')
test='apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt'
before=(base/'before'/test).read_text();after=(repo/test).read_text()
def calls(text):
 out=[]
 for m in re.finditer(r'\b(assert\w+)\s*\(',text):
  index=m.end();depth=1;quote=None
  while depth and index<len(text):
   c=text[index]
   if quote:
    if c=='\\': index+=2;continue
    if c==quote: quote=None
   elif c in ('"',"'"): quote=c
   elif c=='(': depth+=1
   elif c==')': depth-=1
   index+=1
  assert depth==0
  out.append(re.sub(r'\s+',' ',text[m.start():index]).strip())
 return Counter(out)
old=calls(before);new=calls(after)
removed=list((old-new).elements());added=list((new-old).elements())
names=lambda text:re.findall(r'@Test\s+fun\s+(\w+)\s*\(',text)
assert set(names(after))==set(names(before))|{'terminalActionMenuUsesRealPointerKeyboardAndScope'}
assert len(names(after))==7
data=dict(old_methods=names(before),new_methods=names(after),old_assertion_calls=sum(old.values()),new_assertion_calls=sum(new.values()),removed_assertions=removed,added_assertions=added,unchanged_assertion_calls=sum((old&new).values()))
Path('/tmp/harness-terminal-local-visual-static-review-20261010.json').write_text(json.dumps(data,indent=2,ensure_ascii=False)+chr(10))
for row in json.loads((base/'baseline.json').read_text())['files']:
 p=repo/row['path'];assert p.read_bytes().endswith(bytes([10])) and bytes([13]) not in p.read_bytes()
 assert not any(line.rstrip()!=line for line in p.read_text().splitlines())
result=subprocess.run(['git','diff','--check'],cwd=repo,capture_output=True,text=True)
assert result.returncode==0,result.stdout+result.stderr
print(json.dumps(dict(old_methods=names(before),new_methods=names(after),unchanged_assertion_calls=data['unchanged_assertion_calls'],removed_assertions=removed,added_assertion_count=len(added)),ensure_ascii=False))
