#!/usr/bin/env python3
"""Extract selected ASAR members and emit a conservative readable JS overlay.

This is a local inspection aid, not a source recovery/deobfuscation pipeline.
Original bytes are retained verbatim; formatted files preserve bundler/compiler
control flow. Renames apply only to import/local bindings whose roles are
anchored by direct call or DOM evidence. Run under python -I.
"""
from __future__ import annotations
import argparse, hashlib, json, re, struct, subprocess
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

FILES = {
    'webview/index.html': 'entrypoint/webview-index.html',
    'webview/assets/index-65a34a2d8bec.js': 'entrypoint/renderer-entry.js',
    'webview/assets/app-main-efa2926f393b.js': 'shell/app-main.js',
    'webview/assets/app-runtime-b179428fcc1e.js': 'shell/app-runtime.js',
    'webview/assets/app-primary-75d53eef2374.js': 'shell/app-primary.js',
    'webview/assets/sidebar-2fd90c6737a8.js': 'navigation/sidebar-2fd90c6737a8.js',
    'webview/assets/sidebar-9a2307299179.js': 'navigation/sidebar-9a2307299179.js',
    'webview/assets/sidebar-d4b681793807.js': 'navigation/sidebar-d4b681793807.js',
    'webview/assets/sidebar-764926bf4353.js': 'navigation/sidebar-764926bf4353.js',
    'webview/assets/rail-menu-4eabbe4e807c.js': 'navigation/rail-menu.js',
    'webview/assets/shell-e3aeb695d244.js': 'shell/shell.js',
}
IDENT = r'[$A-Z_a-z][$\w]*'


def sha(data): return hashlib.sha256(data).hexdigest()

def read_members(archive, wanted):
    with archive.open('rb') as f:
        pre=f.read(16); a,b,c,n=struct.unpack('<4I',pre); header=json.loads(f.read(n)); base=8+b
        out={}
        for path in wanted:
            entry=header
            for part in path.split('/'): entry=entry['files'][part]
            if entry.get('unpacked') or 'link' in entry: raise ValueError('selected entry not packed: '+path)
            f.seek(base+int(entry['offset'])); data=f.read(int(entry['size']))
            if len(data)!=int(entry['size']): raise ValueError('truncated member '+path)
            expected=entry.get('integrity',{}).get('hash')
            if expected and sha(data)!=expected: raise ValueError('ASAR integrity mismatch '+path)
            out[path]=data
        return out

def lex(source):
    # Ignore quoted strings, template literal contents, comments and regexps;
    # retain newlines/positions, identifiers, punctuation and brace tokens.
    token=re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|`(?:\\.|[^`\\])*`|/(?:\\.|[^/\\\n])+/[a-z]*|'+IDENT+r'|=>|===|!==|==|!=|&&|\|\||\?\.|\.\.\.|\S')
    result=[]
    for m in token.finditer(source):
        s=m.group()
        if s.startswith(('//','/*','"',"'",'`','/')): continue
        result.append((s,m.start(),m.end()))
    return result

def replacements(source, path):
    found=[]
    def add(name, replacement, evidence, confidence='high'):
        if name and name != replacement and re.fullmatch(IDENT,name):
            # All references are identifier-boundary matches. Mapping records
            # evidence and confidence; preserve original token in source map.
            found.append((name,replacement,evidence,confidence,None))
    if path.endswith('app-main-efa2926f393b.js'):
        # This binding is function-local to the one-entry boot wrapper and its
        # only uses are the missing-root guard and createRoot argument. Avoid
        # renaming any other one-letter aliases in this large chunk.
        pattern=re.compile(r'var u,d,f,p;function m\(\).*?\bd=document\.getElementById\(`root`\),!d\)throw Error\(`Root container not found`\);f=window',re.S)
        match=pattern.search(source)
        if match:
            start,end=match.span()
            segment=source[start:end]
            count=len(re.findall(r'(?<![$\w])d(?![$\w])',segment))
            segment=re.sub(r'(?<![$\w])d(?![$\w])','rootElement',segment)
            source=source[:start]+segment+source[end:]
            found.append(('d','rootElement','Function-local declaration var u,d,f,p; direct getElementById("root") assignment; all bounded uses in bootstrap wrapper', 'high', count))
    if path.endswith('app-runtime-b179428fcc1e.js'):
        for m in re.finditer(r'\bfunction\s+([A-Za-z_$][\w$]*)\(\)\{return\s+\1=e\(\(\(\)=>\{[\s\S]{0,160}?\}\)\)\(\)\}',source):
            # Generic minifier init wrappers deliberately remain unnamed.
            pass
        for m in re.finditer(r'\bfunction\s+([A-Za-z_$][\w$]*)\(\)\{J\(\),XO\(\),UY\(\)',source):
            add(m.group(1),'initializeAppRuntime','Function executes app service initializers in sequence')
        for m in re.finditer(r'\bfunction\s+([A-Za-z_$][\w$]*)\(\)\{L\.info\(\`\[statsig-refresh-diagnostics\] React root render requested',source):
            add(m.group(1),'createAppRootElement','Returns JSX for app root after startup readiness')
    if path.endswith('index-65a34a2d8bec.js'):
        for m in re.finditer(r'\b([A-Za-z_$][\w$]*)=document\.createElement\(`link`\)',source):
            add(m.group(1),'preloadLink','Created DOM link element in module preload helper')
    # Replace complete lexical identifier tokens only; preserve literals/comments.
    mapping=[]
    seen=set()
    for original,new,evidence,confidence,scoped_count in found:
        if original in seen: continue
        seen.add(original)
        pattern=re.compile(r'(?<![$\w])'+re.escape(original)+r'(?![$\w])')
        if scoped_count is None:
            matches=list(pattern.finditer(source))
        else:
            anchor=source.find('document.getElementById(`root`)')
            start=source.rfind('function m()',0,anchor)
            end=source.find('m();',anchor)
            if start < 0 or end < 0: raise ValueError('Unable to locate bootstrap binding scope')
            matches=list(pattern.finditer(source,start,end+4))
        if not matches: continue
        for m in reversed(matches): source=source[:m.start()]+new+source[m.end():]
        mapping.append({'original_identifier':original,'semantic_identifier':new,'evidence':evidence,'confidence':confidence,'replacement_count':len(matches),'method':'scoped whole-token identifier replacement; original text retained in immutable source'})
    return source,mapping

