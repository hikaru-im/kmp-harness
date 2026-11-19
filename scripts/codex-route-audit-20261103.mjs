// Verify extracted route bridge syntax and export evidence without execution.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';
const [toolDir, base] = process.argv.slice(2);
const require = createRequire(path.join(toolDir, 'package.json'));
const {parse} = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
const assert = (test, message) => {if (!test) throw Error(message);};
function canon(value) {
  if (typeof value === 'bigint') return value.toString();
  if (Array.isArray(value)) return value.map(canon);
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.keys(value).sort().filter(k => !['start','end','loc','comments','errors','tokens','extra','leadingComments','trailingComments','innerComments'].includes(k)).map(k => [k,canon(value[k])]));
}
const files = [];
for (const [folder, readable] of [['route-bridge-07','primary-app-route-components.js'],['authed-route-08','authed-route.js']]) {
  const root = path.join(base, folder);
  const selection = JSON.parse(fs.readFileSync(path.join(root, 'selection.json')));
  const original = fs.readFileSync(path.join(root, 'original', selection.original_path));
  const formatted = fs.readFileSync(path.join(root, 'formatted', readable), 'utf8');
  assert(hash(original) === selection.original_sha256, 'Original hash mismatch');
  const originalAST = parse(original.toString('utf8'), {sourceType:'module'});
  const formattedAST = parse(formatted, {sourceType:'module'});
  assert(JSON.stringify(canon(originalAST)) === JSON.stringify(canon(formattedAST)), 'Formatting AST mismatch');
  const exports = formattedAST.program.body.filter(n => n.type === 'ExportNamedDeclaration').flatMap(n => n.specifiers.map(s => ({local:s.local.name,exported:s.exported.name,line:s.loc.start.line})));
  const imports = formattedAST.program.body.filter(n => n.type === 'ImportDeclaration').map(n => ({source:n.source.value,specifiers:n.specifiers.map(s => ({local:s.local.name,imported:s.imported?.name})),line:n.loc.start.line}));
  files.push({folder,original_path:selection.original_path,original_sha256:selection.original_sha256,formatted_sha256:hash(formatted),original_hash_matches:true,formatted_ast_equal:true,imports,exports});
}
assert(files[0].exports.some(s => s.local === 'o' && s.exported === 't'), 'Primary bridge export changed');
assert(files[1].exports.some(s => s.local === 't' && s.exported === 'AuthedRoute'), 'Authenticated export changed');
const primarySource = fs.readFileSync(path.join(base,'semantic-restore-06/semantic/shell/app-primary.js'),'utf8');
const primaryAST = parse(primarySource,{sourceType:'module'});
const targetNames = {};
for (const n of primaryAST.program.body) if(n.type === 'ExportNamedDeclaration') for(const s of n.specifiers) if(['gd','hd','_d'].includes(s.exported.name)) targetNames[s.exported.name] = s.local.name;
const targets = [];
traverse(primaryAST,{Program(p){for(const [exported,local] of Object.entries(targetNames)) {const binding=p.scope.getBinding(local);assert(binding,`Missing binding ${local}`);targets.push({exported,local,line:binding.path.node.loc.start.line,source:primarySource.slice(binding.path.node.start,binding.path.node.end)});}p.stop();}});
assert(targets.length === 3, 'Expected three app-primary bridge exports');
const result = {files,app_primary_targets:targets,runtime_confirmed:false};
fs.writeFileSync(path.join(base,'route-bridge-07/route-verification.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({bridge_files:files.length,original_hashes_match:true,formatted_asts_equal:true,targets:targets.map(({exported,local,line})=>({exported,local,line})),runtime_confirmed:false},null,2));
