// Independent replay of the navigation binding map; never execute app members.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';
const [toolDir,root]=process.argv.slice(2);
const require=createRequire(path.join(toolDir,'package.json'));
const {parse}=require('@babel/parser');
const traverse=require('@babel/traverse').default;
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const load=n=>JSON.parse(fs.readFileSync(path.join(root,n)));
const assert=(ok,message)=>{if(!ok)throw Error(message);};
function digest(ast){
 const hash=crypto.createHash('sha256');
 const omitted=new Set(['start','end','loc','comments','errors','tokens','extra','leadingComments','trailingComments','innerComments']);
 function visit(value){
  if(typeof value==='bigint'){hash.update(JSON.stringify(value.toString()));return;}
  if(Array.isArray(value)){hash.update('[');for(let i=0;i<value.length;i++){if(i)hash.update(',');visit(value[i]);}hash.update(']');return;}
  if(value&&typeof value==='object'){
   hash.update('{');let count=0;
   for(const key of Object.keys(value).sort()){
    if(omitted.has(key)||value[key]===undefined)continue;
    if(count++)hash.update(',');hash.update(JSON.stringify(key)+':');visit(key==='shorthand'&&value.type==='ObjectProperty'?false:value[key]);
   }
   hash.update('}');return;
  }
  hash.update(JSON.stringify(value)??'null');
 }
 visit(ast);return hash.digest('hex');
}
function scope(ast,owner){
 let found;
 traverse(ast,{Program(p){if(owner===null){found=p.scope;p.stop();}},FunctionDeclaration(p){if(p.node.id?.name===owner){assert(!found,'Ambiguous owner');found=p.scope;}}});
 assert(found,'Missing owner '+owner);return found;
}
const selection=load('selection.json'),map=load('binding-map.json');
let locations=0;
function checkLoc(text,loc,name){
 const prefix=text.slice(0,loc.utf16_offset),lines=prefix.split('\n');
 assert(Buffer.byteLength(prefix)===loc.byte_offset&&lines.length===loc.line&&lines.at(-1).length===loc.column,'Bad location');
 if(name)assert(text.slice(loc.utf16_offset,loc.utf16_offset+name.length)===name,'Incorrect declaration/reference token');
 locations++;
}
const files=[];
for(const item of selection.files){
 const bytes=fs.readFileSync(path.join(root,'original',item.original_path));
 assert(sha(bytes)===item.original_sha256,'Original hash mismatch');
 if(!item.original_path.endsWith('.js'))continue;
 const row=map.find(x=>x.original_path===item.original_path);assert(row,'Missing map');
 const source=bytes.toString('utf8');let ast=parse(source,{sourceType:'module'});const original=digest(ast);
 ast=null;traverse.cache.clear();global.gc?.();
 const formatted=fs.readFileSync(path.join(root,'formatted',item.readable_path),'utf8');
 ast=parse(formatted,{sourceType:'module'});assert(digest(ast)===original,'Formatted AST mismatch');
 ast=null;traverse.cache.clear();global.gc?.();
 const semantic=fs.readFileSync(path.join(root,'semantic',item.readable_path),'utf8');assert(sha(semantic)===row.semantic_sha256,'Semantic hash mismatch');
 ast=parse(semantic,{sourceType:'module'});
 for(const m of row.mappings){
  checkLoc(source,m.original_declaration,m.old);
  for(const l of m.original_references)checkLoc(source,l,m.old);
  for(const l of m.original_writes)checkLoc(source,l);
  checkLoc(semantic,m.semantic_declaration,m.name);
  for(const l of m.semantic_references)checkLoc(semantic,l,m.name);
 }
 for(const m of [...row.mappings].reverse())scope(ast,m.owner).rename(m.name,m.old);
 assert(digest(ast)===original,'Inverse replay mismatch');
 ast=null;traverse.cache.clear();global.gc?.();
 files.push({file:item.original_path,mappings:row.mappings.length,formatted_ast_equal:true,inverse_scoped_ast_equal:true});
 console.log(JSON.stringify(files.at(-1)));
}
assert(sha(fs.readFileSync(selection.source_asar))===selection.source_asar_sha256,'Source archive changed');
const result={files,original_hashes_match:true,source_asar_hash_matches:true,mappings:map.reduce((n,x)=>n+x.mappings.length,0),checked_locations:locations,location_and_identifier_tokens_match:true,semantic_names_proven_by_ast:false,runtime_confirmed:false};
fs.writeFileSync(path.join(root,'audit-verification.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({mappings:result.mappings,checked_locations:locations,audit_passed:true}));
