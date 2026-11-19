#!/usr/bin/env python3
"""Create immutable, formatted and evidence-mapped Codex bundle copies."""
from __future__ import annotations
import argparse, hashlib, json, re, struct, subprocess
from datetime import datetime, timezone
from pathlib import Path

FILES = {
 'webview/index.html':'entrypoint/webview-index.html',
 'webview/assets/index-65a34a2d8bec.js':'entrypoint/renderer-entry.js',
 'webview/assets/app-main-efa2926f393b.js':'shell/app-main.js',
 'webview/assets/app-runtime-b179428fcc1e.js':'shell/app-runtime.js',
 'webview/assets/app-primary-75d53eef2374.js':'shell/app-primary.js',
 'webview/assets/sidebar-2fd90c6737a8.js':'navigation/sidebar-2fd90c6737a8.js',
 'webview/assets/sidebar-9a2307299179.js':'navigation/sidebar-9a2307299179.js',
 'webview/assets/sidebar-d4b681793807.js':'navigation/sidebar-d4b681793807.js',
 'webview/assets/sidebar-764926bf4353.js':'navigation/sidebar-764926bf4353.js',
 'webview/assets/rail-menu-4eabbe4e807c.js':'navigation/rail-menu.js',
 'webview/assets/shell-e3aeb695d244.js':'shell/shell.js',
}
PRETTIER='/Users/hikaru/.npm/_npx/7e4347a8d51f6fca/node_modules/prettier/bin/prettier.cjs'

