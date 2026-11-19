// CSS inspection only: parse style text without creating a browser or loading assets.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { pathToFileURL } from "node:url";
const [prettierModule, root] = process.argv.slice(2);
const prettier = (await import(pathToFileURL(prettierModule))).default;
const manifest = JSON.parse(fs.readFileSync(path.join(root, "selection.json")));
const hash = (x) => crypto.createHash("sha256").update(x).digest("hex");
const targetProps = new Set([
  "--spacing",
  "--height-toolbar",
  "--sidebar-footer-height",
  "--app-shell-sidebar-top-inset",
  "--sidebar-scroll-header-fade-distance",
  "--sidebar-scroll-header-fade-start",
  "--sidebar-scroll-header-spacing",
  "--padding-row-x",
]);
const selectorTerms = [
  ".h-toolbar",
  "--sidebar-footer-height",
  "--app-shell-sidebar-top-inset",
  ".gap-2",
  ".gap-4",
  ".select-none",
  "fallbackDragRegion_57y6d_2",
  "Navigation_1uxfp_2",
  "ConversationSidebar_1uxfp_2",
];
function flatten(ast, source) {
  const rows = [];
  function walk(node, parents = []) {
    const context =
      node.type === "css-rule"
        ? { type: node.type, selector: node.raws?.selector ?? null }
        : node.type === "css-atrule"
          ? {
              type: node.type,
              name: node.name,
              params: node.raws?.params ?? "",
            }
          : null;
    const next = context ? [...parents, context] : parents;
    if (node.type === "css-decl") {
      const offset = node.source.start.offset,
        prefix = source.slice(0, offset),
        lines = prefix.split("\n");
      rows.push({
        property: node.prop,
        value: node.value.text,
        context: parents,
        utf16_offset: offset,
        byte_offset: Buffer.byteLength(prefix),
        line: lines.length,
        column: lines.at(-1).length,
        source: source.slice(offset, node.source.end.offset),
      });
    }
    for (const child of node.nodes ?? []) walk(child, next);
  }
  walk(ast);
  return rows;
}
// Compare a declaration's parsed value tokens, not its formatting whitespace.
function canonical(node) {
  if (Array.isArray(node)) return node.map(canonical);
  if (node && typeof node === "object")
    return Object.fromEntries(
      Object.keys(node)
        .sort()
        .filter(
          (k) =>
            ![
              "source",
              "sourceIndex",
              "raws",
              "text",
              "startOffset",
              "endOffset",
              "__contentStart",
              "__contentEnd",
            ].includes(k),
        )
        .map((k) => [
          k,
          node.type === "value-number" &&
          k === "value" &&
          typeof node[k] === "string"
            ? node[k].replace(
                /^([+-]?)\.(\d+)$/,
                (_, sign, digits) => sign + "0." + digits,
              )
            : canonical(node[k]),
        ]),
    );
  return node;
}
const results = [];
for (const item of manifest.files.filter((x) =>
  x.original_path.endsWith(".css"),
)) {
  const source = fs.readFileSync(
    path.join(root, "original", item.original_path),
    "utf8",
  );
  const formatted = fs.readFileSync(
    path.join(root, "formatted", item.readable_path),
    "utf8",
  );
  if (hash(source) !== item.original_sha256)
    throw Error("Original CSS hash mismatch");
  const a = (await prettier.__debug.parse(source, { parser: "css" })).ast;
  const b = (await prettier.__debug.parse(formatted, { parser: "css" })).ast;
  const rows = flatten(a, source),
    formattedRows = flatten(b, formatted);
  const originalShape = JSON.stringify(canonical(a)),
    formattedShape = JSON.stringify(canonical(b));
  const equal = originalShape === formattedShape;
  function firstDifference(a, b, p = "") {
    if (a === b) return null;
    if (!a || !b || typeof a !== "object" || typeof b !== "object")
      return { path: p, original: a, formatted: b };
    for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
      const d = firstDifference(a[k], b[k], p + "/" + k);
      if (d) return d;
    }
    return null;
  }
  const difference = equal ? null : firstDifference(canonical(a), canonical(b));
  const selected = rows.filter(
    (row) =>
      targetProps.has(row.property) ||
      row.context.some((c) =>
        selectorTerms.some((t) => c.selector?.includes(t)),
      ),
  );
  results.push({
    ...item,
    css_parse_passed: true,
    canonical_css_asts_equal: equal,
    first_difference: difference,
    canonicalization_excludes_selector_raw_spelling: true,
    declarations: rows.length,
    formatted_declarations: formattedRows.length,
    evidence: selected,
  });
  console.log(
    JSON.stringify({
      file: item.readable_path,
      canonical_css_asts_equal: equal,
      declarations: rows.length,
      evidence: selected.length,
    }),
  );
}
fs.writeFileSync(
  path.join(root, "css-evidence.json"),
  JSON.stringify(
    {
      parser: "Prettier 3.9.10 internal CSS parser",
      comparison_limit:
        "Canonical structural comparison omits raw selector spelling; original selectors, values and contexts are recorded separately. No browser cascade or computed style verification.",
      runtime_confirmed: false,
      files: results,
    },
    null,
    2,
  ) + "\n",
);
