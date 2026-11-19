// Audit local study artifacts as data; never import an analyzed archive member.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {createRequire} from 'node:module';
const [toolDir, root] = process.argv.slice(2);
const require = createRequire(path.join(toolDir, 'package.json'));
const {parse} = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
const load = file => JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
const selection = load('selection.json');
const bindings = load('binding-map.json');
const evidence = load('ui-evidence-index.json');
function assert(test, message) { if (!test) throw Error(message); }
function canon(value) {
  if (typeof value === 'bigint') return value.toString();
  if (Array.isArray(value)) return value.map(canon);
  if (!value || typeof value !== 'object') return value;
  const result = {};
  for (const key of Object.keys(value).sort()) {
    if (['start', 'end', 'loc', 'comments', 'errors', 'tokens', 'extra', 'leadingComments', 'trailingComments', 'innerComments'].includes(key)) continue;
    result[key] = key === 'shorthand' && value.type === 'ObjectProperty' ? false : canon(value[key]);
  }
  return result;
}
const digest = ast => hash(JSON.stringify(canon(ast)));
function scopeFor(ast, owner) {
  let found;
  traverse(ast, {
    Program(p) { if (owner === null) { found = p.scope; p.stop(); } },
    FunctionDeclaration(p) {
      if (p.node.id?.name !== owner) return;
      assert(!found, `Ambiguous scope: ${owner}`);
      found = p.scope;
    }
  });
  assert(found, `Missing scope: ${owner}`);
  return found;
}
let locations = 0, records = 0, mappings = 0;
function checkLocation(source, loc) {
  const prefix = source.slice(0, loc.utf16_offset);
  const lines = prefix.split('\n');
  assert(Buffer.byteLength(prefix) === loc.byte_offset, 'Byte offset mismatch');
  assert(lines.length === loc.line && lines.at(-1).length === loc.column, 'Line/column mismatch');
  locations++;
}
for (const file of selection.files) {
  const bytes = fs.readFileSync(path.join(root, 'original', file.original_path));
  assert(hash(bytes) === file.original_sha256, `Original hash: ${file.original_path}`);
  if (!file.original_path.endsWith('.js')) continue;
  const source = bytes.toString('utf8');
  const row = bindings.find(x => x.original_path === file.original_path);
  const indexed = evidence.find(x => x.original_path === file.original_path);
  assert(row && indexed, `Missing metadata: ${file.original_path}`);
  const originalDigest = digest(parse(source, {sourceType: 'module'}));
  const formatted = fs.readFileSync(path.join(root, 'formatted', file.readable_path), 'utf8');
  assert(digest(parse(formatted, {sourceType: 'module'})) === originalDigest, 'Formatted AST mismatch');
  const semantic = fs.readFileSync(path.join(root, 'semantic', file.readable_path), 'utf8');
  assert(hash(semantic) === row.semantic_sha256, 'Semantic hash mismatch');
  const ast = parse(semantic, {sourceType: 'module'});
  for (const mapping of [...row.mappings].reverse()) scopeFor(ast, mapping.owner).rename(mapping.name, mapping.old);
  assert(digest(ast) === originalDigest, 'Inverse scoped AST mismatch');
  for (const mapping of row.mappings) {
    for (const loc of [mapping.original_declaration, ...mapping.original_references, ...mapping.original_writes]) checkLocation(source, loc);
    for (const loc of [mapping.semantic_declaration, ...mapping.semantic_references]) checkLocation(semantic, loc);
    mappings++;
  }
  for (const item of indexed.evidence) {
    checkLocation(source, item);
    for (const imported of item.imports || []) checkLocation(source, imported.location);
    records++;
  }
}
assert(hash(fs.readFileSync(selection.source_asar)) === selection.source_asar_sha256, 'Source archive hash mismatch');
const result = {files: selection.files.length, javascript_files: bindings.length, mappings, evidence_records: records, checked_locations: locations, original_hashes_match: true, source_asar_hash_matches: true, formatted_asts_equal: true, inverse_scoped_asts_equal: true, location_offsets_consistent: true, runtime_confirmed: false};
fs.writeFileSync(path.join(root, 'audit-verification.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
