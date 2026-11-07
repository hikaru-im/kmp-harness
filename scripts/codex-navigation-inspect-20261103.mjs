// Bounded static binding lookup: never evaluate inspected source.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const [toolDir, root, file, ...names] = process.argv.slice(2);
const require = createRequire(path.join(toolDir, 'package.json'));
const {parse} = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const source = fs.readFileSync(path.join(root, 'original', file), 'utf8');
const ast = parse(source, {sourceType:'module'});
const result = {file, requests:[]};
function record(node) {
  return {start:node.start,end:node.end,byte_offset:Buffer.byteLength(source.slice(0,node.start)),line:node.loc.start.line,column:node.loc.start.column,source:source.slice(node.start,node.end)};
}
traverse(ast,{Program(p){
  for (const request of names) {
    let [name, owner] = request.split('@');
    const exported = name.startsWith('export:') ? name.slice(7) : null;
    if (exported) {
      const matches = ast.program.body.filter(n => n.type === 'ExportNamedDeclaration').flatMap(n => n.specifiers).filter(s => s.exported.name === exported);
      if (matches.length !== 1) throw Error('Missing or ambiguous export '+exported);
      name = matches[0].local.name;
    }
    let scope = p.scope;
    if (owner) {
      scope = null;
      p.traverse({FunctionDeclaration(q){if(q.node.id?.name === owner){if(scope)throw Error('Ambiguous owner');scope=q.scope;}}});
      if (!scope) throw Error('Missing owner '+owner);
    }
    const binding = scope.getOwnBinding(name);
    if(!binding){result.requests.push({name,owner,missing:true});continue;}
    const item = {name,owner:owner??null,declaration:record(binding.path.node),writes:[],references:[],dependencies:[]};
    if(binding.path.isImportSpecifier()) item.import_source = binding.path.parent.source.value;
    for(const w of binding.constantViolations) item.writes.push(record(w.node));
    for(const r of binding.referencePaths.slice(0,18)) item.references.push({owner:r.getFunctionParent()?.node.id?.name??null,...record(r.parentPath.node)});
    const dependencies=new Set();
    const addDependency = q => {if(q.isReferencedIdentifier()){const b=q.scope.getBinding(q.node.name);if(b?.scope===p.scope)dependencies.add(q.node.name);}};
    binding.path.traverse({Identifier:addDependency});
    for(const w of binding.constantViolations) w.traverse({Identifier:addDependency});
    item.dependencies=[...dependencies];
    result.requests.push(item);
  }
  p.stop();
}});
console.log(JSON.stringify(result,null,2));