def sha(data): return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser(); p.add_argument('--app',type=Path,required=True); p.add_argument('--output',type=Path,required=True); p.add_argument('--expected-sha256',required=True); a=p.parse_args()
 archive=a.app.resolve()/'Contents/Resources/app.asar'
 if sha(archive.read_bytes())!=a.expected_sha256: raise SystemExit('source ASAR hash mismatch')
 if a.output.exists(): raise SystemExit('output exists; refusing to overwrite')
 a.output.mkdir(parents=True); roots={k:a.output/v for k,v in [('original','original'),('formatted','formatted'),('semantic','semantic')]}
 for d in roots.values(): d.mkdir()
 with archive.open('rb') as f:
  _,header_size,_,json_size=struct.unpack('<4I',f.read(16)); tree=json.loads(f.read(json_size)); base=8+header_size; rawmap={}
  for packed,rel in FILES.items():
   e=tree
   for part in packed.split('/'): e=e['files'][part]
   if e.get('unpacked') or 'link' in e: raise SystemExit('selected member not packed: '+packed)
   f.seek(base+int(e['offset'])); data=f.read(int(e['size']))
   if len(data)!=int(e['size']) or (e.get('integrity',{}).get('hash') and sha(data)!=e['integrity']['hash']): raise SystemExit('member integrity failure: '+packed)
   rawmap[packed]=data
 rows=[]
 for packed,rel in FILES.items():
  data=rawmap[packed]; orig=roots['original']/rel; orig.parent.mkdir(parents=True,exist_ok=True); orig.write_bytes(data)
  if not packed.endswith('.js'):
   for key in ('formatted','semantic'):
    q=roots[key]/rel; q.parent.mkdir(parents=True,exist_ok=True); q.write_bytes(data)
   continue
  text=data.decode(); semantic=text; mappings=[]
  if packed.endswith('app-main-efa2926f393b.js'):
   # The outer bootstrap's lexical `d` is assigned by getElementById(root),
   # checked for missing root, then passed as createRoot's container.
   pat=re.compile(r'var u,d,f,p;function m\(\).*?d=document\.getElementById\(`root`\),!d\)throw Error\(`Root container not found`\);',re.S)
   match=pat.search(semantic)
   if not match: raise SystemExit('app-main root binding pattern changed; refusing semantic rename')
   start=match.start(); end=semantic.find('m();',match.end())
   if end<0: raise SystemExit('app-main bootstrap scope boundary not found')
   scope=semantic[start:end+4]; count=len(re.findall(r'(?<![$\w])d(?![$\w])',scope))
   scope=re.sub(r'(?<![$\w])d(?![$\w])','rootElement',scope)
   semantic=semantic[:start]+scope+semantic[end+4:]
   mappings=[{'original_identifier':'d','semantic_identifier':'rootElement','evidence':'Function-local var d; direct document.getElementById(`root`) assignment, missing-root guard and argument to React createRoot','confidence':'high','replacement_count':count,'scope':'outer bootstrap function only'}]
  fmt=roots['formatted']/Path(rel).with_suffix('.js'); sem=roots['semantic']/Path(rel).with_suffix('.js')
  for q,s in ((fmt,text),(sem,semantic)): q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s)
  rows.append({'original_path':packed,'original_sha256':sha(data),'original_bytes':len(data),'formatted_path':str(fmt.relative_to(a.output)),'semantic_path':str(sem.relative_to(a.output)),'symbol_mappings':mappings})
 js=[str(x) for key in ('formatted','semantic') for x in roots[key].rglob('*.js')]
 run=subprocess.run(['node',PRETTIER,'--write','--ignore-path','/dev/null','--parser','babel',*js],capture_output=True,text=True)
 if run.returncode: raise SystemExit(run.stderr or run.stdout)
 for row in rows:
  row['formatted_sha256']=sha((a.output/row['formatted_path']).read_bytes()); row['semantic_sha256']=sha((a.output/row['semantic_path']).read_bytes())
 pairs=[]
 for row in rows: pairs += [str(roots['original']/FILES[row['original_path']]),str(a.output/row['formatted_path']),str(a.output/row['semantic_path'])]
 jscheck=r'''const p=require('/Users/hikaru/.npm/_npx/7e4347a8d51f6fca/node_modules/prettier');const c=require('crypto');const fs=require('fs');function n(x){if(typeof x==='bigint')return x.toString();if(Array.isArray(x))return x.map(n);if(x&&typeof x==='object'){const o={};for(const[k,v]of Object.entries(x)){if(['start','end','loc','comments','errors','tokens','extra','leadingComments','trailingComments','innerComments','__contentStart','__contentEnd'].includes(k))continue;if(k==='name'&&x.type==='Identifier'){o[k]='$ID';continue}o[k]=n(v)}return o}return x}Promise.all(process.argv.slice(1).map(async f=>{const a=(await p.__debug.parse(fs.readFileSync(f,'utf8'),{parser:'babel'})).ast;return c.createHash('sha256').update(JSON.stringify(n(a))).digest('hex')})).then(x=>console.log(x.join('\n'))).catch(e=>{console.error(e);process.exit(1)})'''
 check=subprocess.run(['node','-e',jscheck,*pairs],capture_output=True,text=True)
 if check.returncode: raise SystemExit(check.stderr)
 hashes=check.stdout.splitlines()
 for i,row in enumerate(rows):
  h=hashes[i*3:i*3+3]; row.update({'ast_fingerprint_original':h[0],'ast_fingerprint_formatted':h[1],'ast_fingerprint_semantic':h[2],'ast_structure_matches_format':h[0]==h[1],'ast_structure_matches_semantic':h[1]==h[2],'parse_verdict':'Prettier Babel parser accepted all three copies'})
 (a.output/'symbol-map.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
 report={'recorded_at':datetime.now(timezone.utc).isoformat(),'source_asar_sha256':a.expected_sha256,'selected_files':len(FILES),'semantic_mapping_count':sum(len(r['symbol_mappings']) for r in rows),'prettier_version':'3.9.10','parser':'babel','ast_fingerprint_method':'Prettier Babel AST with positions/comments removed and identifier names normalized','all_ast_structures_match':all(r['ast_structure_matches_format'] and r['ast_structure_matches_semantic'] for r in rows),'all_selected_originals_byte_identical':all(sha((roots['original']/FILES[k]).read_bytes())==sha(v) for k,v in rawmap.items()),'runtime_status':'not confirmed','remaining_scope':'complete shell/navigation semantics, remaining reachable UI chunks, runtime event traces and visual confirmation','limitations':['Normalized AST equality preserves tree shape but does not prove behavior or identifier binding correctness.','Only one directly evidenced local binding is semantically renamed.','No TSX reconstruction, inferred component bodies, runtime launch or visual measurement.']}
 (a.output/'semantic-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n'); print(json.dumps(report,ensure_ascii=False,indent=2))
 if not report['all_ast_structures_match'] or not report['all_selected_originals_byte_identical']: raise SystemExit(1)
if __name__=='__main__': main()
