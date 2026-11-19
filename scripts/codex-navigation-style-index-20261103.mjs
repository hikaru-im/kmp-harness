// Build a concise index of styles proven in extracted lazy CSS members.
import fs from 'node:fs';
import path from 'node:path';
const root=process.argv[2];
const evidence=JSON.parse(fs.readFileSync(path.join(root,'scoped-css/css-evidence.json'),'utf8'));
const terms=new Set(['--app-shell-sidebar-top-inset','--height-token-nav-row','_fallbackDragRegion_57y6d_2','_Navigation_1uxfp_2','_ConversationSidebar_1uxfp_2']);
const declarations=[];
for(const file of evidence.files)for(const row of file.evidence){
  if(terms.has(row.property)||row.context.some(c=>[...terms].some(term=>c.selector?.includes(term))))declarations.push({file:file.original_path,sha256:file.original_sha256,property:row.property,value:row.value,context:row.context,utf16_offset:row.utf16_offset,byte_offset:row.byte_offset,line:row.line,column:row.column,source:row.source});
}
fs.writeFileSync(path.join(root,'scoped-css/navigation-style-evidence.json'),JSON.stringify({scope:'Exact selected declarations in two statically discovered lazy stylesheet members; no runtime style computation.',runtime_confirmed:false,declarations},null,2)+'\n');
console.log(JSON.stringify({declarations:declarations.length}));
