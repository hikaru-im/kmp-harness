#!/usr/bin/env node
/**
 * Phase 13 — per-route stylesheet evidence.
 *
 * Walks the settings sidebar read-only and, for each panel, records which
 * stylesheets the running app has actually applied (`document.styleSheets`
 * hrefs) and which CSS-module class hashes appear in the rendered DOM.
 *
 * The point is to answer a question the static pass cannot: of the 28 route
 * stylesheets Phase 12 copied, which ones does the real app load for the
 * route they belong to, and which load for a different route than the static
 * route table implies.
 *
 * Usage: node scripts/codex-full-restore-runtime-css-20261105.mjs <outDir>
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const PORT = process.env.CDP_PORT ?? "9333";
const outDir = process.argv[2];
if (!outDir) {
  console.error("usage: <outDir>");
  process.exit(2);
}

class Session {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
  }
  async open() {
    this.ws = new WebSocket(this.wsUrl);
    await new Promise((resolve, reject) => {
      this.ws.addEventListener("open", resolve, { once: true });
      this.ws.addEventListener("error", reject, { once: true });
    });
    this.ws.addEventListener("message", (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id != null && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        if (msg.error) reject(new Error(JSON.stringify(msg.error)));
        else resolve(msg.result);
        return;
      }
    });
    return this;
  }
  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
  close() {
    try {
      this.ws.close();
    } catch {
      /* ignore */
    }
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function evaluate(session, expression) {
  const result = await session.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text);
  return result.result?.value;
}

async function clickAt(session, x, y) {
  const base = { x, y, button: "left", clickCount: 1, buttons: 1 };
  await session.send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y, button: "none", buttons: 0 });
  await session.send("Input.dispatchMouseEvent", { type: "mousePressed", ...base });
  await session.send("Input.dispatchMouseEvent", { type: "mouseReleased", ...base, buttons: 0 });
}

const all = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
const pages = all.filter((t) => t.type === "page");
const page = pages.find((t) => !/initialRoute=.*spotlight/.test(t.url)) ?? pages.at(-1);
const session = await new Session(page.webSocketDebuggerUrl).open();

const panels = await evaluate(
  session,
  `(() => {
    const nav = document.querySelectorAll("nav")[1];
    if (!nav) return [];
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    return [...nav.querySelectorAll("button")].filter(vis).map(b => {
      const r = b.getBoundingClientRect();
      return {
        slug: b.getAttribute("data-settings-panel-slug"),
        label: (b.textContent || "").trim(),
        x: Math.round(r.x + r.width / 2),
        y: Math.round(r.y + r.height / 2),
      };
    }).filter(p => p.slug);
  })()`,
);
if (!panels.length) throw new Error("settings sidebar is not open in this window");

const sheetExpr = `(() => {
  const hrefs = [...document.styleSheets].map(s => s.href).filter(Boolean)
    .map(h => (h.split("/assets/")[1] || h));
  const html = document.querySelector("main")?.outerHTML ?? "";
  const hashes = [...new Set([...html.matchAll(/_([A-Za-z0-9]+)_([a-z0-9]{5})_([0-9]+)/g)].map(m => m[2]))];
  return { hrefs: [...new Set(hrefs)].sort(), hashes };
})()`;

const results = [];
let previous = new Set();
for (const panel of panels) {
  await clickAt(session, panel.x, panel.y);
  await sleep(1200);
  const state = await evaluate(session, sheetExpr);
  const current = new Set(state.hrefs);
  results.push({
    slug: panel.slug,
    label: panel.label,
    stylesheetCount: state.hrefs.length,
    hrefs: state.hrefs,
    added: [...current].filter((h) => !previous.has(h)).sort(),
    removed: [...previous].filter((h) => !current.has(h)).sort(),
    cssModuleHashes: state.hashes.sort(),
  });
  previous = current;
  console.log(JSON.stringify({ slug: panel.slug, sheets: state.hrefs.length, added: results.at(-1).added.length, removed: results.at(-1).removed.length }));
}

session.close();
fs.writeFileSync(
  path.join(outDir, "route-stylesheets.json"),
  JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      generatedBy: "scripts/codex-full-restore-runtime-css-20261105.mjs",
      note: "Stylesheet deltas are measured after the panel renders; the app keeps previously applied route stylesheets loaded and normally only adds, so `added` is the meaningful column.",
      panels: results,
    },
    null,
    2,
  ),
);
console.log(JSON.stringify({ panels: results.length }));
