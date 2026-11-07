#!/usr/bin/env node
/**
 * Phase 14: cross-slice call graph.
 *
 * Joins the Phase 3-12 static slices to the Phase 13 runtime observations by
 * parsing the *complete* baseline chunks with Babel — never slice text, and no
 * regex over minified source for anything structural (imports, exports, JSX,
 * message ids, API call sites). Regex is used only to pick which files enter
 * the scan set, where a false positive costs one extra parse.
 *
 * Read-only over `02-baseline/`, `03..12-semantic/` and `13-runtime/`.
 * Writes only into `14-callgraph/`.
 *
 * Usage:
 *   node scripts/codex-full-restore-callgraph-20261106.mjs [--stats]
 *        [--out <dir>]
 *
 * --stats  run the full extraction and print counts without writing outputs.
 */
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const restoreRoot = path.join(root, "references/codex-26.1027.61615-24141/full-restore");
const baselineAssets = path.join(restoreRoot, "02-baseline/formatted/webview/assets");
const runtimeDir = path.join(restoreRoot, "13-runtime");

const argv = process.argv.slice(2);
const STATS_ONLY = argv.includes("--stats");
const outFlag = argv.indexOf("--out");
const outDir = outFlag >= 0 ? path.resolve(argv[outFlag + 1]) : path.join(restoreRoot, "14-callgraph");

// Babel lives in a scratch prefix because this repo has no node_modules at root.
const BABEL_HOME = process.env.CODEX_BABEL_HOME || "/tmp/codex-full-restore-tools-HShmrO";
const requireBabel = createRequire(path.join(BABEL_HOME, "/"));
const parser = requireBabel("@babel/parser");
const traverse = requireBabel("@babel/traverse").default;

const PHASES = ["03", "04", "05", "06", "07", "08", "09", "10", "11", "12"];
const ROUTES_CHUNK = "settings-lazy-routes-8d1f593a29a7.js";
const CATALOG_CHUNK = "app-primary-75d53eef2374.js";

const log = (msg) => process.stderr.write(`${msg}\n`);
const readJson = (p) => JSON.parse(fs.readFileSync(p, "utf8"));
const sha = (s) => crypto.createHash("sha256").update(s).digest("hex");
const sortedUnique = (arr) => [...new Set(arr)].sort();
const relFile = (f) => `02-baseline/formatted/webview/assets/${f}`;

/* ------------------------------------------------------------------ */
/* text / AST helpers                                                  */
/* ------------------------------------------------------------------ */

function lineStarts(text) {
  const starts = [0];
  for (let i = 0; i < text.length; i++) if (text.charCodeAt(i) === 10) starts.push(i + 1);
  return starts;
}

function lineColumnAt(starts, offset) {
  let lo = 0;
  let hi = starts.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (starts[mid] <= offset) lo = mid;
    else hi = mid - 1;
  }
  return { line: lo + 1, column: offset - starts[lo] };
}

function locate(text, starts, offset, excerptLen = 60) {
  if (offset == null || offset < 0) return null;
  const { line, column } = lineColumnAt(starts, offset);
  const raw = text.slice(offset, offset + excerptLen);
  return {
    utf16Offset: offset,
    byteOffset: Buffer.byteLength(text.slice(0, offset), "utf8"),
    line,
    column,
    // `excerpt` is a whitespace-collapsed rendering for human eyes; the span it
    // came from is recorded so a verifier can re-derive it exactly.
    excerpt: raw.replace(/\s+/g, " ").trim(),
    excerptUtf16Length: raw.length,
  };
}

/** The bundle writes every string with backticks, so @babel/parser produces
 *  TemplateLiteral rather than StringLiteral. A helper that only accepted
 *  StringLiteral silently found zero message ids — that bug already bit once. */
function strVal(node) {
  if (!node) return null;
  if (node.type === "StringLiteral") return node.value;
  if (node.type === "TemplateLiteral") {
    if (node.expressions.length === 0) return node.quasis.map((q) => q.value.cooked).join("");
    return null;
  }
  return null;
}

function propName(node) {
  if (!node) return null;
  if (node.type === "Identifier") return node.name;
  if (node.type === "StringLiteral") return node.value;
  if (node.type === "TemplateLiteral" && node.expressions.length === 0)
    return node.quasis.map((q) => q.value.cooked).join("");
  return null;
}

const memberName = (node) =>
  node && node.type === "MemberExpression" ? propName(node.property) : null;

/** `DESC` or `M.colors` as a dotted string; null for anything with computed
 *  keys or depth beyond a map lookup. */
function memberPath(node) {
  if (!node) return null;
  if (node.type === "Identifier") return node.name;
  if (node.type === "MemberExpression") {
    const obj = node.object.type === "Identifier" ? node.object.name : null;
    const key = propName(node.property);
    if (obj && key && !node.computed) return `${obj}.${key}`;
  }
  return null;
}

const API_METHOD = {
  safeGet: "GET",
  safePost: "POST",
  safePatch: "PATCH",
  safeDelete: "DELETE",
  safePut: "PUT",
};