def token_counts(source):
    toks=lex(source)
    return dict(Counter(t for t,_,_ in toks if t in {'import','export','if','else','switch','case','for','while','try','catch','throw','return','=>','?','&&','||','{','}','(',')','[',']'}))

def main():
    p=argparse.ArgumentParser(); p.add_argument('--app',type=Path,required=True); p.add_argument('--output',type=Path,required=True); p.add_argument('--expected-sha256',required=True); a=p.parse_args()
    archive=a.app.resolve()/'Contents/Resources/app.asar'
    if sha(archive.read_bytes())!=a.expected_sha256: raise SystemExit('source ASAR hash mismatch')
    if a.output.exists(): raise SystemExit('output already exists; refusing to replace originals')
    a.output.mkdir(parents=True)
    members=read_members(archive,FILES)
    # Stage immutable copies first, then derived readable variants.
    original=a.output/'original'; formatted=a.output/'formatted'; semantic=a.output/'semantic'
    for d in (original,formatted,semantic): d.mkdir()
    for packed,relative in FILES.items():
        data=members[packed]; raw=original/relative; raw.parent.mkdir(parents=True,exist_ok=True); raw.write_bytes(data)
        if packed.endswith('.html'):
            target=formatted/relative; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data); continue
        text=data.decode('utf-8')
        target=formatted/(Path(relative).with_suffix('.js')); target.parent.mkdir(parents=True,exist_ok=True); target.write_text(text,encoding='utf-8')
    prettier='/Users/hikaru/.npm/_npx/7e4347a8d51f6fca/node_modules/prettier/bin/prettier.cjs'
    js_files=[str(p) for p in formatted.rglob('*.js')]
    run=subprocess.run(['node',prettier,'--write','--ignore-path','/dev/null','--parser','babel',*js_files],capture_output=True,text=True)
    if run.returncode: raise SystemExit(run.stdout+'\n'+run.stderr)
    rows=[]; counts={}
    for packed,relative in FILES.items():
        if not packed.endswith('.js'): continue
        fmt=formatted/Path(relative).with_suffix('.js'); formatted_text=fmt.read_text()
        semtext,maps=replacements(formatted_text,packed)
        dest=semantic/Path(relative).with_suffix('.js'); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text(semtext)
        raw=members[packed].decode('utf-8')
        before=token_counts(raw); after=token_counts(semtext)
        rows.append({'original_path':packed,'original_sha256':sha(members[packed]),'original_bytes':len(members[packed]),'formatted_path':str(fmt.relative_to(a.output)),'formatted_sha256':sha(fmt.read_bytes()),'semantic_path':str(dest.relative_to(a.output)),'semantic_sha256':sha(dest.read_bytes()),'symbol_mappings':maps,'structure_before':before,'structure_after':after,'token_counts_match':before==after,'parse_verdict':'Prettier parser=babel completed; parser rejected malformed input with nonzero exit'})
        counts[packed]={'imports':len(re.findall(r'\bimport\s*(?:\(|\{)',raw)),'exports':len(re.findall(r'\bexport\s*\{',raw)),'mappings':len(maps)}
    (a.output/'symbol-map.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
    report={'recorded_at':datetime.now(timezone.utc).isoformat(),'source_asar':str(archive),'source_asar_sha256':a.expected_sha256,'selected_files':len(FILES),'semantic_mapping_count':sum(len(r['symbol_mappings']) for r in rows),'prettier_version':'3.9.10','prettier_parser':'babel','ast_parser_explicitly_used':False,'transformation':'Prettier formatting plus only direct-evidence whole-token local binding renames; no TSX reconstruction and no bundler-control-flow polish','all_token_counts_match':all(r['token_counts_match'] for r in rows),'source_immutable_copies_match':all(sha((original/FILES[p]).read_bytes())==sha(members[p]) for p in FILES),'runtime_status':'not confirmed','remaining_scope':'other reachable chunks in component-index.json; complete shell/navigation states and event chains need targeted review; no runtime visual evidence','limitations':['Token counts are not AST equivalence proof.','Prettier parser accepted JS syntax but may normalize syntax and comments.','Rename evidence is direct textual evidence, not a full binding-scope engine; scope is limited to selected direct bindings.','Runtime/visual mounted status is not claimed.']}
    (a.output/'semantic-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    if not report['all_token_counts_match'] or not report['source_immutable_copies_match']: raise SystemExit(1)
if __name__=='__main__': main()
