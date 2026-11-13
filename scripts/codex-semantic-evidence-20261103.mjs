// Generate exact-source evidence records, never execute an analyzed member.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const [toolDir,root]=process.argv.slice(2);
const require=createRequire(path.join(toolDir,'package.json'));
const {parse}=require('@babel/parser');
const traverse=require('@babel/traverse').default;
const selection=JSON.parse(fs.readFileSync(path.join(root,'selection.json')));
const inventory=[];
for(const file of selection.files.filter(x=>x.original_path.endsWith('.js'))){
 const source=fs.readFileSync(path.join(root,'original',file.original_path),'utf8');
 const ast=parse(source,{sourceType:'module'});
 const locator=node=>({file:file.original_path,utf16_offset:node.start,byte_offset:Buffer.byteLength(source.slice(0,node.start)),line:node.loc.start.line,column:node.loc.start.column});
 const owner=p=>{const names=[];let x=p;while(x){if(x.isFunctionDeclaration()&&x.node.id)names.push(x.node.id.name);x=x.parentPath;}return names;};
 const text=n=>source.slice(n.start,n.end);
 const rows=[];
 traverse(ast,{
  ExportNamedDeclaration(p){for(const s of p.node.specifiers||[])rows.push({kind:'export',...locator(s),local:s.local?.name,exported:s.exported?.name||s.exported?.value,confidence:'exact-source'});},
  VariableDeclarator(p){const n=p.node;if(n.id.type==='ArrayPattern'&&n.init?.type==='CallExpression'){const call=text(n.init.callee);if(call.includes('useState')||call.includes('useReducer'))rows.push({kind:'react-state-binding',...locator(n),owner:owner(p),pattern:text(n.id),initializer:text(n.init),confidence:'exact-source'});}},
  AssignmentExpression(p){const n=p.node;let target=n.right;if(target?.type!=='CallExpression')return;let imports=[];p.traverse({ImportExpression(q){imports.push({specifier:q.node.source.value,location:locator(q.node)})}});if(imports.length&&n.left.type==='Identifier')rows.push({kind:'lazy-assignment-with-import',...locator(n),owner:owner(p),binding:n.left.name,imports,expression:text(n),confidence:'exact-source; lazy assignment is not activation'});},
  ObjectProperty(p){const n=p.node;const key=n.key.name??n.key.value;if(['onClick','onSelect','onChange','onOpenChange','onKeyDown','onScroll','onBeforeAction'].includes(key))rows.push({kind:'event-prop',...locator(n),owner:owner(p),event:key,value:text(n.value),confidence:'exact-source; event handling runtime unconfirmed'});if(['className','style'].includes(key))rows.push({kind:'style-prop',...locator(n),owner:owner(p),property:key,value:text(n.value),confidence:'exact-source; computed style unconfirmed'});},
  ConditionalExpression(p){const test=text(p.node.test);if(/(?:area|sidebar|scheduled|meetings|code-review|defense-factory|global-dictation|startupReady)/i.test(test))rows.push({kind:'conditional-branch',...locator(p.node.test),owner:owner(p),test,confidence:'exact-source; activation unconfirmed'});},
  IfStatement(p){const test=text(p.node.test);if(/(?:scheduled|meetings|code-review|defense-factory)/.test(test))rows.push({kind:'if-branch',...locator(p.node.test),owner:owner(p),test,confidence:'exact-source; activation unconfirmed'});},
 });
 inventory.push({...file,evidence:rows});
}
fs.writeFileSync(path.join(root,'ui-evidence-index.json'),JSON.stringify(inventory,null,2)+'\n');
console.log(JSON.stringify({files:inventory.length,evidence:inventory.reduce((n,x)=>n+x.evidence.length,0)}));