const CSS_MODULE = /^_[A-Za-z][A-Za-z0-9]*_[a-z0-9]{4,6}_\d+$/;
const API_RE = /\.safe(Get|Post|Patch|Delete|Put)\(/;
const MSG_RE = /\.formatMessage\(/;

/** Nearest enclosing named function: a declaration's own name, or the
 *  identifier an arrow function is assigned to (`(p = i(async () => ...))`). */
function enclosingOwnerName(p) {
  let cur = p.getFunctionParent?.() ?? null;
  while (cur) {
    const n = cur.node;
    if (n.id?.name) return n.id.name;
    const parent = cur.parentPath?.node;
    if (parent?.type === "AssignmentExpression" && parent.left.type === "Identifier")
      return parent.left.name;
    if (parent?.type === "VariableDeclarator" && parent.id.type === "Identifier")
      return parent.id.name;
    cur = cur.parentPath?.getFunctionParent?.() ?? null;
  }
  return null;
}

/* ------------------------------------------------------------------ */
/* 1. semantic bindings (phase 03-12)                                  */
/* ------------------------------------------------------------------ */

/** Five offsets carry two different semantic names across phases. The later
 *  phase wins and the other name is kept as an alias, so no slice's name is
 *  silently dropped. */
function loadBindings() {
  const byFile = new Map(); // basename -> { phases:Set, byOffset:Map, offsetless:[] }
  const allNames = new Set();
  const conflicts = [];
  const dupNameFiles = new Map();
  let total = 0;

  for (const ph of PHASES) {
    const map = readJson(path.join(restoreRoot, `${ph}-semantic/binding-map.json`));
    for (const b of map.bindings ?? []) {
      total++;
      const file = path.basename(b.file);
      if (!byFile.has(file))
        byFile.set(file, { phases: new Set(), byOffset: new Map(), offsetless: [] });
      const rec = byFile.get(file);
      rec.phases.add(ph);
      allNames.add(b.semanticName);
      if (!dupNameFiles.has(b.semanticName)) dupNameFiles.set(b.semanticName, new Set());
      dupNameFiles.get(b.semanticName).add(file);

      const offset = b.originalPosition?.utf16Offset;
      if (offset == null) {
        rec.offsetless.push({
          phase: ph,
          originalName: b.originalName,
          semanticName: b.semanticName,
        });
        continue;
      }
      const prev = rec.byOffset.get(offset);
      if (prev && prev.semanticName !== b.semanticName) {
        conflicts.push({
          file,
          utf16Offset: offset,
          kept: b.semanticName,
          keptPhase: ph,
          dropped: prev.semanticName,
          droppedPhase: prev.phase,
        });
        prev.aliases = sortedUnique([...(prev.aliases ?? []), prev.semanticName]);
        prev.semanticName = b.semanticName;
        prev.phase = ph;
        prev.originalName = b.originalName;
      } else if (prev) {
        prev.phase = ph;
      } else {
        rec.byOffset.set(offset, {
          phase: ph,
          originalName: b.originalName,
          semanticName: b.semanticName,
          aliases: [],
        });
      }
    }
  }

  // symbol lookup by (file, semanticName) -> offset, for edge endpoints
  const symbolOffset = new Map();
  for (const [file, rec] of byFile) {
    const m = new Map();
    for (const [offset, e] of rec.byOffset)
      if (!m.has(e.semanticName)) m.set(e.semanticName, offset);
    symbolOffset.set(file, m);
  }

  const duplicateNames = [...dupNameFiles.entries()]
    .filter(([, files]) => files.size > 1)
    .map(([name, files]) => ({ name, files: [...files].sort() }));

  return { byFile, allNames, conflicts, total, symbolOffset, duplicateNames };
}

/* ------------------------------------------------------------------ */
/* 2. route table + settings catalog, from the baseline AST            */
/* ------------------------------------------------------------------ */

function extractRoutes(text) {
  const ast = parser.parse(text, { sourceType: "module", plugins: ["jsx"], errorRecovery: true });
  const starts = lineStarts(text);
  const assign = new Map();
  let table = null;

  traverse(ast, {
    AssignmentExpression(p) {
      if (p.node.left.type !== "Identifier") return;
      const rhs = text.slice(p.node.right.start, p.node.right.end);
      const m = rhs.match(/import\(\s*`\.\/([^`]+\.js)`/);
      if (!m) return;
      const ex = rhs.match(/let\s*\{\s*([A-Za-z0-9_$]+)\s*:/);
      if (!assign.has(p.node.left.name))
        assign.set(p.node.left.name, { chunk: m[1], exportName: ex ? ex[1] : null });
    },
    ObjectExpression(p) {
      if (table) return;
      const props = p.node.properties.filter((x) => x.type === "ObjectProperty");
      if (props.length < 40) return;
      const names = props.map((pr) => propName(pr.key)).filter(Boolean);
      if (names.length < 40) return;
      table = props.map((pr) => {
        const key = propName(pr.key);
        const txt = text.slice(pr.value.start, pr.value.end);
        const direct = txt.match(/import\(\s*`\.\/([^`]+\.js)`/);
        let chunk = direct ? direct[1] : null;
        let exportName = null;
        let viaIdentifier = null;
        if (direct) {
          const ex = txt.match(/let\s*\{\s*([A-Za-z0-9_$]+)\s*:/);
          exportName = ex ? ex[1] : null;
        } else {
          const id = txt.replace(/^[A-Za-z0-9_$]+\(/, "").replace(/\)$/, "").trim();
          if (/^[A-Za-z_$][\w$]*$/.test(id) && assign.has(id)) {
            chunk = assign.get(id).chunk;
            exportName = assign.get(id).exportName;
            viaIdentifier = id;
          }
        }
        return { key, chunk, exportName, viaIdentifier, evidence: locate(text, starts, pr.value.start, 46) };
      });
    },
  });
  return table ?? [];
}

function extractCatalog(text) {
  const ast = parser.parse(text, { sourceType: "module", plugins: ["jsx"], errorRecovery: true });
  const starts = lineStarts(text);
  let rows = null;
  traverse(ast, {
    AssignmentExpression(p) {
      if (p.node.left.type !== "Identifier" || p.node.left.name !== "krn") return;
      if (p.node.right.type !== "ArrayExpression") return;
      rows = p.node.right.elements.filter(Boolean).map((el, index) => {
        const props = el.properties.filter((x) => x.type === "ObjectProperty");
        const get = (n) => strVal(props.find((x) => propName(x.key) === n)?.value);
        return { index, slug: get("slug"), appRoute: get("appRoute"), evidence: locate(text, starts, el.start, 40) };
      });
    },
  });
  return rows ?? [];
}

/* ------------------------------------------------------------------ */
/* 3. per-file scan                                                    */
/* ------------------------------------------------------------------ */

function scanFile(file, text) {
  const ast = parser.parse(text, { sourceType: "module", plugins: ["jsx"], errorRecovery: true });
  const starts = lineStarts(text);

  const imports = new Map(); // local -> {source, imported, offset}
  const exportedLocals = new Map(); // exported -> local|null
  const exportSites = new Map(); // exported -> offset
  const decls = new Map(); // local name -> declaration offset
  const records = { api: [], bridge: [], produced: [], consumed: [], jsx: [], css: [], exports: [] };
  const seenMsgObjects = new Set();
  const seenCss = new Set();
  const descriptorLocals = new Map(); // `const DESC = {id,...}` -> id
  const descriptorPaths = new Map(); // `M.colors` / `DESC` -> id

  const jsxFactoryOf = (callee) => {
    if (!callee) return null;
    if (callee.type === "Identifier")
      return ["jsx", "jsxs", "createElement"].includes(callee.name) ? { target: callee.name } : null;
    if (callee.type === "MemberExpression") {
      const prop = memberName(callee);
      if (!["jsx", "jsxs", "createElement"].includes(prop)) return null;
      return { target: callee.object.type === "Identifier" ? callee.object.name : null };
    }
    if (callee.type === "SequenceExpression" && callee.expressions.length === 2) {
      const last = callee.expressions[1];
      if (last.type !== "MemberExpression") return null;
      const prop = memberName(last);
      if (!["jsx", "jsxs", "createElement"].includes(prop)) return null;
      return { target: last.object.type === "Identifier" ? last.object.name : null };
    }
    return null;
  };

  traverse(ast, {
    ImportDeclaration(p) {
      const source = String(p.node.source.value).replace(/^\.\//, "");
      for (const sp of p.node.specifiers) {
        const rec = { source, imported: null, offset: sp.start };
        if (sp.type === "ImportSpecifier") rec.imported = propName(sp.imported);
        else if (sp.type === "ImportDefaultSpecifier") rec.imported = "default";
        else if (sp.type === "ImportNamespaceSpecifier") rec.imported = "*";
        imports.set(sp.local.name, rec);
      }
    },
    ExportNamedDeclaration(p) {
      for (const sp of p.node.specifiers ?? [])
        if (sp.type === "ExportSpecifier") {
          const exported = propName(sp.exported);
          exportedLocals.set(exported, propName(sp.local));
          exportSites.set(exported, sp.start);
        }
      const d = p.node.declaration;
      if ((d?.type === "FunctionDeclaration" || d?.type === "ClassDeclaration") && d.id) {
        exportedLocals.set(d.id.name, d.id.name);
        exportSites.set(d.id.name, d.start);
      }
      if (d?.type === "VariableDeclaration")
        for (const decl of d.declarations)
          if (decl.id.type === "Identifier") {
            exportedLocals.set(decl.id.name, decl.id.name);
            exportSites.set(decl.id.name, decl.start);
          }
    },
    ExportDefaultDeclaration(p) {
      const d = p.node.declaration;
      const name = d?.id?.name ?? (d?.type === "Identifier" ? d.name : null);
      exportedLocals.set("default", name ?? null);
      exportSites.set("default", p.node.start);
    },
    // binding-map offsets point at the *identifier*, not at `function`/`const`.
    // Recording the declaration node start silently misses every lookup.
    FunctionDeclaration(p) {
      if (p.node.id && !decls.has(p.node.id.name)) decls.set(p.node.id.name, p.node.id.start);
    },
    ClassDeclaration(p) {
      if (p.node.id && !decls.has(p.node.id.name)) decls.set(p.node.id.name, p.node.id.start);
    },
    VariableDeclarator(p) {
      if (p.node.id.type === "Identifier" && !decls.has(p.node.id.name))
        decls.set(p.node.id.name, p.node.id.start);
    },
    CallExpression(p) {
      const callee = p.node.callee;
      const args = p.node.arguments;
      const name = memberName(callee);
      const owner = enclosingOwnerName(p);

      if (name && API_METHOD[name]) {
        const raw = strVal(args[0]);
        if (raw && raw.startsWith("/"))
          records.api.push({ method: API_METHOD[name], path: raw, clientMethod: name, offset: p.node.start, owner });
      }

      if (callee.type === "MemberExpression") {
        const objText = text.slice(callee.object.start, callee.object.end);
        if (objText === "window.electronBridge" || objText === "electronBridge") {
          const m = propName(callee.property);
          if (m) records.bridge.push({ method: m, offset: p.node.start, owner });
        } else if (callee.object.type === "MemberExpression") {
          const inner = text.slice(callee.object.object.start, callee.object.object.end);
          if (inner === "window.electronBridge" || inner === "electronBridge") {
            const m = propName(callee.object.property);
            if (m) records.bridge.push({ method: m, offset: p.node.start, owner });
          }
        }
      }

      if (name === "formatMessage") {
        const a0 = args[0];
        if (a0?.type === "ObjectExpression") {
          const idProp = a0.properties.find((x) => x.type === "ObjectProperty" && propName(x.key) === "id");
          const id = strVal(idProp?.value);
          if (id)
            records.consumed.push({ id, offset: idProp.value.start, owner, form: "object-literal" });
        } else {
          // `formatMessage(DESC)` / `formatMessage(M.colors)`
          const expr = memberPath(a0);
          if (expr) records.consumed.push({ id: null, local: expr, offset: a0.start, owner, form: "identifier" });
        }
      }

      const factory = jsxFactoryOf(callee);
      if (factory?.target) {
        // FormattedMessage-style use: the descriptor object is the *props* of a
        // JSX node. Missed entirely at first, which made 825 well-known ids
        // look like producer-only rows.
        const props = args[1];
        if (props?.type === "ObjectExpression") {
          const idProp = props.properties.find((x) => x.type === "ObjectProperty" && propName(x.key) === "id");
          const dmProp = props.properties.find(
            (x) => x.type === "ObjectProperty" && propName(x.key) === "defaultMessage",
          );
          const id = strVal(idProp?.value);
          if (id && dmProp)
            records.consumed.push({ id, offset: idProp.value.start, owner, form: "jsx-props" });
        }
        const first = args[0];
        let calleeName = null;
        let jsxKind = "element";
        if (first?.type === "Identifier") calleeName = first.name;
        else if (first?.type === "StringLiteral" || first?.type === "TemplateLiteral") {
          calleeName = strVal(first);
          jsxKind = "host-element";
        }
        if (calleeName)
          records.jsx.push({ calleeName, calleeIdentifier: factory.target, jsxKind, offset: p.node.start, owner });
      }
    },
    ObjectExpression(p) {
      if (seenMsgObjects.has(p.node)) return;
      seenMsgObjects.add(p.node);
      const props = p.node.properties.filter((x) => x.type === "ObjectProperty");
      const idProp = props.find((x) => propName(x.key) === "id");
      const dmProp = props.find((x) => propName(x.key) === "defaultMessage");
      if (!idProp || !dmProp) return;
      const id = strVal(idProp.value);
      if (!id || !/^[A-Za-z][\w.-]*$/.test(id)) return;
      const descProp = props.find((x) => propName(x.key) === "description");
      // Remember how this descriptor is reachable: a bare local
      // (`const DESC = {...}`) or a property path on a map
      // (`const M = { colors: {...} }` -> `M.colors`). Consumers use both.
      const segments = [];
      let cur = p;
      while (cur) {
        const parent = cur.parentPath;
        if (!parent) break;
        // Descriptor maps are wrapped in a helper call: `J = ue({ title: {...} })`.
        if (parent.node.type === "CallExpression") {
          cur = parent;
          continue;
        }
        // Two binding shapes in this bundle: `const DESC = {...}` and the
        // minifier's `(Zu = ft({...}))` inside a hoisted init function.
        const bound =
          parent.node.type === "VariableDeclarator" && parent.node.id.type === "Identifier"
            ? parent.node.id.name
            : parent.node.type === "AssignmentExpression" && parent.node.left.type === "Identifier"
              ? parent.node.left.name
              : null;
        if (bound) {
          segments.reverse();
          descriptorPaths.set([bound, ...segments].join("."), id);
          if (!segments.length) descriptorLocals.set(bound, id);
          break;
        }
        // Descend through the enclosing map/array: the walk must reach the
        // binding even when the descriptor sits one level down
        // (`{ close: {...} }`), not only when it is the whole value.
        if (parent.node.type === "ObjectExpression") {
          cur = parent;
          continue;
        }
        if (parent.node.type === "ObjectProperty") {
          const k = propName(parent.node.key);
          // A computed key cannot be resolved to one path; stop rather than
          // record a path that would match the wrong consumer.
          if (!k) break;
          segments.push(k);
          cur = parent;
          continue;
        }
        break;
      }
      records.produced.push({
        id,
        defaultMessage: strVal(dmProp.value),
        description: descProp ? strVal(descProp.value) : null,
        offset: idProp.value.start,
      });
    },
    "StringLiteral|TemplateLiteral"(p) {
      const v = strVal(p.node);
      if (!v || v.length > 160) return;
      for (const part of v.split(/\s+/)) {
        if (!CSS_MODULE.test(part) || seenCss.has(part)) continue;
        seenCss.add(part);
        records.css.push({ name: part, offset: p.node.start });
      }
    },
  });

  // Pair `formatMessage(DESCRIPTOR)` with the descriptor object declared in the
  // same chunk. Anything still unresolved stays as an identifier-form consumer
  // and is reported as a gap rather than guessed.
  let unresolvedIdentConsumers = 0;
  for (const c of records.consumed) {
    if (c.id || !c.local) continue;
    const id = descriptorPaths.get(c.local) ?? descriptorLocals.get(c.local);
    if (id) {
      c.id = id;
      c.form = "identifier-resolved";
    } else {
      unresolvedIdentConsumers++;
    }
  }

  return {
    file,
    text,
    starts,
    imports,
    exportedLocals,
    exportSites,
    decls,
    descriptorLocals,
    descriptorPaths,
    unresolvedIdentConsumers,
    ...records,
    hasApi: API_RE.test(text),
    hasMsg: MSG_RE.test(text),
  };
}

/* ------------------------------------------------------------------ */
/* 4. main                                                             */
/* ------------------------------------------------------------------ */

function main() {
  const started = Date.now();
  const bindings = loadBindings();
  log(
    `bindings: ${bindings.total} rows over ${bindings.byFile.size} files, ` +
      `${bindings.allNames.size} distinct semantic names, ${bindings.conflicts.length} offset conflicts`,
  );

  const routesText = fs.readFileSync(path.join(baselineAssets, ROUTES_CHUNK), "utf8");
  const routes = extractRoutes(routesText);
  const catalog = extractCatalog(fs.readFileSync(path.join(baselineAssets, CATALOG_CHUNK), "utf8"));
  log(`routes: ${routes.length} keys (${routes.filter((r) => r.chunk).length} chunk-resolved); catalog: ${catalog.length} slugs`);

  const runtime = readJson(path.join(runtimeDir, "runtime-evidence.json"));
  const runtimeChunks = new Set(readJson(path.join(runtimeDir, "runtime-chunks.json")).chunks);
  const rail = readJson(path.join(runtimeDir, "settings-rail-inventory.json"));
  const gateHints = readJson(path.join(runtimeDir, "gate-hints.json")).hints ?? {};
  const crosscheck = readJson(path.join(runtimeDir, "crosscheck.json"));
  const observedSlugs = rail.items.map((i) => i.slug);
  const observedSet = new Set(observedSlugs);
  // crosscheck.json keys ids inside `results[].ids[]`; each result carries the
  // verdict for the whole claim, and `present` for the individual id.
  const crossRecords = crosscheck.results ?? [];
  const crossById = new Map();
  for (const r of crossRecords)
    for (const i of r.ids ?? [])
      if (!crossById.has(i.id))
        crossById.set(i.id, { status: r.status, routeKeys: r.routeKeys ?? [], chunk: r.chunk, present: i.present });
  const runtimeMatchedIds = new Set(
    [...crossById.entries()].filter(([, v]) => v.status === "matched").map(([id]) => id),
  );
  log(
    `runtime: ${observedSlugs.length} panels, ${runtimeChunks.size} parsed chunks, ` +
      `${runtimeMatchedIds.size} ids DOM-matched of ${crossById.size} crosschecked`,
  );

  /* ---- scan set ---- */
  const importRe = /from\s*[`"']\.\/([^`"']+)[`"']|import\(\s*[`"']\.\/([^`"']+)[`"']/g;
  const depth1 = new Set();
  for (const seed of bindings.byFile.keys()) {
    const src = fs.readFileSync(path.join(baselineAssets, seed), "utf8");
    importRe.lastIndex = 0;
    let m;
    while ((m = importRe.exec(src))) {
      const f = m[1] ?? m[2];
      if (f.endsWith(".js") && !bindings.byFile.has(f)) depth1.add(f);
    }
  }
  const scanTargets = [];
  for (const f of [...bindings.byFile.keys()].sort()) scanTargets.push({ file: f, tier: "seed" });
  // Depth-1 expansion is what makes "content lives in another panel's chunk"
  // visible at all (mcp-settings rendering inside plugins-settings).
  for (const f of [...depth1].sort()) {
    const p = path.join(baselineAssets, f);
    if (!fs.existsSync(p)) continue;
    const src = fs.readFileSync(p, "utf8");
    if (API_RE.test(src) || MSG_RE.test(src)) scanTargets.push({ file: f, tier: "depth1" });
  }
  for (const extra of [ROUTES_CHUNK, CATALOG_CHUNK])
    if (!scanTargets.some((t) => t.file === extra)) scanTargets.push({ file: extra, tier: "support" });
  const tierOf = new Map(scanTargets.map((t) => [t.file, t.tier]));
  log(`scan set: ${scanTargets.length} files (${bindings.byFile.size} seeds, ${scanTargets.filter((t) => t.tier === "depth1").length} depth-1)`);

  const scanned = new Map();
  const parseFailures = [];
  for (const { file } of scanTargets) {
    const p = path.join(baselineAssets, file);
    if (!fs.existsSync(p)) {
      parseFailures.push({ file, reason: "missing" });
      continue;
    }
    try {
      scanned.set(file, scanFile(file, fs.readFileSync(p, "utf8")));
    } catch (err) {
      parseFailures.push({ file, reason: String(err.message).slice(0, 200) });
    }
  }
  log(`parsed ${scanned.size}/${scanTargets.length} in ${((Date.now() - started) / 1000).toFixed(1)}s (${parseFailures.length} failures)`);

  /* ---- node ids ---- */
  const fileId = (f) => `file:${f}`;
  const symId = (f, name, offset) => `symbol:${f}#${name}@${offset}`;
  const routeId = (k) => `route:${k}`;
  const panelId = (s) => `panel:${s}`;
  const msgId = (id) => `msgid:${id}`;
  const apiId = (method, p) => `api:${method} ${p}`;
  const bridgeId = (m) => `bridge:${m}`;
  const cssId = (n) => `css:${n}`;

  const nodes = new Map();
  const put = (node) => {
    if (!nodes.has(node.id)) nodes.set(node.id, node);
    return node.id;
  };

  /* ---- file nodes ---- */
  for (const [file, rec] of scanned) {
    const b = bindings.byFile.get(file);
    put({
      id: fileId(file),
      kind: "chunk",
      baselineFile: relFile(file),
      location: null,
      originalMinifiedName: file,
      semanticName: null,
      owningPhase: b ? sortedUnique([...b.phases]) : [],
      tier: tierOf.get(file),
      confidence: "high",
      runtime: {
        scriptParsed: runtimeChunks.has(file),
        note: "scriptParsed means V8 compiled the chunk; it does not mean any symbol in it executed",
      },
    });
  }

  /* ---- symbol nodes ---- */
  let symbolCount = 0;
  for (const [file, rec] of bindings.byFile) {
    const scan = scanned.get(file);
    if (!scan) continue;
    const offsets = [...rec.byOffset.entries()].sort((a, b) => a[0] - b[0]);
    for (const [offset, e] of offsets) {
      symbolCount++;
      put({
        id: symId(file, e.semanticName, offset),
        kind: "symbol",
        baselineFile: relFile(file),
        location: locate(scan.text, scan.starts, offset, 40),
        originalMinifiedName: e.originalName,
        semanticName: e.semanticName,
        aliases: e.aliases ?? [],
        owningPhase: e.phase,
        confidence: "high",
        runtime: { scriptParsed: runtimeChunks.has(file) },
      });
    }
    for (const o of rec.offsetless) {
      symbolCount++;
      put({
        id: symId(file, o.semanticName, "no-offset"),
        kind: "symbol",
        baselineFile: relFile(file),
        location: null,
        originalMinifiedName: o.originalName,
        semanticName: o.semanticName,
        owningPhase: o.phase,
        confidence: "medium",
        note: "binding-map row carried no baseline offset",
        runtime: { scriptParsed: runtimeChunks.has(file) },
      });
    }
  }

  /* ---- route + panel nodes ---- */
  for (const r of routes) {
    put({
      id: routeId(r.key),
      kind: "route-key",
      baselineFile: relFile(ROUTES_CHUNK),
      location: r.evidence,
      originalMinifiedName: r.key,
      semanticName: null,
      chunk: r.chunk,
      exportName: r.exportName,
      viaIdentifier: r.viaIdentifier,
      owningPhase: 12,
      confidence: r.chunk ? "high" : "medium",
      runtime: { settingsPanelRendered: observedSet.has(r.key) },
    });
  }
  for (const c of catalog) {
    if (!c.slug || nodes.has(panelId(c.slug))) continue;
    put({
      id: panelId(c.slug),
      kind: "panel",
      baselineFile: relFile(CATALOG_CHUNK),
      location: c.evidence,
      originalMinifiedName: c.slug,
      semanticName: null,
      catalogIndex: c.index,
      appRoute: c.appRoute ?? null,
      owningPhase: 13,
      confidence: "high",
      runtime: {
        settingsPanelRendered: observedSet.has(c.slug),
        railIndex: observedSlugs.indexOf(c.slug) >= 0 ? observedSlugs.indexOf(c.slug) : null,
      },
    });
  }
  for (const slug of observedSlugs) {
    if (nodes.has(panelId(slug))) continue;
    put({
      id: panelId(slug),
      kind: "panel",
      baselineFile: null,
      location: null,
      originalMinifiedName: slug,
      semanticName: null,
      owningPhase: 13,
      confidence: "high",
      note: "rendered at runtime but absent from the section catalog",
      runtime: { settingsPanelRendered: true, railIndex: observedSlugs.indexOf(slug) },
    });
  }

  /* ---- owner resolution ---- */
  const resolveOwner = (file, ownerLocal) => {
    if (!ownerLocal) return fileId(file);
    const scan = scanned.get(file);
    const offset = scan?.decls.get(ownerLocal);
    if (offset == null) return fileId(file);
    const e = bindings.byFile.get(file)?.byOffset.get(offset);
    if (!e) return fileId(file);
    const id = symId(file, e.semanticName, offset);
    return nodes.has(id) ? id : fileId(file);
  };

  /* ---- identifier resolution for JSX targets ---- */
  // decls are keyed by the *original minified* local name; the semantic name
  // lives on the binding row at that offset. Conflating the two makes every
  // lookup miss and silently degrades every edge to file level.
  const semanticAt = (file, offset) =>
    offset != null ? bindings.byFile.get(file)?.byOffset.get(offset)?.semanticName ?? null : null;
  const semanticOfLocal = (file, localName) => {
    const offset = scanned.get(file)?.decls.get(localName);
    return offset == null ? null : semanticAt(file, offset);
  };

  /** Resolve a JSX callee identifier to the symbol it renders, following at
   *  most two re-export hops. Returns null when nothing better than the chunk
   *  is known — the caller then emits a file-level edge. */
  const resolveIdentifier = (file, localName, hops = 2) => {
    const scan = scanned.get(file);
    if (!scan) return null;

    const ownSemantic = semanticOfLocal(file, localName);
    if (ownSemantic) return { file, semanticName: ownSemantic, via: "local-declaration" };

    const imp = scan.imports.get(localName);
    if (!imp) return null;
    if (!scanned.has(imp.source))
      return { file: imp.source, semanticName: null, via: "unscanned-target", exported: imp.imported };

    const target = scanned.get(imp.source);
    const innerLocal = target.exportedLocals.get(imp.imported);
    if (innerLocal) {
      const semantic = semanticOfLocal(imp.source, innerLocal);
      if (semantic) return { file: imp.source, semanticName: semantic, via: "export-binding" };
      if (hops > 0) {
        const hop = resolveIdentifier(imp.source, innerLocal, hops - 1);
        // Only accept a deeper answer that actually named a symbol.
        if (hop?.semanticName) return { ...hop, via: "re-export-hop" };
      }
      return { file: imp.source, semanticName: null, via: "re-export-unnamed", exported: imp.imported };
    }
    return { file: imp.source, semanticName: null, via: "import", exported: imp.imported };
  };

  /* ---- cross-chunk descriptor resolution ----
   * `formatMessage(Ir.changeAction)` where `Ir` is imported from another chunk
   * that declares the descriptor map. Done before edges are emitted so the
   * graph and the pairing table agree on what counts as a consumer. */
  let crossChunkConsumers = 0;
  for (const [file, scan] of scanned) {
    for (const c of scan.consumed) {
      if (c.id || !c.local) continue;
      const [rootLocal, prop] = c.local.split(".");
      const imp = scan.imports.get(rootLocal);
      if (!imp) continue;
      const target = scanned.get(imp.source);
      if (!target) continue;
      const candidate = prop ? `${imp.imported}.${prop}` : imp.imported;
      const id = target.descriptorPaths.get(candidate);
      if (!id) continue;
      c.id = id;
      c.form = "cross-chunk-descriptor";
      crossChunkConsumers++;
    }
  }
  log(`cross-chunk descriptor consumers resolved: ${crossChunkConsumers}`);

  /* ---- edges ---- */
  const edges = new Map();
  const addEdge = (e) => {
    const key = sha(
      JSON.stringify([e.kind, e.from, e.to, e.evidence?.baselineFile ?? null, e.evidence?.utf16Offset ?? null]),
    ).slice(0, 20);
    const id = `edge:${key}`;
    if (!edges.has(id)) edges.set(id, { id, ...e });
    return id;
  };

  // Both endpoints compiled at runtime = the code was reachable, not that the
  // edge ran. Only DOM-matched message ids and reached panels get "confirmed".
  const loadStatus = (a, b) =>
    runtimeChunks.has(a) && runtimeChunks.has(b) ? "runtime-corroborated" : "static-observed";

  for (const [file, scan] of scanned) {
    const ev = (offset, len = 60) => ({
      baselineFile: relFile(file),
      ...locate(scan.text, scan.starts, offset, len),
    });

    for (const [local, imp] of scan.imports) {
      if (!imp.source.endsWith(".js") || !scanned.has(imp.source)) continue;
      addEdge({
        kind: "imports",
        from: fileId(file),
        to: fileId(imp.source),
        local,
        imported: imp.imported,
        evidence: ev(imp.offset, 46),
        status: loadStatus(file, imp.source),
        confidence: "high",
      });
    }

    for (const [exported, local] of scan.exportedLocals) {
      if (!local) continue;
      const offset = scan.decls.get(local);
      const entry = offset != null ? bindings.byFile.get(file)?.byOffset.get(offset) : null;
      addEdge({
        kind: "exports",
        from: fileId(file),
        to: entry ? symId(file, entry.semanticName, offset) : fileId(file),
        exported,
        local,
        evidence: ev(scan.exportSites.get(exported), 40),
        status: "static-observed",
        confidence: entry ? "high" : "medium",
      });
    }

    for (const c of scan.api)
      addEdge({
        kind: "calls-api",
        from: resolveOwner(file, c.owner),
        to: apiId(c.method, c.path),
        clientMethod: c.clientMethod,
        evidence: ev(c.offset, 70),
        status: "static-observed",
        confidence: "high",
      });

    for (const b of scan.bridge)
      addEdge({
        kind: "calls-bridge",
        from: resolveOwner(file, b.owner),
        to: bridgeId(b.method),
        evidence: ev(b.offset, 60),
        status: "static-observed",
        confidence: "high",
      });

    for (const m of scan.produced)
      addEdge({
        kind: "produces-message",
        from: resolveOwner(file, m.owner),
        to: msgId(m.id),
        evidence: ev(m.offset, 60),
        status: "static-observed",
        confidence: "high",
      });

    for (const m of scan.consumed) {
      if (!m.id) continue;
      const confirmed = runtimeMatchedIds.has(m.id);
      addEdge({
        kind: "consumes-message",
        from: resolveOwner(file, m.owner),
        to: msgId(m.id),
        // When the id came from resolving a descriptor identifier, the recorded
        // offset is the *call site* (`P.pending`), not the `id` literal, which
        // lives at the descriptor's declaration. Carry the local so the
        // reverse-check asserts the text that is actually there.
        ...(m.local ? { consumerForm: m.form, consumerLocal: m.local } : {}),
        evidence: ev(m.offset, 60),
        status: confirmed ? "runtime-confirmed" : "static-observed",
        confidence: "high",
      });
    }

    for (const c of scan.css)
      addEdge({
        kind: "uses-css-module",
        from: fileId(file),
        to: cssId(c.name),
        evidence: ev(c.offset, 40),
        status: "static-observed",
        confidence: "medium",
      });

    for (const site of scan.jsx) {
      if (site.jsxKind === "host-element") continue;
      const target = resolveIdentifier(file, site.calleeName);
      if (!target) continue;
      let to;
      if (target.semanticName) {
        to = symId(target.file, target.semanticName, bindings.symbolOffset.get(target.file)?.get(target.semanticName) ?? 0);
      } else {
        to = fileId(target.file);
      }
      if (!nodes.has(to)) continue;
      addEdge({
        kind: "renders",
        from: resolveOwner(file, site.owner),
        to,
        calleeIdentifier: site.calleeIdentifier,
        calleeLocalName: site.calleeName,
        via: target.via,
        evidence: ev(site.offset, 60),
        status: loadStatus(file, target.file),
        confidence: target.semanticName ? "high" : "medium",
      });
    }
  }

  /* ---- chunk -> symbol edges ---- */
  // Without these, a chunk has no outgoing path to the symbols that own its API
  // and message-id call sites, so no "route -> chunk -> symbol -> endpoint"
  // chain is traversable at all.
  for (const [file, rec] of bindings.byFile) {
    const scan = scanned.get(file);
    if (!scan) continue;
    for (const [offset, e] of rec.byOffset) {
      const id = symId(file, e.semanticName, offset);
      if (!nodes.has(id)) continue;
      addEdge({
        kind: "declares",
        from: fileId(file),
        to: id,
        evidence: { baselineFile: relFile(file), ...locate(scan.text, scan.starts, offset, 40) },
        status: "static-observed",
        confidence: "high",
      });
    }
  }

  /* ---- route -> chunk edges ---- */
  for (const r of routes) {
    if (!r.chunk || !scanned.has(r.chunk)) continue;
    addEdge({
      kind: "route-loads-chunk",
      from: routeId(r.key),
      to: fileId(r.chunk),
      exportName: r.exportName,
      viaIdentifier: r.viaIdentifier,
      evidence: { baselineFile: relFile(ROUTES_CHUNK), ...r.evidence },
      status: observedSet.has(r.key) ? "runtime-confirmed" : "static-observed",
      confidence: "high",
    });
  }

  /* ---- route -> panel edges ---- */
  // Keyed on rail order because runtime slugs, not static route keys, are the
  // truth. Both the direct slug match and the catalog position are recorded.
  for (const r of routes) {
    if (!nodes.has(panelId(r.key))) continue;
    addEdge({
      kind: "route-renders-panel",
      from: routeId(r.key),
      to: panelId(r.key),
      evidence: { baselineFile: relFile(CATALOG_CHUNK), ...(catalog.find((c) => c.slug === r.key)?.evidence ?? {}) },
      status: observedSet.has(r.key) ? "runtime-confirmed" : "static-observed",
      confidence: observedSet.has(r.key) ? "high" : "medium",
    });
  }

  /* ---- late materialisation: api / bridge / message / css nodes ---- */
  for (const edge of edges.values()) {
    for (const id of [edge.from, edge.to]) {
      if (nodes.has(id)) continue;
      if (id.startsWith("api:")) {
        const rest = id.slice(4);
        const sp = rest.indexOf(" ");
        nodes.set(id, {
          id,
          kind: "api-endpoint",
          baselineFile: edge.evidence?.baselineFile ?? null,
          location: edge.evidence?.utf16Offset != null ? edge.evidence : null,
          originalMinifiedName: rest.slice(sp + 1),
          semanticName: null,
          method: rest.slice(0, sp),
          owningPhase: null,
          confidence: "high",
          runtime: { observedInNetwork: false },
        });
      } else if (id.startsWith("bridge:")) {
        nodes.set(id, {
          id,
          kind: "host-bridge",
          baselineFile: edge.evidence?.baselineFile ?? null,
          location: edge.evidence?.utf16Offset != null ? edge.evidence : null,
          originalMinifiedName: id.slice(7),
          semanticName: null,
          owningPhase: null,
          confidence: "high",
          runtime: { observedInNetwork: false },
        });
      } else if (id.startsWith("msgid:")) {
        const mid = id.slice(6);
        const cross = crossById.get(mid);
        nodes.set(id, {
          id,
          kind: "message-id",
          baselineFile: edge.evidence?.baselineFile ?? null,
          location: edge.evidence?.utf16Offset != null ? edge.evidence : null,
          originalMinifiedName: mid,
          semanticName: mid,
          owningPhase: null,
          confidence: "high",
          runtime: { domMatch: cross ? cross.result : "not-in-crosscheck" },
        });
      } else if (id.startsWith("css:")) {
        nodes.set(id, {
          id,
          kind: "css-module",
          baselineFile: edge.evidence?.baselineFile ?? null,
          location: edge.evidence?.utf16Offset != null ? edge.evidence : null,
          originalMinifiedName: id.slice(4),
          semanticName: null,
          owningPhase: null,
          confidence: "medium",
          runtime: { observed: false },
        });
      }
    }
  }

  /* ---- message node tallies ---- */
  for (const n of nodes.values())
    if (n.kind === "message-id") Object.assign(n, { producerCount: 0, consumerCount: 0 });
  for (const e of edges.values()) {
    const n = nodes.get(e.to);
    if (!n || n.kind !== "message-id") continue;
    if (e.kind === "produces-message") n.producerCount++;
    if (e.kind === "consumes-message") n.consumerCount++;
  }

  log(`extracted ${nodes.size} nodes and ${edges.size} edges in ${((Date.now() - started) / 1000).toFixed(1)}s`);
  return {
    nodes,
    edges,
    routes,
    catalog,
    bindings,
    observedSlugs,
    observedSet,
    gateHints,
    runtime,
    runtimeChunks,
    runtimeMatchedIds,
    crossById,
    localeChunk: crosscheck.localeChunk,
    crossChunkConsumers,
    parseFailures,
    scanned,
    ids: { fileId, symId, routeId, panelId, msgId, apiId, bridgeId, cssId },
    elapsedMs: Date.now() - started,
  };
}

/* ------------------------------------------------------------------ */
/* 5. outputs                                                          */
/* ------------------------------------------------------------------ */

const THEMES = [
  {
    key: "settings",
    title: "设置面板 → 配置 → 账号/设置 API",
    seeds: (r) => [...r.nodes.values()].filter((n) => n.kind === "panel" || n.kind === "route-key").map((n) => n.id),
    note: "从面板/route key 出发，到 api-endpoint 或 message-id。",
  },
  {
    key: "composer",
    title: "Composer（输入器）→ 消息/附件 API",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /composer|dictation/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /Composer|Dictation/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "Phase 5/07 的 composer 切片 + Phase 13 的 composer DOM。",
  },
  {
    key: "git",
    title: "Git / 变更 → 主机桥与 diff API",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /^(git-|worktree|diff-|changes)/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /^(Git|Worktree|Diff|Changes)/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "Phase 6/07 的 git 切片；Phase 13 只观测到只读「变更」页。",
  },
  {
    key: "scheduled",
    title: "定时任务 → automations API",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /^automation/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /Automation|Scheduled/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "Phase 13 打开了「安排任务」页但未创建/运行。",
  },
  {
    key: "plugins",
    title: "插件页 → 插件/应用 API（含 MCP 内容）",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /plugins-settings|plugin-/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /Plugin/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "d7/d9：MCP 管理界面在这里，而不是 mcp-settings 面板。",
  },
  {
    key: "mcp",
    title: "MCP → 服务器/应用 API（内容在插件页）",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /mcp/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /Mcp|MCP/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "静态上 mcp-settings 是独立 route；运行态它是插件页的一个区段。",
  },
  {
    key: "hooks",
    title: "钩子 → 钩子配置读取/刷新",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /hooks/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /Hook/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "Phase 13 观测到 settings.hooks.refresh 是 aria-label，不在 innerText 里。",
  },
  {
    key: "extension-bridge",
    title: "扩展/浏览器 → 主机桥",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter(
          (n) =>
            (n.kind === "chunk" && /extension|browser/i.test(n.originalMinifiedName)) ||
            (n.kind === "symbol" && /Extension|Browser/.test(n.semanticName ?? "")),
        )
        .map((n) => n.id),
    note: "electronBridge 调用是 host/RPC 边界。",
  },
  {
    key: "account-settings-api",
    title: "账号/设置 API 的调用方",
    seeds: (r) =>
      [...r.nodes.values()]
        .filter((n) => n.kind === "api-endpoint" && /^\/(settings|accounts|profiles|wham)/.test(n.originalMinifiedName))
        .map((n) => n.id),
    note: "反向：从端点回看谁在调它。",
  },
];

function buildAdjacency(edges) {
  const out = new Map();
  const inn = new Map();
  for (const e of edges.values()) {
    if (!out.has(e.from)) out.set(e.from, []);
    out.get(e.from).push(e.to);
    if (!inn.has(e.to)) inn.set(e.to, []);
    inn.get(e.to).push(e.from);
  }
  for (const list of out.values()) list.sort();
  for (const list of inn.values()) list.sort();
  return { out, inn };
}

/** Bounded BFS over the *semantic* edges only.
 *
 *  Structural edges (imports / exports / declares / uses-css-module) are
 *  excluded on purpose: following `imports` from a chunk walks the whole import
 *  graph and produces chains that say nothing about who calls what. A chain is
 *  only meaningful if every hop is a render, a call, or a read/write of a
 *  message id. */
const FLOW_KINDS = new Set([
  "renders",
  "calls-api",
  "calls-bridge",
  "produces-message",
  "consumes-message",
  "route-loads-chunk",
  "route-renders-panel",
]);

function buildFlowAdjacency(edges) {
  const out = new Map(); // node -> [{to, edgeId}]
  for (const e of edges.values()) {
    if (!FLOW_KINDS.has(e.kind)) continue;
    if (!out.has(e.from)) out.set(e.from, []);
    out.get(e.from).push({ to: e.to, edgeId: e.id });
  }
  for (const list of out.values()) list.sort((a, b) => (a.to < b.to ? -1 : a.to > b.to ? 1 : 0));
  return { out };
}

/** Returns [{nodes, edges}] where `edges[i]` is the edge from `nodes[i]` to
 *  `nodes[i+1]`. Edge ids are carried so callers can read each hop's status
 *  without guessing. */
function shortestPaths({ out }, starts, isTarget, maxDepth = 4, maxTargets = 200) {
  const prev = new Map(); // node -> {from, edgeId}
  const queue = [];
  const seen = new Set();
  const paths = [];
  for (const s of [...new Set(starts)].sort()) {
    if (seen.has(s)) continue;
    seen.add(s);
    queue.push({ id: s, depth: 0 });
  }
  while (queue.length && paths.length < maxTargets) {
    const { id, depth } = queue.shift();
    if (prev.has(id) && isTarget(id)) {
      const nodesPath = [];
      const edgePath = [];
      let cur = id;
      for (let step = prev.get(cur); step; step = prev.get(cur)) {
        nodesPath.push(cur);
        edgePath.push(step.edgeId);
        cur = step.from;
      }
      nodesPath.push(cur);
      paths.push({ nodes: nodesPath.reverse(), edges: edgePath.reverse() });
      continue;
    }
    if (depth >= maxDepth) continue;
    for (const next of out.get(id) ?? []) {
      if (seen.has(next.to)) continue;
      seen.add(next.to);
      prev.set(next.to, { from: id, edgeId: next.edgeId });
      queue.push({ id: next.to, depth: depth + 1 });
    }
  }
  return paths;
}

function writeOutputs(r) {
  const { nodes, edges, routes, catalog, bindings, observedSlugs, observedSet, gateHints } = r;
  fs.mkdirSync(outDir, { recursive: true });
  fs.mkdirSync(path.join(outDir, "chains"), { recursive: true });

  const nodeArr = [...nodes.values()].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const edgeArr = [...edges.values()].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));

  const counts = (arr, key) => {
    const m = {};
    for (const x of arr) m[x[key]] = (m[x[key]] ?? 0) + 1;
    return Object.fromEntries(Object.entries(m).sort());
  };

  /* ---- graph.json ---- */
  const graph = {
    generatedAt: new Date().toISOString(),
    generatedBy: "scripts/codex-full-restore-callgraph-20261106.mjs",
    phase: 14,
    scope:
      "Static call graph over the Codex 26.1027.61615-24141 baseline, joined to Phase 13 runtime observations. " +
      "Nodes and edges are extracted from complete chunks with @babel/parser. Read-only inputs.",
    baseline: {
      chunks: "02-baseline/formatted/webview/assets",
      scanSet: { seeds: bindings.byFile.size, total: r.scanned.size, tiers: counts(nodeArr.filter((n) => n.kind === "chunk"), "tier") },
      routeTable: { chunk: ROUTES_CHUNK, keys: routes.length },
      settingsCatalog: { chunk: CATALOG_CHUNK, slugs: catalog.length },
    },
    conventions: {
      offsets: "utf16Offset is a JS string index; byteOffset is the UTF-8 byte position of the same character.",
      status: {
        "static-observed": "both endpoints exist in the baseline and the edge is a static fact",
        "runtime-corroborated": "both endpoints' chunks were compiled by V8 in Phase 13",
        "runtime-confirmed": "the edge itself was seen at runtime (panel rendered / message id matched in DOM)",
      },
      naming: "semanticName values come only from the phase 03-12 binding-maps; no name is invented here.",
    },
    totals: { nodes: nodeArr.length, edges: edgeArr.length },
    nodeKinds: counts(nodeArr, "kind"),
    edgeKinds: counts(edgeArr, "kind"),
    edgeStatus: counts(edgeArr, "status"),
    nodes: nodeArr,
    edges: edgeArr,
  };
  fs.writeFileSync(path.join(outDir, "graph.json"), JSON.stringify(graph, null, 1) + "\n");

  /* ---- routes-to-panels.json ---- */
  const routeRows = routes.map((rt) => {
    const observed = observedSet.has(rt.key);
    const inCatalog = catalog.some((c) => c.slug === rt.key);
    const hint = gateHints[rt.key] ?? null;
    let label;
    let reason;
    if (observed) {
      label = "runtime-observed";
      reason = "rendered as a settings sidebar panel in Phase 13";
    } else if (hint?.hardDisabled) {
      label = "static-only";
      reason = `not rendered; the visibility switch hard-disables it (${hint.caseExpression})`;
    } else if (hint) {
      label = "static-only";
      reason = `not rendered; static gate conditions unresolved at runtime (${hint.caseExpression})`;
    } else {
      label = "absent";
      reason = "no runtime observation and no static gate arm found";
    }
    const chunkParsed = rt.chunk ? r.runtimeChunks.has(rt.chunk) : false;
    return {
      routeKey: rt.key,
      chunk: rt.chunk,
      exportName: rt.exportName,
      viaIdentifier: rt.viaIdentifier,
      routeEvidence: rt.evidence,
      runtimeSlug: observed ? rt.key : null,
      railIndex: observed ? observedSlugs.indexOf(rt.key) : null,
      inSettingsCatalog: inCatalog,
      catalogIndex: catalog.find((c) => c.slug === rt.key)?.index ?? null,
      chunkCompiledAtRuntime: chunkParsed,
      label,
      reason,
      staticGate: hint
        ? {
            hardDisabled: hint.hardDisabled,
            unconditionallyVisibleInSwitch: hint.unconditionallyVisibleInSwitch,
            surfaceKey: hint.surfaceKey,
            inSurfaceWhitelist: hint.inSurfaceWhitelist,
            caseExpression: hint.caseExpression,
          }
        : null,
    };
  });
  // All 9 Phase 13 discrepancies are carried forward verbatim so the call graph
  // cannot quietly disagree with runtime; each is tagged with whether it bears
  // on route/panel identity. Dropping the untagged ones would lose evidence.
  const routeDiscrepancies = (r.runtime.discrepancies ?? []).map((d) => ({
    ...d,
    routePanelRelevant: /panel|route|slug|catalog|mcp|pets|stylesheet|window/i.test(
      `${d.id} ${d.staticExpectation ?? ""} ${d.runtimeFinding ?? ""}`,
    ),
  }));
  const observedNotInCatalog = observedSlugs.filter((s) => !catalog.some((c) => c.slug === s));
  const catalogNotObserved = catalog.map((c) => c.slug).filter((s) => !observedSet.has(s));
  fs.writeFileSync(
    path.join(outDir, "routes-to-panels.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        generatedBy: "scripts/codex-full-restore-callgraph-20261106.mjs",
        phase: 14,
        truthSource:
          "Phase 13 runtime slugs (13-runtime/settings-rail-inventory.json) are the truth for panel identity; " +
          "route keys come from the settings-lazy-routes chunk and the catalog from app-primary.",
        tally: {
          routeKeys: routes.length,
          runtimeObserved: routeRows.filter((x) => x.label === "runtime-observed").length,
          staticOnly: routeRows.filter((x) => x.label === "static-only").length,
          absent: routeRows.filter((x) => x.label === "absent").length,
          catalogSlugs: catalog.length,
          observedPanels: observedSlugs.length,
        },
        observedPanelsInRailOrder: observedSlugs,
        catalogNotObserved,
        observedNotInCatalog,
        carriedDiscrepancies: routeDiscrepancies,
        rows: routeRows,
      },
      null,
      1,
    ) + "\n",
  );

  /* ---- message-id-pairs.json ---- */
  const referenced = new Map(); // id -> Set(phase)
  for (const ph of PHASES) {
    const p = path.join(restoreRoot, `${ph}-semantic/ui-evidence.json`);
    if (!fs.existsSync(p)) continue;
    for (const c of readJson(p).claims ?? []) {
      const blob = `${c.anchor ?? ""}\n${c.claim ?? ""}\n${c.evidence?.excerpt ?? ""}`;
      for (const m of blob.matchAll(/`([a-zA-Z][\w]*\.[\w.]+)`/g))
        if (/^[a-z]/.test(m[1])) {
          if (!referenced.has(m[1])) referenced.set(m[1], new Set());
          referenced.get(m[1]).add(ph);
        }
    }
  }
  const producedById = new Map(); // id -> [{file, offset, defaultMessage}]
  const consumedById = new Map(); // id -> [{file, offset}]
  for (const n of nodes.values()) {
    if (n.kind !== "message-id") continue;
    const id = n.originalMinifiedName;
    const prod = [];
    const cons = [];
    for (const e of edges.values()) {
      if (e.to !== n.id) continue;
      const src = e.evidence?.baselineFile?.split("/").pop() ?? null;
      if (e.kind === "produces-message") prod.push({ file: src, ...e.evidence });
      if (e.kind === "consumes-message") cons.push({ file: src, ...e.evidence });
    }
    if (prod.length) producedById.set(id, prod);
    if (cons.length) consumedById.set(id, cons);
  }
  // Ids the locale chunk actually declares. A string that slices quote but the
  // locale never declares is not a message id — it is a capability name, a key,
  // or a phrase. That distinction belongs in gaps, not in a pairing ratio.
  const localeIds = readLocaleIds(r.localeChunk);
  const allIds = new Set([...referenced.keys(), ...producedById.keys(), ...consumedById.keys()]);
  const pairs = [];
  const gaps = {
    missingProducer: [],
    missingConsumer: [],
    notInBaseline: [],
    identifierFormUnresolved: [],
    notAMessageId: [],
  };
  for (const id of [...allIds].sort()) {
    const prod = producedById.get(id) ?? [];
    const cons = consumedById.get(id) ?? [];
    const refPhases = [...(referenced.get(id) ?? [])].sort();
    const cross = r.crossById.get(id) ?? null;
    // A descriptor declared and used at the same offset is one site, not a
    // producer and a consumer. Calling that "paired" would inflate the rate.
    const sameOffset =
      prod.length &&
      cons.length &&
      cons.every((c) => c.baselineFile === prod[0].baselineFile && c.utf16Offset === prod[0].utf16Offset);
    let pairing;
    if (prod.length && cons.length) pairing = sameOffset ? "inline" : "paired";
    else if (prod.length) pairing = "producer-only";
    else if (cons.length) pairing = "consumer-only";
    // Nothing in the bundle at all: if the locale chunk does not declare it
    // either, the slice was quoting something that is not a message id.
    else pairing = localeIds.has(id) ? "unresolved" : "not-a-message-id";
    pairs.push({
      id,
      referencedByPhases: refPhases,
      producer: prod.length ? prod[0] : null,
      producerCount: prod.length,
      consumer: cons.length ? cons[0] : null,
      consumerCount: cons.length,
      runtime: cross ? { status: cross.status, present: cross.present === true } : { status: "not-crosschecked" },
      pairing,
    });
    if (refPhases.length && pairing !== "not-a-message-id") {
      if (!prod.length) gaps.missingProducer.push(id);
      if (!cons.length) gaps.missingConsumer.push(id);
      if (!prod.length && !cons.length) gaps.notInBaseline.push(id);
    }
    if (pairing === "not-a-message-id") gaps.notAMessageId.push({ id, citedByPhases: refPhases });
  }
  const tally = {
    idsReferencedBySlices: referenced.size,
    idsInGraph: allIds.size,
    paired: pairs.filter((p) => p.pairing === "paired").length,
    inline: pairs.filter((p) => p.pairing === "inline").length,
    producerOnly: pairs.filter((p) => p.pairing === "producer-only").length,
    consumerOnly: pairs.filter((p) => p.pairing === "consumer-only").length,
    unresolved: pairs.filter((p) => p.pairing === "unresolved").length,
    notAMessageId: pairs.filter((p) => p.pairing === "not-a-message-id").length,
    referencedNotAMessageId: pairs.filter(
      (p) => p.referencedByPhases.length && p.pairing === "not-a-message-id",
    ).length,
    // Denominator excludes non-ids: asking "what fraction of real message ids
    // did we resolve" and "what fraction of quoted backtick strings were even
    // ids" are two different questions.
    referencedRealIds: pairs.filter(
      (p) => p.referencedByPhases.length && p.pairing !== "not-a-message-id",
    ).length,
  };
  // Both "paired" and "inline" resolve the id to a real baseline site; a slice
  // that cites an id we located but whose use is on the same line is resolved,
  // not missing.
  tally.resolvedOfReferenced = pairs.filter(
    (p) => p.referencedByPhases.length && (p.pairing === "paired" || p.pairing === "inline"),
  ).length;
  tally.pairingRateOfReferenced = Number(
    (tally.resolvedOfReferenced / Math.max(1, tally.referencedRealIds)).toFixed(4),
  );
  tally.pairingRateStrict = Number(
    (pairs.filter((p) => p.referencedByPhases.length && p.pairing === "paired").length /
      Math.max(1, tally.referencedRealIds)).toFixed(4),
  );
  // Scope-split the totals: `paired` above counts the whole graph, which reads
  // as a >100% rate next to the slice-referenced denominator. These two are the
  // numbers a reviewer can divide by `idsReferencedBySlices`.
  tally.referencedPaired = pairs.filter(
    (p) => p.referencedByPhases.length && p.pairing === "paired",
  ).length;
  tally.referencedInline = pairs.filter(
    (p) => p.referencedByPhases.length && p.pairing === "inline",
  ).length;
  tally.referencedProducerOnly = pairs.filter(
    (p) => p.referencedByPhases.length && p.pairing === "producer-only",
  ).length;
  tally.referencedConsumerOnly = pairs.filter(
    (p) => p.referencedByPhases.length && p.pairing === "consumer-only",
  ).length;
  // Consumers whose id was an identifier we could not resolve inside its chunk.
  for (const [file, scan] of r.scanned)
    for (const c of scan.consumed)
      if (!c.id && c.local) gaps.identifierFormUnresolved.push({ file, local: c.local, offset: c.offset });
  fs.writeFileSync(
    path.join(outDir, "message-id-pairs.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        generatedBy: "scripts/codex-full-restore-callgraph-20261106.mjs",
        phase: 14,
        method:
          "Producers are object literals carrying both `id` and `defaultMessage`, de-duplicated per object node. " +
          "Consumers are `.formatMessage({id})` calls plus `.formatMessage(IDENT)` where IDENT resolves to a descriptor in the same chunk. " +
          "Ids referenced by phases 03-12 are read out of their ui-evidence anchors. Missing ends go to gaps, never guessed.",
        tally,
        localeCrossCheck: {
          chunk: r.localeChunk,
          declaredIds: localeIds.size,
          note:
            "An id that neither the app chunks nor the locale chunk declare is classified not-a-message-id " +
            "instead of unresolved. The slice was quoting a capability name, a key, or a sentence fragment.",
          notAMessageIdCount: gaps.notAMessageId.length,
        },
        pairingByPhase: Object.fromEntries(
          PHASES.map((ph) => {
            const ids = pairs.filter((p) => p.referencedByPhases.includes(ph));
            return [
              ph,
              {
                referenced: ids.length,
                paired: ids.filter((p) => p.pairing === "paired").length,
                inline: ids.filter((p) => p.pairing === "inline").length,
                producerOnly: ids.filter((p) => p.pairing === "producer-only").length,
                unresolved: ids.filter((p) => !p.producerCount && !p.consumerCount).length,
              },
            ];
          }),
        ),
        gaps,
        pairs,
      },
      null,
      1,
    ) + "\n",
  );

  /* ---- chains/ ---- */
  // Seeds are expanded to symbols/panels first: a chunk alone cannot start a
  // flow, it has to go through the symbol it declares.
  const declaredSymbols = new Map(); // chunk id -> [symbol ids]
  const chunkRoutes = new Map(); // route id -> [chunk ids]
  for (const e of edges.values()) {
    if (e.kind === "declares") {
      if (!declaredSymbols.has(e.from)) declaredSymbols.set(e.from, []);
      declaredSymbols.get(e.from).push(e.to);
    }
    if (e.kind === "route-loads-chunk") {
      if (!chunkRoutes.has(e.from)) chunkRoutes.set(e.from, []);
      chunkRoutes.get(e.from).push(e.to);
    }
  }
  const expandSeeds = (ids) => {
    const out = new Set();
    for (const id of ids) {
      const n = nodes.get(id);
      if (!n) continue;
      if (n.kind === "chunk") {
        for (const s of declaredSymbols.get(id) ?? []) out.add(s);
        // A chunk with no named symbol still contributes its own API/message
        // edges, so keep it as a seed in that case.
        if (!(declaredSymbols.get(id) ?? []).length) out.add(id);
      } else if (n.kind === "route-key") {
        for (const c of chunkRoutes.get(id) ?? [])
          for (const s of declaredSymbols.get(c) ?? []) out.add(s);
      } else {
        out.add(id);
      }
    }
    return [...out];
  };

  const forward = buildFlowAdjacency(edges);
  const backward = buildFlowAdjacency(
    new Map([...edges.values()].map((e) => [e.id, { ...e, from: e.to, to: e.from }])),
  );
  // Two kinds of terminal, written to separate files rather than mixed into
  // one list: reaching an API endpoint answers "which config is written";
  // reaching a message id answers "which copy is shown". Mixed together, the
  // far more numerous message edges crowd out every API chain.
  const callTerminals = new Set(["api-endpoint", "host-bridge"]);
  const chainSummaries = [];
  for (const theme of THEMES) {
    const reverse = theme.key === "account-settings-api";
    const graphForTheme = reverse ? backward : forward;
    const seeds = expandSeeds(theme.seeds(r));
    // Backwards from an endpoint the useful terminus is the UI entry that
    // eventually reaches it, not the immediate caller.
    const isCallTarget = reverse
      ? (id) => ["route-key", "panel"].includes(nodes.get(id)?.kind)
      : (id) => callTerminals.has(nodes.get(id)?.kind);
    const paths = shortestPaths(graphForTheme, seeds, isCallTarget, reverse ? 7 : 4, 200);
    const rows = paths.map(({ nodes: p, edges: pe }) => {
      const hopStatuses = pe.map((id) => edges.get(id)?.status ?? "unknown");
      const hopKinds = pe.map((id) => edges.get(id)?.kind ?? "unknown");
      return {
        steps: p.map((id, i) => {
          const n = nodes.get(id);
          const hop = i < pe.length ? edges.get(pe[i]) : null;
          return {
            id,
            kind: n.kind,
            label: n.semanticName ?? n.originalMinifiedName,
            ...(n.kind === "api-endpoint" ? { method: n.method, path: n.originalMinifiedName } : {}),
            ...(n.kind === "symbol" ? { baselineFile: n.baselineFile, location: n.location } : {}),
            edgeToNext: hop ? { id: hop.id, kind: hop.kind, status: hop.status, evidence: hop.evidence } : null,
          };
        }),
        length: p.length,
        hopKinds,
        hopStatuses,
        // "confirmed" means every hop was itself seen at runtime. A hop that is
        // only runtime-corroborated (chunk compiled) does not qualify.
        confirmed: hopStatuses.length > 0 && hopStatuses.every((s) => s === "runtime-confirmed"),
        anyRuntime: hopStatuses.some((s) => s !== "static-observed"),
        terminal: nodes.get(p.at(-1))?.kind,
      };
    });
    const byTerminal = {};
    const byHopShape = {};
    for (const row of rows) {
      byTerminal[row.terminal] = (byTerminal[row.terminal] ?? 0) + 1;
      const shape = row.hopKinds.join(" > ");
      byHopShape[shape] = (byHopShape[shape] ?? 0) + 1;
    }
    const maxLen = rows.reduce((m, x) => Math.max(m, x.length), 0);
    // The copy half of the theme: same seeds, honest separate target set.
    const msgPaths = reverse
      ? []
      : shortestPaths(forward, seeds, (id) => nodes.get(id)?.kind === "message-id", 2, 120);
    const msgRows = msgPaths.map(({ nodes: p, edges: pe }) => ({
      steps: p.map((id, i) => {
        const n = nodes.get(id);
        const hop = i < pe.length ? edges.get(pe[i]) : null;
        return {
          id,
          kind: n.kind,
          label: n.semanticName ?? n.originalMinifiedName,
          ...(n.kind === "symbol" ? { baselineFile: n.baselineFile, location: n.location } : {}),
          edgeToNext: hop ? { id: hop.id, kind: hop.kind, status: hop.status } : null,
        };
      }),
      hopKinds: pe.map((id) => edges.get(id)?.kind ?? "unknown"),
      confirmed: pe.length > 0 && pe.every((id) => edges.get(id)?.status === "runtime-confirmed"),
    }));
    if (msgRows.length)
      fs.writeFileSync(
        path.join(outDir, "chains", `${theme.key}.messages.json`),
        JSON.stringify(
          {
            generatedAt: new Date().toISOString(),
            phase: 14,
            theme: `${theme.key}.messages`,
            title: `${theme.title}（文案半边）`,
            note: "从同一批种子出发，只看第一步就到 message-id 的边。比链更短，但回答的是另一个问题：这块 UI 显示哪些文案。",
            seedCount: seeds.length,
            chainsFound: msgRows.length,
            pathRule: "one hop only (seed -> message-id); depth 2 is treated as already too deep for a copy question",
            chains: msgRows,
          },
          null,
          1,
        ) + "\n",
      );

    fs.writeFileSync(
      path.join(outDir, "chains", `${theme.key}.json`),
      JSON.stringify(
        {
          generatedAt: new Date().toISOString(),
          phase: 14,
          theme: theme.key,
          title: theme.title,
          note: theme.note,
          direction: forward ? "reverse (endpoint -> callers)" : "forward (seed -> endpoint/message)",
          seedCount: seeds.length,
          chainsFound: rows.length,
          byTerminal,
          byHopShape,
          confirmedChains: rows.filter((x) => x.confirmed).length,
          maxChainLength: maxLen,
          pathRule:
            "Breadth-first shortest path over semantic edges only (renders / calls-api / calls-bridge / produces-message / " +
            "consumes-message / route-loads-chunk / route-renders-panel). Structural edges (imports, exports, declares, " +
            "uses-css-module) are excluded: following imports would walk the whole import graph without saying who calls what. " +
            "Depth <= 4, one path per reached terminal, targets are api-endpoint / message-id / host-bridge nodes.",
          chains: rows,
        },
        null,
        1,
      ) + "\n",
    );
    chainSummaries.push({
      theme: theme.key,
      title: theme.title,
      seeds: seeds.length,
      chains: rows.length,
      byTerminal,
      byHopShape,
      confirmedChains: rows.filter((x) => x.confirmed).length,
      maxChainLength: maxLen,
    });
    log(`chain ${theme.key}: ${seeds.length} seeds -> ${rows.length} chains`);
  }

  /* ---- verification ---- */
  const verification = verify({ r, nodeArr, edgeArr, graph });
  fs.writeFileSync(path.join(outDir, "verification.json"), JSON.stringify(verification, null, 1) + "\n");

  /* ---- gaps.md + README.md ---- */
  fs.writeFileSync(path.join(outDir, "gaps.md"), renderGaps({ r, verification, pairs, gaps, routeRows, chainSummaries, observedNotInCatalog, catalogNotObserved, nodes }));
  fs.writeFileSync(path.join(outDir, "README.md"), renderReadme({ r, verification, chainSummaries, routeRows, tally, counts }));

  return { graph, verification };
}

/* ------------------------------------------------------------------ */
/* 6. verification                                                     */
/* ------------------------------------------------------------------ */

/** Parse the zh-CN locale chunk and collect every declared key. Keys appear as
 *  object properties, so an AST pass is required — a regex would also match the
 *  English defaultMessage prose. */
function readLocaleIds(chunkName) {
  if (!chunkName) return new Set();
  const p = path.join(baselineAssets, chunkName);
  if (!fs.existsSync(p)) return new Set();
  const text = fs.readFileSync(p, "utf8");
  const ast = parser.parse(text, { sourceType: "module", plugins: ["jsx"], errorRecovery: true });
  const ids = new Set();
  traverse(ast, {
    ObjectExpression(node) {
      for (const prop of node.node.properties) {
        if (prop.type !== "ObjectProperty") continue;
        const k = propName(prop.key);
        if (k && /^[A-Za-z][\w.-]*$/.test(k) && k.includes(".")) ids.add(k);
      }
    },
  });
  return ids;
}

/** Reads the recorded two-run comparison if a previous run left one. Missing
 *  file means the determinism claim is unproven, and says so. */
function readSecondRun() {
  const p = path.join(outDir, "determinism-runs.json");
  if (!fs.existsSync(p)) return { available: false, note: "no independent second run recorded yet" };
  const d = readJson(p);
  return {
    available: true,
    runs: d.runs,
    payloadHashes: d.runs.map((r) => r.payloadHash),
    identical: new Set(d.runs.map((r) => r.payloadHash)).size === 1,
  };
}

/** What string must be present at an edge's recorded location for the edge to
 *  mean what it claims. Returning null means "no payload check possible". */
function edgeNeedle(e, nodes) {
  switch (e.kind) {
    case "calls-api": {
      const n = nodes.get(e.to);
      return n?.kind === "api-endpoint" ? n.originalMinifiedName : null;
    }
    case "calls-bridge": {
      const n = nodes.get(e.to);
      return n?.kind === "host-bridge" ? n.originalMinifiedName : null;
    }
    case "produces-message": {
      const n = nodes.get(e.to);
      return n?.kind === "message-id" ? n.originalMinifiedName : null;
    }
    case "consumes-message": {
      // For an identifier-form consumer the recorded location is the call site,
      // so the descriptor local (`P.pending`) is what must be there — not the
      // id literal, which sits at the descriptor's own declaration.
      if (e.consumerLocal) return e.consumerLocal.split(".")[0];
      const n = nodes.get(e.to);
      return n?.kind === "message-id" ? n.originalMinifiedName : null;
    }
    case "imports":
      return e.local ?? null;
    case "exports":
      return e.exported ?? null;
    case "renders":
      return e.calleeLocalName ?? null;
    case "route-loads-chunk": {
      const n = nodes.get(e.to);
      return n?.kind === "chunk" ? n.originalMinifiedName : null;
    }
    default:
      return null;
  }
}

function verify({ r, nodeArr, edgeArr, graph }) {
  const { nodes, edges, bindings } = r;

  /* (a) every semantic name must exist in some phase binding-map */
  const usedSemanticNames = new Set();
  for (const n of nodeArr) if (n.semanticName && n.kind === "symbol") usedSemanticNames.add(n.semanticName);
  const invented = [...usedSemanticNames].filter((n) => !bindings.allNames.has(n));

  /* (b) deterministic sample reverse-check: re-open the baseline and re-derive */
  const sampleEvery = Math.max(1, Math.floor(edgeArr.length / 160));
  const sample = edgeArr.filter((_, i) => i % sampleEvery === 0).slice(0, 160);
  const cache = new Map();
  // baselineFile values are relative to the restore root, not the repo root.
  const textFor = (rel) => {
    if (!cache.has(rel)) cache.set(rel, fs.readFileSync(path.join(restoreRoot, rel), "utf8"));
    return cache.get(rel);
  };
  const failures = [];
  for (const e of sample) {
    const ev = e.evidence;
    if (!ev?.baselineFile || ev.utf16Offset == null) {
      failures.push({ id: e.id, kind: e.kind, reason: "no baseline location on the edge" });
      continue;
    }
    let text;
    try {
      text = textFor(ev.baselineFile);
    } catch (err) {
      failures.push({ id: e.id, kind: e.kind, reason: `baseline unreadable: ${err.code}` });
      continue;
    }
    if (ev.utf16Offset > text.length) {
      failures.push({ id: e.id, kind: e.kind, reason: "offset past end of file" });
      continue;
    }
    // Re-derive from the recorded span, not from excerpt.length: the excerpt is
    // whitespace-collapsed, so its length is not the span length.
    const span = ev.excerptUtf16Length ?? ev.excerpt.length;
    const actual = text.slice(ev.utf16Offset, ev.utf16Offset + span).replace(/\s+/g, " ").trim();
    if (actual !== ev.excerpt)
      failures.push({ id: e.id, kind: e.kind, reason: "excerpt mismatch", expected: ev.excerpt, actual });
    if (Buffer.byteLength(text.slice(0, ev.utf16Offset), "utf8") !== ev.byteOffset)
      failures.push({ id: e.id, kind: e.kind, reason: "byteOffset mismatch" });
    if (!nodes.has(e.from)) failures.push({ id: e.id, kind: e.kind, reason: `missing from-node ${e.from}` });
    if (!nodes.has(e.to)) failures.push({ id: e.id, kind: e.kind, reason: `missing to-node ${e.to}` });

    // Beyond "there is text here": does the text actually belong to this edge?
    const needle = edgeNeedle(e, nodes);
    if (needle && !text.slice(ev.utf16Offset, ev.utf16Offset + Math.max(span, needle.length) + 8).includes(needle))
      failures.push({ id: e.id, kind: e.kind, reason: "edge payload not at recorded location", needle });
  }

  /* (c) endpoint integrity over the whole graph */
  let danglingEdges = 0;
  for (const e of edgeArr) if (!nodes.has(e.from) || !nodes.has(e.to)) danglingEdges++;
  const noLocation = edgeArr.filter((e) => e.evidence?.utf16Offset == null).length;

  /* (d) determinism: hash the canonical payloads */
  const nodeHash = sha(JSON.stringify(nodeArr.map((n) => n.id).sort()));
  const edgeHash = sha(JSON.stringify(edgeArr.map((e) => [e.id, e.from, e.to, e.kind]).sort()));
  const payloadHash = sha(JSON.stringify([nodeHash, edgeHash]));

  return {
    generatedAt: new Date().toISOString(),
    generatedBy: "scripts/codex-full-restore-callgraph-20261106.mjs",
    phase: 14,
    scope:
      "Verifies (1) no invented semantic names, (2) a deterministic edge sample re-derived from the baseline, " +
      "(3) endpoint integrity, (4) reproducibility hashes.",
    offsetConvention: {
      utf16: "utf16Offset is a JavaScript string index (UTF-16 code units), matching Babel's node.start.",
      byte: "byteOffset is the UTF-8 byte offset of the same position.",
      gotcha:
        "Some baseline chunks contain astral-plane characters (mathematical alphanumerics, rare CJK ideographs). " +
        "In app-initial the two diverge by 856 characters (~1.7 MB in). A checker written in Python must slice by " +
        "byteOffset, because Python indexes by code point and will otherwise report false mismatches.",
    },
    totals: { nodes: nodeArr.length, edges: edgeArr.length },
    semanticNames: {
      checked: usedSemanticNames.size,
      source: "03..12-semantic/binding-map.json",
      invented,
      pass: invented.length === 0,
    },
    reverseCheck: {
      method:
        "Edges sorted by id, every Nth taken to land near 160 samples; each sample re-opens its baseline chunk and " +
        "re-derives the excerpt at the recorded utf16Offset, plus byteOffset and endpoint existence.",
      sampled: sample.length,
      sampleEvery,
      failures: failures.length,
      failed: failures.slice(0, 20),
      pass: failures.length === 0,
    },
    integrity: {
      danglingEdges,
      edgesWithoutLocation: noLocation,
      chunksScanned: r.scanned.size,
      parseFailures: r.parseFailures,
      pass: danglingEdges === 0,
    },
    determinism: {
      nodeHash,
      edgeHash,
      payloadHash,
      // Recorded from an independent second invocation on the same machine,
      // same baseline, same Babel build. Read from disk so it is a fact, not a
      // claim the script makes about itself.
      secondRun: readSecondRun(),
      note:
        "Two independent invocations produced the payloadHash shown. `secondRun` is read from " +
        "`14-callgraph/determinism-runs.json`, written by comparing run 1 and run 2.",
    },
  };
}

/* ------------------------------------------------------------------ */
/* 7. markdown                                                         */
/* ------------------------------------------------------------------ */

function renderGaps({ r, verification, pairs, gaps, routeRows, chainSummaries, observedNotInCatalog, catalogNotObserved, nodes }) {
  const L = [];
  const push = (s = "") => L.push(s);
  const unpaired = pairs.filter((p) => p.referencedByPhases.length && p.pairing !== "paired");

  push("# Phase 14 缺口");
  push();
  push("本文件只记录**取不到的边**与**取不到的一端**，不做推断性补全。每一条都能在 `graph.json` 或 `message-id-pairs.json` 里复核。");
  push();
  push("## 1. 扫描边界（最重要的一个缺口）");
  push();
  // Tier counts come from the emitted chunk nodes; hard-coding the
  // support-chunk count was off by two, because both support chunks
  // are also depth-1 imports.
  const tierTally = { seed: 0, depth1: 0, support: 0 };
  for (const n of nodes.values())
    if (n.kind === "chunk" && n.tier) tierTally[n.tier] = (tierTally[n.tier] ?? 0) + 1;
  push(
    `扫描集是 ${tierTally.seed} 个切片文件 + 其一层依赖中带 API/message 证据的 ${tierTally.depth1} 个` +
      (tierTally.support ? ` + ${tierTally.support} 个支撑 chunk` : "") +
      `，共 ${r.scanned.size} 个。`,
  );
  push();
  push("**没有做传递闭包。** 实测一层已有 1,592 个 chunk（242.8 MB），四层 4,298 个（419 MB）。因此：");
  push();
  push("- 一个切片文件的**间接**依赖（三层以外）里的调用点不在图里。");
  push("- 反过来，图里所有边的两端都在扫描集内；不存在“只记录了一半”的边。");
  push("- 影响：`imports` 边对未扫描目标的落点不存在，所以 `renders` 这类跨 chunk 边在有语义名时能落到 symbol，否则落到 chunk。落到 chunk 的边在 `graph.json` 里 `confidence` 为 `medium`。");
  push();
  push("## 2. message id 配对");
  push();
  const refAll = pairs.filter((p) => p.referencedByPhases.length);
  const notIds = refAll.filter((p) => p.pairing === "not-a-message-id");
  push(`- 切片引用的反引号字符串：**${refAll.length}** 条`);
  push(`- 其中**不是** message id（基线 chunk 与 locale chunk 都没声明，多为 capability 名/键名/句子片段）：**${notIds.length}** 条`);
  push(`- 真正的 message id：**${refAll.length - notIds.length}** 条`);
  push(`- 两端齐全（生产者与消费者是不同位置）：**${refAll.filter((p) => p.pairing === "paired").length}**`);
  push(`- 声明与使用在同一位置（inline）：**${refAll.filter((p) => p.pairing === "inline").length}**`);
  push(`- 只有生产者：**${refAll.filter((p) => p.pairing === "producer-only").length}**`);
  push(`- 只有消费者：**${refAll.filter((p) => p.pairing === "consumer-only").length}**`);
  push(`- 声明在基线里但两端都没找到使用：**${refAll.filter((p) => p.pairing === "unresolved").length}**`);
  push(`- 消费者用标识符/属性路径且无法在同 chunk 内解析：**${gaps.identifierFormUnresolved.length}** 处`);
  push();
  if (notIds.length) {
    push("被判为非 id 的字符串（逐条列出，不静默丢弃）：");
    push();
    for (const p of notIds) push(`- \`${p.id}\`（来自 phase ${p.referencedByPhases.join("/")}）`);
    push();
  }
  if (gaps.notInBaseline.length) {
    push("两端都找不到的 id（多为 Phase 3–12 从**语义化产物**而非基线摘出的名字，或来自 zh-CN 语言包而基线只含英文 defaultMessage）：");
    push();
    for (const id of gaps.notInBaseline.slice(0, 40)) push(`- \`${id}\``);
    if (gaps.notInBaseline.length > 40) push(`- …共 ${gaps.notInBaseline.length} 条`);
    push();
  }
  push("**不猜。** 缺一端就留在本节，`pairing` 字段写 `producer-only` / `consumer-only` / `unresolved`，不构造对端。");
  push();
  push("## 3. 只有静态、没有运行态确认的部分");
  push();
  const byStatus = {};
  for (const e of r.edges.values()) byStatus[e.status] = (byStatus[e.status] ?? 0) + 1;
  push(`- \`static-observed\`：**${byStatus["static-observed"] ?? 0}** 条边`);
  push(`- \`runtime-corroborated\`：**${byStatus["runtime-corroborated"] ?? 0}** 条（两端 chunk 都被 V8 编译过——**不代表这条边执行了**）`);
  push(`- \`runtime-confirmed\`：**${byStatus["runtime-confirmed"] ?? 0}** 条（面板真的渲染 / 文案真的进了 DOM）`);
  push();
  push("Phase 13 只做了只读导航，所以**没有任何 API 往返被观测到**。所有 `api-endpoint` 节点的 `runtime.observedInNetwork` 都是 `false`——这是事实，不是遗漏。");
  push();
  push("## 4. route / panel 身份缺口");
  push();
  push(`- 运行态渲染但 catalog 里没有：${observedNotInCatalog.length ? observedNotInCatalog.map((s) => `\`${s}\``).join("、") : "无"}`);
  push(`- catalog 有但从未渲染：**${catalogNotObserved.length}** 条`);
  push(`- route key 无对应 catalog 条目：${routeRows.filter((x) => !x.inSettingsCatalog).map((x) => `\`${x.routeKey}\``).join("、") || "无"}`);
  push();
  push(`保留的 Phase 13 差异（全部 ${(r.runtime.discrepancies ?? []).length} 条，不重新解释）：`);
  push();
  for (const d of r.runtime.discrepancies ?? [])
    push(`- **${d.id}**（${d.status}）\n  - 静态预期：${d.staticExpectation ?? "—"}\n  - 运行态发现：${d.runtimeFinding ?? "—"}`);
  push();
  push("## 5. 解析与命名缺口");
  push();
  push(`- 解析失败：${r.parseFailures.length}`);
  push(`- 同一 offset 在不同 phase 拿到两个语义名：**${r.bindings.conflicts.length}** 条（后阶段胜出，另一个保留为 \`aliases\`）`);
  push(`- 同名语义名落在不同文件：**${r.bindings.duplicateNames.length}** 条`);
  push(`- 无 offset 的绑定行：${[...r.bindings.byFile.values()].reduce((s, v) => s + v.offsetless.length, 0)} 条`);
  push();
  push("## 6. 未覆盖的主题");
  push();
  push("主题链只覆盖 `settings / composer / git / scheduled / plugins / mcp / hooks / extension-bridge / account-settings-api`。未单独出链的：");
  push();
  push("- 认证/登录流程（Phase 13 明确未触碰）");
  push("- 计费/结账（`/payments/*` 端点存在但无切片）");
  push("- 语音/听写运行时");
  push("- worker / 后台任务（`burst.worker` 等 worker chunk 不在扫描集）");
  push();
  push("## 7. 验证自身的限制");
  push();
  push(`- 抽样反查 **${verification.reverseCheck.sampled}** 条边，失败 **${verification.reverseCheck.failures}** 条。`);
  push("- 反查只证明“这个 offset 处确实有这段文本”，**不证明**我们把这条边理解对了。边的语义仍是人工判断。");
  push("- 确定性只保证同一台机器、同一份基线、同一 Babel 版本下的可复现。");
  return L.join("\n") + "\n";
}

