// Scoped reading aids for the bounded navigation study; analyzed members are data.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { createRequire } from "node:module";
const [toolDir, root] = process.argv.slice(2);
const require = createRequire(path.join(toolDir, "package.json"));
const { parse } = require("@babel/parser");
const traverse = require("@babel/traverse").default;
const generate = require("@babel/generator").default;
const hash = (x) => crypto.createHash("sha256").update(x).digest("hex");
const parseJS = (s) => parse(s, { sourceType: "module", attachComment: true });
const manifest = JSON.parse(fs.readFileSync(path.join(root, "selection.json")));
const specs = [];
function mapping(
  file,
  owner,
  old,
  name,
  evidence,
  anchors,
  confidence = "high",
) {
  specs.push({ file, owner, old, name, evidence, anchors, confidence });
}
const initial = "shell/app-initial.js";
for (const [old, name, evidence, anchors] of [
  [
    "l0r",
    "resolveSidebarArea",
    "Exact export alias w3t resolves to this function, whose ordered route/state tests return sidebar area strings",
    ["l0r as w3t", "function l0r(e,t)"],
  ],
  [
    "UXr",
    "isScheduledSidebarLocation",
    "Tests /scheduled and /automations or supported conversation routes with scheduled sidebar state",
    ["function UXr(e)", "sidebarAutomationRecommendation"],
  ],
  [
    "u0r",
    "resolveDedicatedSidebarDestination",
    "Returns builtin:agents or validated mcp sidebarDestinationId; area resolver excludes these locations",
    ["function u0r(e)", "return`builtin:agents`"],
  ],
  [
    "f0r",
    "sidebarAreaDefinitions",
    "Registry has path/sidebar/destinationId/rail per area",
    ['f0r={"defense-factory":', "sidebar:`conversations`"],
  ],
  [
    "dO",
    "areaNavigationEnabledState",
    "Derived UI state checks feature 3085093835 and excludes denied/unsupported-auth",
    ["dO=U(Q,", "`3085093835`"],
  ],
  [
    "e0r",
    "meetingsAreaEnabledState",
    "Derived state combines area navigation state, aR and feature 544772515; aR meaning remains unresolved",
    ["e0r=U(Q,", "`544772515`"],
  ],
  [
    "nfs",
    "createPrefetchableRouteComponent",
    "React.lazy wrapper with cached loader, prefetch and retry after rejected promise; export NM",
    ["nfs as NM", "function nfs(e)"],
  ],
])
  mapping(initial, null, old, name, evidence, anchors);
for (const [old, name, evidence, anchors] of [
  [
    "e",
    "location",
    "Parameter supplies pathname/search/state to ordered area tests",
    ["function l0r(e,t)"],
  ],
  [
    "t",
    "getState",
    "Optional state getter used for meetings and defense-factory UI predicates",
    ["t(e0r)", "t?.(X1r)"],
  ],
  [
    "n",
    "spaceAreaFallback",
    "State meetings normalizes to space; otherwise sidebarArea or space",
    ["e.state?.sidebarArea===`meetings`?`space`:e.state?.sidebarArea??`space`"],
  ],
])
  mapping(initial, "l0r", old, name, evidence, anchors);
mapping(
  initial,
  "UXr",
  "e",
  "location",
  "Location parameter tested for scheduled routes/state",
  ["function UXr(e)"],
);
mapping(
  initial,
  "nfs",
  "e",
  "loadComponent",
  "Loader called through Promise.then and wrapped as default export",
  ["Promise.resolve().then(e).then(e=>({default:e}))"],
);
const primary = "shell/app-primary.js";
for (const [old, name, evidence, anchors] of [
  [
    "RD",
    "resolveSidebarArea",
    "Exact imported w3t alias; app-initial export targets l0r",
    ["w3t as RD"],
  ],
  [
    "km",
    "areaNavigationEnabledState",
    "Exact imported DUn alias; app-initial export targets dO",
    ["DUn as km"],
  ],
  [
    "JP",
    "retainedSidebarPreviewState",
    "Retains or clears peek state based on layout, explore and animation state; consumed as C?.area",
    ["JP=_o(Z,()=>null", "r.on(`change`"],
  ],
  [
    "vAt",
    "hoverSidebarPreviewState",
    "Watches peek overlay state and pointer/focus/media conditions, sets {area,productMode} or null",
    ["vAt=_o(Z,()=>null", "data-slate-sidebar-peek-area"],
  ],
  [
    "GP",
    "sidebarOverlayState",
    "Nullable {type:peek|explore,...} state; other types are not inferred",
    ["GP=yr(Z,null)"],
  ],
  [
    "oAt",
    "readSidebarPeekTarget",
    "Reads permitted data-slate-sidebar-peek-area and product-mode attributes",
    ["function oAt(e)", "data-slate-sidebar-peek-product-mode"],
  ],
  [
    "gc",
    "createLazySidebarComponent",
    "Exact V8 import alias resolves to shared UBt helper, which adds Suspense/fallback/preload/prefetch",
    ["V8 as gc"],
  ],
])
  mapping(primary, null, old, name, evidence, anchors);
