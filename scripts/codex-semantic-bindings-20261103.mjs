// Local analysis only: parse archive members as data; never import them.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
const [toolDir, outputDir] = process.argv.slice(2);
const require = createRequire(path.join(toolDir, 'package.json'));
const { parse } = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;
const hash = x => crypto.createHash('sha256').update(x).digest('hex');
const parseJS = s => parse(s, { sourceType: 'module', attachComment: true });
const manifest = JSON.parse(fs.readFileSync(path.join(outputDir, 'selection.json')));

// Every name is a reading aid. Export aliases are original facts; descriptive
// names retain confidence/evidence rather than claiming upstream source names.
const specs = [];
function mapping(file, owner, old, name, evidence, anchors, confidence = 'high') {
  specs.push({file, owner, old, name, evidence, anchors, confidence});
}
const main = 'shell/app-main.js';
for (const [old,name,evidence,anchors] of [
 ['d','rootElement','Module-local DOM root; missing-root guard and createRoot container',['document.getElementById(`root`)','!d','createRoot)(d)']],
 ['f','browserWindow','Module-local alias assigned window and used for __codexRoot',['f=window','f.__codexRoot']],
 ['p','reactRoot','Module-local root alias assigned __codexRoot and used to render initializer result',['p=f.__codexRoot','p.render(e)']],
 ['m','initializeRootBootstrap','Module bootstrap initializer creates root and loads window-specific runtime',['function m()','Root container not found','initializeApp']],
]) mapping(main,null,old,name,evidence,anchors);
const runtime='shell/app-runtime.js';
for (const [old,name,evidence,anchors,confidence] of [
 ['qY','initializeApp','Original named export is initializeApp',['qY as initializeApp'],'high'],
 ['JY','initializeRendererNavigation','Initial navigation and renderer_entry_evaluated timing mark',['Ug.navigate','renderer_entry_evaluated'],'high'],
 ['YY','markHostServicesReady','Marks host_services_ready, then calls two unresolved services',['host_services_ready'],'high'],
 ['XY','createAppRootElement','Returns JSX of FY with startupReady after host startup timing handling',['React root render requested','startupReady:e'],'high'],
 ['FY','AppRootFrame','Root StrictMode/error-boundary/Suspense frame and fallback drag region; descriptive role',['function FY(e)','fallbackDragRegion','name:`App`'],'medium'],
 ['rY','AppProviderTree','Consumes startupReady then composes nested providers/AppRoutes boundary; descriptive role',['function rY(e)','startupReady','name:`AppRoutes`'],'medium'],
 ['IY','markRendererReadyOnMount','Non-null ref marks renderer_ready then calls unresolved Zb',['renderer_ready'],'high'],
]) mapping(runtime,null,old,name,evidence,anchors,confidence);
mapping(runtime,'FY','n','startupReady','Destructured startupReady prop controls Suspense branch',['startupReady:n']);
mapping(runtime,'rY','n','startupReady','Destructured startupReady prop consumed by React use',['startupReady:n','_Y.use)(n)']);
mapping(runtime,'XY','e','hostStartupReady','Optional host startup readiness promise passed to root frame',['startupReady:e','host_startup_ready']);
const exports=[
 ['navigation/sidebar-2fd90c6737a8.js','he','DefenseFactorySidebar'],
 ['navigation/sidebar-9a2307299179.js','tn','MeetingsSidebar'],
 ['navigation/sidebar-d4b681793807.js','yr','CodeReviewSidebar'],
 ['navigation/sidebar-764926bf4353.js','Si','ScheduledSidebar'],
 ['navigation/rail-menu.js','kt','AmbientUsageRailMenu'],
];
for(const [file,old,name] of exports) mapping(file,null,old,name,'Exact original export alias, preserved as public API',[`export{${old} as ${name}}`]);
const scheduled='navigation/sidebar-764926bf4353.js';
for(const [old,name,evidence,anchors] of [
 ['o','searchQuery','Nullable useState value drives search input; null hides search',['[o,s]=(0,Gi.useState)(null)','searchQuery:o']],
 ['s','setSearchQuery','Setter paired with searchQuery, invoked by input and Escape',['[o,s]=(0,Gi.useState)(null)','s(null)']],
 ['d','normalizedSearchQuery','Trimmed lowercase query passed to query prop',['o?.trim().toLocaleLowerCase()','query:d']],
 ['c','searchTriggerRef','Ref on search toggle button, focused when Escape closes input',['ref:c','c.current?.focus()']],
 ['T','scrollContainerRef','Ref to scroll container, used to restore scrollTop',['T.current','ref:T']],
 ['g','statusFilter','Store value passed to statusFilter prop and scroll key',['statusFilter:g']],
 ['h','accountId','Value used in scroll persistence accountId key',['accountId:h']],
 ['_','ownerFilter','Store value used in owners persistence key',['owners:_']],
 ['v','routeLocation','Route object inspected for pathname/search/state',['v.pathname','v.search','v.state']],
 ['m','searchLabel','Localized searchTasks message used as aria-label and placeholder',['scheduled.sidebar.searchTasks','"aria-label":m']],
 ['P','toggleSearch','Toggles query between empty string and null; button onClick',['s(o==null?``:null)','onClick:P']],
 ['O','persistSidebarScroll','onScroll saves scrollTop only when normalized query is empty',['e.currentTarget.scrollTop','onScroll:O']],
 ['E','restoreSidebarScroll','Layout effect restores saved scroll or scrolls selected item',['e.scrollTop=','Gi.useLayoutEffect)(E,D)']],
 ['w','hasAutomationSelection','Boolean from scheduled/new route automationId or sidebarAutomationId',['sidebarAutomationId','new URLSearchParams(v.search).has(`automationId`)']],
 ['r','sidebarTriggerState','Default app-shell value passed to sidebarTriggerState',['n===void 0?`app-shell`:n','sidebarTriggerState:r']],
]) mapping(scheduled,'Si',old,name,evidence,anchors);
const rail='navigation/rail-menu.js';
for(const [old,name,evidence,anchors] of [
 ['m','isOpen','useState menu open value, bound to open prop',['[m,h]=(0,Pt.useState)(l)','open:m']],
 ['h','setOpen','Paired open setter bound to onOpenChange and close-before-action',['onOpenChange:h','h(!1)']],
 ['r','menu','Destructured menu prop providing actions',['menu:r','r.actions']],
 ['i','usageSource','Destructured source with kind/heading and usage calculations',['source:i','i?.kind']],
 ['o','usage','Destructured usage data with rate_limit',['usage:o','o?.rate_limit']],
 ['s','actions','Destructured actions passed to menuActions renderer',['actions:s']],
 ['d','intl','Value with formatMessage for Usage label',['d.formatMessage','codex.ambientUsage.railMenu.usage']],
 ['N','openUsageSettings','Callback closes menu then navigates /settings/usage',['f(`/settings/usage`','h(!1)']],
]) mapping(rail,'kt',old,name,evidence,anchors);
const primary='shell/app-primary.js';
for(const [old,name,evidence,anchors] of [
 ['Dar','SidebarAreaContent','Branch dispatcher on area to specialized sidebar JSX; descriptive role',['function Dar(e)','r===`scheduled`','r===`code-review`']],
 ['kar','LazyDefenseFactorySidebar','Lazy import resolves exact DefenseFactorySidebar export',['kar=gc(','e.DefenseFactorySidebar']],
 ['Aar','LazyMeetingsSidebar','Lazy import resolves exact MeetingsSidebar export',['Aar=gc(','e.MeetingsSidebar']],
 ['jar','LazyCodeReviewSidebar','Lazy import resolves exact CodeReviewSidebar export',['jar=gc(','e.CodeReviewSidebar']],
 ['Mar','LazyScheduledSidebar','Lazy import resolves exact ScheduledSidebar export',['Mar=gc(','e.ScheduledSidebar']],
]) mapping(primary,null,old,name,evidence,anchors,'high');
mapping(primary,'Dar','r','area','Destructured area drives sidebar dispatcher branches',['area:r','r===`scheduled`']);
mapping(primary,'Dar','o','sidebarTriggerState','Destructured prop forwarded to specialized sidebars',['sidebarTriggerState:o']);