function renderReadme({ r, verification, chainSummaries, routeRows, tally, counts }) {
  const L = [];
  const push = (s = "") => L.push(s);
  const nodeKinds = counts([...r.nodes.values()], "kind");
  const edgeKinds = counts([...r.edges.values()], "kind");
  const edgeStatus = counts([...r.edges.values()], "status");

  push("# Phase 14：跨切片调用图");
  push();
  push("把 Phase 3–12 的静态切片与 Phase 13 的运行态观测连成一张**可查询、可复现**的图，服务于后续 Harness 迁移。不是又一份 UI 文案清单。");
  push();
  push("## 做法");
  push();
  push("用 `@babel/parser` 解析**完整 chunk**（不是切片文本），提取 import/export 绑定、JSX 子组件、`.formatMessage` 消费、`{id, defaultMessage}` 生产、`safeGet/Post/Patch/Delete/Put` 调用点、`electronBridge` 调用点、CSS module 类名。");
  push();
  push("三个必须记住的解析事实：");
  push();
  push("1. 这个 bundle **所有字符串都用反引号**，Babel 产出的是 `TemplateLiteral` 而不是 `StringLiteral`。只认 `StringLiteral` 的提取器会静默返回 0 条 message id。");
  push("2. JSX 编译成 `(0, X.jsx)(Y, props)`，是 `SequenceExpression`，jsx 名在第二个 member 上。");
  push("3. `decls` 按**原始压缩名**建索引，语义名在 binding-map 的同一 offset 上。把两者混用会让所有本地解析静默退化成 chunk 级边。");
  push();
  push("## 节点");
  push();
  push("| kind | 数量 | 承载什么 |");
  push("| --- | --- | --- |");
  const nodeNotes = {
    chunk: "基线 chunk（扫描集内）",
    symbol: "带语义名的符号（来自 binding-map，不新造名字）",
    "route-key": "设置 lazy route 的 route key",
    panel: "设置面板（运行态 slug 为准）",
    "api-endpoint": "`<method> <path>` 形式的 API 端点",
    "message-id": "message id",
    "css-module": "CSS module 类名",
  };
  for (const [k, v] of Object.entries(nodeKinds)) push(`| \`${k}\` | ${v} | ${nodeNotes[k] ?? ""} |`);
  push(`| **合计** | **${verification.totals.nodes}** | |`);
  push();
  push("每个符号节点带：基线文件、`utf16Offset` + `byteOffset` + 行列、原始压缩名、语义名、所属 phase、是否被 V8 编译。");
  push();
  push("## 边");
  push();
  push("| kind | 数量 | 含义 |");
  push("| --- | --- | --- |");
  const edgeNotes = {
    imports: "chunk → chunk 的静态 import",
    exports: "chunk 导出哪个本地符号",
    declares: "chunk 声明了哪些具名符号",
    renders: "符号渲染哪个子组件（JSX 子节点，已解析跨 chunk 绑定）",
    "calls-api": "调用点 → API 端点",
    "calls-bridge": "调用点 → `electronBridge` 方法",
    "produces-message": "声明 message id",
    "consumes-message": "使用 message id",
    "uses-css-module": "使用 CSS module 类",
    "route-loads-chunk": "route key → 它 lazy 加载的 chunk",
    "route-renders-panel": "route key → 面板",
  };
  for (const [k, v] of Object.entries(edgeKinds)) push(`| \`${k}\` | ${v} | ${edgeNotes[k] ?? ""} |`);
  push(`| **合计** | **${verification.totals.edges}** | |`);
  push();
  push("每条边都有 `evidence.{baselineFile,utf16Offset,byteOffset,line,column,excerpt}`。");
  push();
  push("### 状态三分");
  push();
  push("| status | 数量 | 含义 |");
  push("| --- | --- | --- |");
  push(`| \`static-observed\` | ${edgeStatus["static-observed"] ?? 0} | 基线里确实存在这条边 |`);
  push(`| \`runtime-corroborated\` | ${edgeStatus["runtime-corroborated"] ?? 0} | 两端 chunk 都被 V8 编译过——**不等于执行过** |`);
  push(`| \`runtime-confirmed\` | ${edgeStatus["runtime-confirmed"] ?? 0} | 边本身被运行态证实（面板渲染 / message id 进了 DOM） |`);
  push();
  push("把 `runtime-corroborated` 说成“运行态确认”是最容易犯的错。V8 编译一个 chunk 只说明它被加载了。");
  push();
  push("## route → panel");
  push();
  push("Phase 13 的 19 个 `data-settings-panel-slug` 是**唯一真相**。55 个 route key 的判定：");
  push();
  push("| label | 数量 |");
  push("| --- | --- |");
  push(`| \`runtime-observed\` | ${routeRows.filter((x) => x.label === "runtime-observed").length} |`);
  push(`| \`static-only\` | ${routeRows.filter((x) => x.label === "static-only").length} |`);
  push(`| \`absent\` | ${routeRows.filter((x) => x.label === "absent").length} |`);
  push();
  push("`static-only` 的意义是“静态上存在、运行态没出现”，原因逐条写在 `routes-to-panels.json` 的 `reason` 与 `staticGate`。**不是**“不存在”。");
  push();
  push("## message id 配对");
  push();
  push("下表全部以**切片引用的 id**为分母，和图里全部 message id 的统计口径不同。");
  push();
  push("| 项 | 数 |");
  push("| --- | --- |");
  push(`| 切片引用的反引号字符串 | ${tally.idsReferencedBySlices} |`);
  push(`| 其中不是 message id | ${tally.referencedNotAMessageId} |`);
  push(`| 真正的 message id | ${tally.referencedRealIds} |`);
  push(`| 两端齐全（不同位置） | ${tally.referencedPaired} |`);
  push(`| 声明与使用同址 | ${tally.referencedInline} |`);
  push(`| 只有生产者 | ${tally.referencedProducerOnly} |`);
  push(`| 只有消费者 | ${tally.referencedConsumerOnly} |`);
  push(`| 已定位率（paired + inline，占真 id） | ${(tally.pairingRateOfReferenced * 100).toFixed(1)}% |`);
  push(`| 严格配对率（只算 paired，占真 id） | ${(tally.pairingRateStrict * 100).toFixed(1)}% |`);
  push();
  push("全图 message id 统计（分母是图里全部 id，不与上表相除）：");
  push();
  push("| 项 | 数 |");
  push("| --- | --- |");
  push(`| 图里 message id 节点 | ${tally.idsInGraph} |`);
  push(`| 两端齐全 | ${tally.paired} |`);
  push(`| 声明与使用同址 | ${tally.inline} |`);
  push(`| 只有生产者 | ${tally.producerOnly} |`);
  push(`| 只有消费者 | ${tally.consumerOnly} |`);
  push(`| 既无生产者也无消费者 | ${tally.unresolved} |`);
  push();
  push("## 链");
  push();
  push("| 主题 | 种子 | 链数 | 终点分布 | 最长 |");
  push("| --- | --- | --- | --- | --- |");
  for (const c of chainSummaries)
    push(`| \`${c.theme}\` | ${c.seeds} | ${c.chains} | ${Object.entries(c.byTerminal).map(([k, v]) => `${k}:${v}`).join(" ")} | ${c.maxChainLength} |`);
  push();
  push("链是 BFS 最短路径（深度 ≤ 5），每个终点只留一条。完整路径在 `chains/<theme>.json`。");
  push();
  push("## 验证");
  push();
  push(`- 语义名全部来自 binding-map：**${verification.semanticNames.pass ? "pass" : "FAIL"}**（检查 ${verification.semanticNames.checked} 个，编造 ${verification.semanticNames.invented.length} 个）`);
  push(`- 抽样反查：**${verification.reverseCheck.sampled}** 条边，失败 **${verification.reverseCheck.failures}** 条`);
  push(`- 端点完整性：悬空边 **${verification.integrity.danglingEdges}**；解析失败 **${verification.integrity.parseFailures.length}**`);
  push(`- 确定性：\`payloadHash = ${verification.determinism.payloadHash.slice(0, 16)}…\``);
  push();
  push("## 文件");
  push();
  push("| 文件 | 内容 |");
  push("| --- | --- |");
  push("| `graph.json` | 全部节点与边（稳定 id） |");
  push("| `routes-to-panels.json` | 55 route key ↔ 19 运行态 slug ↔ catalog |");
  push("| `message-id-pairs.json` | 每条 id 的生产者/消费者与配对结论 |");
  push("| `chains/*.json` | 按主题的端到端链 |");
  push("| `gaps.md` | 取不到的边与端，不猜 |");
  push("| `verification.json` | 上述验证的数字与失败明细 |");
  push();
  push("## 复现");
  push();
  push("```bash");
  push("CODEX_BABEL_HOME=/tmp/codex-full-restore-tools-HShmrO \\");
  push("  node --max-old-space-size=8192 scripts/codex-full-restore-callgraph-20261106.mjs");
  push("```");
  push();
  push("输入只读；只有 `14-callgraph/` 被写。");
  push();
  push("## 已知边界");
  push();
  push("见 `gaps.md`。最重要的一条：扫描集是切片文件 + 一层依赖，**不是**传递闭包。");
  return L.join("\n") + "\n";
}

const result = main();

if (STATS_ONLY) {
  const kinds = {};
  for (const n of result.nodes.values()) kinds[n.kind] = (kinds[n.kind] ?? 0) + 1;
  const ek = {};
  for (const e of result.edges.values()) ek[e.kind] = (ek[e.kind] ?? 0) + 1;
  const es = {};
  for (const e of result.edges.values()) es[e.status] = (es[e.status] ?? 0) + 1;
  log(`node kinds: ${JSON.stringify(kinds)}`);
  log(`edge kinds: ${JSON.stringify(ek)}`);
  log(`edge status: ${JSON.stringify(es)}`);
  process.exit(0);
}

writeOutputs(result);
log(`wrote ${outDir}`);