for (const [old, name, evidence, anchors] of [
  [
    "d",
    "areaNavigationEnabled",
    "J(km) UI state; not proof feature enabled at runtime",
    ["d=J(km)"],
  ],
  [
    "h",
    "canReadSidebarPreview",
    "Area navigation enabled and trigger not browser-popover",
    ["h=d&&o!==`browser-popover`"],
  ],
  [
    "x",
    "routeLocation",
    "si() hook value passed to area and mode selectors",
    ["x=si()"],
  ],
  ["S", "currentArea", "Selector result Q(scr, location)", ["S=Q(scr,x)"]],
  [
    "C",
    "preview",
    "Nullable retained preview state read only when h",
    ["C=J(h?JP:null)"],
  ],
  ["w", "previewArea", "preview?.area or null", ["w=C?.area??null"]],
  [
    "T",
    "displayArea",
    "Preview area takes priority over current area",
    ["T=w??S"],
  ],
  [
    "z",
    "navigationMode",
    "jXe selector result compared to legacy/rail-only",
    ["z=Q(jXe,x)", "z!==`legacy`"],
  ],
  ["V", "hasNavigationRail", "Mode differs from legacy", ["V=z!==`legacy`"]],
  [
    "I",
    "footerHeight",
    "useState(0) updated from measured .height then serialized as px",
    ["[I,L]=(0,Y2.useState)(0)", "le=`${I}px`"],
  ],
  [
    "L",
    "setFooterHeight",
    "Paired footer height setter used by measurement callback",
    ["L(wi(e).height)"],
  ],
  [
    "Ee",
    "sidebarAccountKey",
    "Derived chatgpt:accountId or auth:authMethod key used as React key and accountKey",
    ["Ee=`chatgpt:${P}`", "Ee=`auth:${F}`"],
  ],
  [
    "qe",
    "hasAvailableCustomization",
    "te && availableDestinations length>0 && kXe value not null; describes composition, not complete policy",
    ["qe=te&&Ge&&Ke!=null"],
  ],
  [
    "tt",
    "showAlternateConversationSections",
    "Exact UI gate excludes area navigation and requires rail, active priority entry, je and !qe",
    ["tt=!d&&V&&Re!==`off`&&je&&!qe"],
  ],
])
  mapping(
    primary,
    "$sr",
    old,
    name,
    evidence,
    anchors,
    old === "qe" || old === "tt" ? "medium" : "high",
  );
const shared = "shell/app-shared.js";
for (const [old, name, evidence, anchors] of [
  [
    "UBt",
    "createLazySidebarComponent",
    "Export V8: cached loader, Suspense, optional parent suspension, fallback and error behavior",
    ["UBt as V8", "function UBt(e,"],
  ],
  [
    "xq",
    "createRouteObjectsFromElements",
    "React children validation and recursive Route/Fragment conversion; backing implementation of Rwt",
    ["function xq(e,t=[])", "is not a <Route> component."],
  ],
  [
    "n5e",
    "createRouteObjects",
    "Rwt export alias assigned xq",
    ["n5e=xq", "n5e as Rwt"],
  ],
  [
    "K3e",
    "useRouteObjects",
    "nTt export passes route objects and optional location into q3e",
    ["K3e as nTt", "function K3e(e,t)"],
  ],
  [
    "q3e",
    "useMatchedRouteElements",
    "Uses route context, optional location, route matching and nested rendering; names inferred from body",
    ["function q3e(e,t,n)"],
  ],
  [
    "Ug",
    "preloadImportDependencies",
    "FRt export creates dependency link elements and reports preload failure before import callback",
    ["Ug as FRt", "vite:preloadError"],
  ],
])
  mapping(shared, null, old, name, evidence, anchors, "medium");