function canon(x) {
 if(typeof x==='bigint')return x.toString();
 if(Array.isArray(x))return x.map(canon);
 if(x&&typeof x==='object') {
  const o={};
  for(const k of Object.keys(x).sort()) {
   if(['start','end','loc','comments','errors','tokens','extra','leadingComments','trailingComments','innerComments'].includes(k))continue;
   if(k==='shorthand' && x.type==='ObjectProperty') {o[k]=false;continue;}
   o[k]=canon(x[k]);
  }
  return o;
 }
 return x;
}
function digestAST(ast){return hash(JSON.stringify(canon(ast)));}
function scopeFor(ast,owner){
 let scope;
 traverse(ast,{Program(p){if(owner===null){scope=p.scope;p.stop();}},FunctionDeclaration(p){if(p.node.id?.name===owner){if(scope)throw Error(`Ambiguous owner ${owner}`);scope=p.scope;}}});
 if(!scope)throw Error(`Missing owner scope ${owner}`);return scope;
}
function loc(node,source){return {utf16_offset:node.start,byte_offset:Buffer.byteLength(source.slice(0,node.start)),line:node.loc.start.line,column:node.loc.start.column};}
const results=[];
for(const item of manifest.files.filter(x=>x.original_path.endsWith('.js'))) {
 const source=fs.readFileSync(path.join(outputDir,'original',item.original_path),'utf8');
 const ast=parseJS(source), baseAST=digestAST(ast);
 // Apply function-local mappings first; module aliases last. This makes scope
 // ownership stable during transforms and reversible without offset guessing.
 const selected=specs.filter(s=>s.file===item.readable_path).sort((a,b)=>(a.owner===null)-(b.owner===null));
 const applied=[];
 for(const s of selected) {
  for(const anchor of s.anchors)if(!source.includes(anchor))throw Error(`Evidence anchor missing ${s.file}/${s.old}: ${anchor}`);
  const scope=scopeFor(ast,s.owner), binding=scope.getOwnBinding(s.old);
  if(!binding)throw Error(`Missing own binding ${s.file}/${s.owner}/${s.old}`);
  if(scope.hasBinding(s.name))throw Error(`Rename collision ${s.name}`);
  const row={...s,original_declaration:loc(binding.identifier,source),original_references:binding.referencePaths.map(p=>loc(p.node,source)),original_writes:binding.constantViolations.map(p=>loc(p.node,source)),binding_kind:binding.kind};
  scope.rename(s.old,s.name);applied.push(row);
 }
 const generated=generate(ast,{sourceMaps:true,sourceFileName:item.original_path,comments:true},source);
 const semanticFile=path.join(outputDir,'semantic',item.readable_path);fs.mkdirSync(path.dirname(semanticFile),{recursive:true});fs.writeFileSync(semanticFile,generated.code+'\n');fs.writeFileSync(semanticFile+'.map',JSON.stringify(generated.map));
 // Parse the generated JS, then reverse each scoped rename. Identifier names,
 // property keys, export names, free variables and literal values are retained
 // in this comparison (only positions/comments/raw spellings are excluded).
 const reverse=parseJS(generated.code);
 for(const s of [...applied].reverse())scopeFor(reverse,s.owner).rename(s.name,s.old);
 const reverseHash=digestAST(reverse);
 if(baseAST!==reverseHash)throw Error(`Inverse scoped AST verification failed: ${item.original_path}`);
 const fmt=parseJS(fs.readFileSync(path.join(outputDir,'formatted',item.readable_path),'utf8'));
 const formattedHash=digestAST(fmt);
 if(baseAST!==formattedHash)throw Error(`Formatting AST mismatch: ${item.original_path}`);
 // Post-rename positions come from the actual generated file, not estimates.
 const semAST=parseJS(generated.code);
 const moduleNames=new Map(applied.filter(s=>s.owner===null).map(s=>[s.old,s.name]));
 for(const s of applied){const owner=s.owner===null?null:(moduleNames.get(s.owner)||s.owner);const b=scopeFor(semAST,owner).getOwnBinding(s.name);s.semantic_declaration=loc(b.identifier,generated.code);s.semantic_references=b.referencePaths.map(p=>loc(p.node,generated.code));}
 results.push({...item,mappings:applied,original_ast_sha256:baseAST,formatted_ast_sha256:formattedHash,semantic_inverse_ast_sha256:reverseHash,inverse_scoped_ast_equal:true,formatted_ast_equal:true,semantic_sha256:hash(generated.code+'\n')});
}
fs.writeFileSync(path.join(outputDir,'binding-map.json'),JSON.stringify(results,null,2)+'\n');
fs.writeFileSync(path.join(outputDir,'binding-verification.json'),JSON.stringify({files:results.length,mappings:results.reduce((n,x)=>n+x.mappings.length,0),all_inverse_scoped_asts_equal:results.every(x=>x.inverse_scoped_ast_equal),all_formatted_asts_equal:results.every(x=>x.formatted_ast_equal),identifier_names_preserved_in_comparison:true,tools:['@babel/parser@8.1.0','@babel/traverse@8.1.0','@babel/generator@8.1.0'],runtime_confirmed:false},null,2)+'\n');
console.log(JSON.stringify({files:results.length,mappings:results.reduce((n,x)=>n+x.mappings.length,0),inverse_ast_equal:true}));