// Reused canonicalization/scope approach from the verified sixth-round transformer.
function canon(x) {
  if (typeof x === "bigint") return x.toString();
  if (Array.isArray(x)) return x.map(canon);
  if (x && typeof x === "object") {
    const o = {};
    for (const k of Object.keys(x).sort()) {
      if (
        [
          "start",
          "end",
          "loc",
          "comments",
          "errors",
          "tokens",
          "extra",
          "leadingComments",
          "trailingComments",
          "innerComments",
        ].includes(k)
      )
        continue;
      if (k === "shorthand" && x.type === "ObjectProperty") {
        o[k] = false;
        continue;
      }
      o[k] = canon(x[k]);
    }
    return o;
  }
  return x;
}
function digest(ast) {
  const h = crypto.createHash("sha256");
  function visit(x) {
    if (typeof x === "bigint") {
      h.update(JSON.stringify(x.toString()));
      return;
    }
    if (Array.isArray(x)) {
      h.update("[");
      x.forEach((v, i) => {
        if (i) h.update(",");
        visit(v);
      });
      h.update("]");
      return;
    }
    if (x && typeof x === "object") {
      h.update("{");
      let count = 0;
      for (const k of Object.keys(x).sort()) {
        if (
          [
            "start",
            "end",
            "loc",
            "comments",
            "errors",
            "tokens",
            "extra",
            "leadingComments",
            "trailingComments",
            "innerComments",
          ].includes(k) ||
          x[k] === undefined
        )
          continue;
        if (count++) h.update(",");
        h.update(JSON.stringify(k) + ":");
        visit(k === "shorthand" && x.type === "ObjectProperty" ? false : x[k]);
      }
      h.update("}");
      return;
    }
    h.update(JSON.stringify(x) ?? "null");
  }
  visit(ast);
  return h.digest("hex");
}
function scopeFor(ast, owner) {
  let scope;
  traverse(ast, {
    Program(p) {
      if (owner === null) {
        scope = p.scope;
        p.stop();
      }
    },
    FunctionDeclaration(p) {
      if (p.node.id?.name === owner) {
        if (scope) throw Error(`Ambiguous owner ${owner}`);
        scope = p.scope;
      }
    },
  });
  if (!scope) throw Error(`Missing owner ${owner}`);
  return scope;
}
function loc(node, source) {
  return {
    utf16_offset: node.start,
    byte_offset: Buffer.byteLength(source.slice(0, node.start)),
    line: node.loc.start.line,
    column: node.loc.start.column,
  };
}
const results = [];
for (const item of manifest.files.filter((x) =>
  x.original_path.endsWith(".js"),
)) {
  const source = fs.readFileSync(
    path.join(root, "original", item.original_path),
    "utf8",
  );
  if (hash(source) !== item.original_sha256)
    throw Error("Original hash mismatch");
  let ast = parseJS(source);
  const base = digest(ast);
  const selected = specs
    .filter((s) => s.file === item.readable_path)
    .sort((a, b) => (a.owner === null) - (b.owner === null));
  const applied = [];
  for (const s of selected) {
    for (const a of s.anchors)
      if (!source.includes(a)) throw Error(`Missing evidence ${s.old}: ${a}`);
    const scope = scopeFor(ast, s.owner),
      binding = scope.getOwnBinding(s.old);
    if (!binding || scope.hasBinding(s.name))
      throw Error(`Missing/colliding binding ${s.old}/${s.name}`);
    const row = {
      ...s,
      original_declaration: loc(binding.identifier, source),
      original_references: binding.referencePaths.map((p) =>
        loc(p.node, source),
      ),
      original_writes: binding.constantViolations.map((p) =>
        loc(p.node, source),
      ),
    };
    scope.rename(s.old, s.name);
    applied.push(row);
  }
  const generated = generate(
    ast,
    { sourceMaps: true, sourceFileName: item.original_path, comments: true },
    source,
  );
  const file = path.join(root, "semantic", item.readable_path);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, generated.code + "\n");
  fs.writeFileSync(file + ".map", JSON.stringify(generated.map));
  const moduleNames = new Map(
    applied.filter((s) => s.owner === null).map((s) => [s.old, s.name]),
  );
  ast = null;
  traverse.cache.clear();
  global.gc?.();
  let semAST = parseJS(generated.code);
  for (const s of applied) {
    const owner = s.owner === null ? null : moduleNames.get(s.owner) || s.owner;
    const b = scopeFor(semAST, owner).getOwnBinding(s.name);
    s.semantic_declaration = loc(b.identifier, generated.code);
    s.semantic_references = b.referencePaths.map((p) =>
      loc(p.node, generated.code),
    );
  }
  for (const s of [...applied].reverse())
    scopeFor(semAST, s.owner).rename(s.name, s.old);
  if (digest(semAST) !== base)
    throw Error("Inverse scoped AST mismatch " + item.original_path);
  semAST = null;
  traverse.cache.clear();
  global.gc?.();
  let fmtAST = parseJS(
    fs.readFileSync(path.join(root, "formatted", item.readable_path), "utf8"),
  );
  if (digest(fmtAST) !== base) throw Error("Formatted AST mismatch");
  fmtAST = null;
  traverse.cache.clear();
  global.gc?.();
  results.push({
    ...item,
    mappings: applied,
    original_ast_sha256: base,
    semantic_sha256: hash(generated.code + "\n"),
    inverse_scoped_ast_equal: true,
    formatted_ast_equal: true,
  });
  console.log(
    JSON.stringify({ file: item.readable_path, mappings: applied.length }),
  );
}
fs.writeFileSync(
  path.join(root, "binding-map.json"),
  JSON.stringify(results, null, 2) + "\n",
);
fs.writeFileSync(
  path.join(root, "binding-verification.json"),
  JSON.stringify(
    {
      files: results.length,
      mappings: results.reduce((n, x) => n + x.mappings.length, 0),
      all_inverse_scoped_asts_equal: true,
      all_formatted_asts_equal: true,
      semantic_names_require_evidence_review: true,
      runtime_confirmed: false,
    },
    null,
    2,
  ) + "\n",
);
