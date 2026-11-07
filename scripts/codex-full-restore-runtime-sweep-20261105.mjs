#!/usr/bin/env node
/**
 * Phase 13 read-only sweeper for the settings surface.
 *
 * Drives the isolated (own --user-data-dir) Codex instance over CDP using only:
 *   - Input.dispatchMouseEvent at the centre of a settings-sidebar button,
 *   - Runtime.evaluate with pure DOM-query expressions,
 *   - Page.captureScreenshot,
 *   - Log/Runtime console collection.
 *
 * It never types into a form control, never presses a save/delete/run/send
 * button, never navigates the SPA away from settings, and never issues a
 * network request of its own. Every page it records is recorded as it is,
 * with any e-mail / token-shaped text redacted before it is written to disk.
 *
 * Usage: node scripts/codex-full-restore-runtime-sweep-20261105.mjs <plan.json> <outDir>
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const PORT = process.env.CDP_PORT ?? "9333";
const [, , planPath, outDir] = process.argv;
if (!planPath || !outDir) {
  console.error("usage: <plan.json> <outDir>");
  process.exit(2);
}

const plan = JSON.parse(fs.readFileSync(planPath, "utf8"));
for (const d of ["screenshots", "dom", "console"]) {
  fs.mkdirSync(path.join(outDir, d), { recursive: true });
}

class Session {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.events = [];
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
      if (msg.method) this.events.push(msg);
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
  const result = await session.send("Runtime.evaluate", {
    expression,
    returnByValue: true,
    awaitPromise: true,
    userGesture: false,
  });
  if (result.exceptionDetails) {
    return { ok: false, error: result.exceptionDetails.exception?.description ?? result.exceptionDetails.text };
  }
  return { ok: true, value: result.result?.value };
}

async function clickAt(session, x, y) {
  const base = { x, y, button: "left", clickCount: 1, buttons: 1 };
  await session.send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y, button: "none", buttons: 0 });
  await session.send("Input.dispatchMouseEvent", { type: "mousePressed", ...base });
  await session.send("Input.dispatchMouseEvent", { type: "mouseReleased", ...base, buttons: 0 });
}

/** Escape dismisses a transient overlay. It writes nothing. */
async function pressEscape(session) {
  const key = { key: "Escape", code: "Escape", windowsVirtualKeyCode: 27, nativeVirtualKeyCode: 27 };
  await session.send("Input.dispatchKeyEvent", { type: "keyDown", ...key });
  await session.send("Input.dispatchKeyEvent", { type: "keyUp", ...key });
  await sleep(400);
}

/** Coordinates of a settings-sidebar button whose label matches exactly. */
function locateExpr(label) {
  return `(() => {
    const nav = document.querySelectorAll("nav")[1];
    if (!nav) return null;
    const hit = [...nav.querySelectorAll("button")].find(b => (b.textContent || "").trim() === ${JSON.stringify(label)});
    if (!hit) return null;
    const r = hit.getBoundingClientRect();
    if (!r.width || !r.height) return null;
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  })()`;
}

/**
 * Full observation of the settings content area. Deliberately returns no form
 * values: only the shape of the page (labels, headings, control kinds, data-*
 * attributes, message-ish copy already rendered as visible text).
 */
const SNAPSHOT_EXPR = `(() => {
  const nav = document.querySelectorAll("nav")[1];
  const buttons = nav ? [...nav.querySelectorAll("button")] : [];
  const active = buttons.find(b => b.getAttribute("aria-current") === "page");
  const isVisible = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const visibleNav = buttons.filter(isVisible).map(b => (b.textContent || "").trim());
  const hiddenNav = buttons.filter(b => !isVisible(b)).map(b => (b.textContent || "").trim());
  const main = document.querySelector("main") || document.body;
  const dataAttrs = [...main.attributes].filter(a => a.name.startsWith("data-")).map(a => a.name + "=" + String(a.value).slice(0, 60));
  const dataAttrsTree = [...main.querySelectorAll("*")].slice(0, 4000)
    .flatMap(el => [...el.attributes].filter(a => a.name.startsWith("data-")).map(a => ({
      tag: el.tagName.toLowerCase(),
      name: a.name,
      value: String(a.value).slice(0, 60),
    })))
    .filter((v, i, arr) => arr.findIndex(o => o.name === v.name && o.value === v.value) === i)
    .slice(0, 60);
  const headings = [...main.querySelectorAll("h1,h2,h3")].filter(isVisible).map(h => ({
    tag: h.tagName.toLowerCase(),
    text: (h.textContent || "").trim().slice(0, 60),
  })).slice(0, 30);
  const controls = [...main.querySelectorAll("input,select,textarea,button,[role=switch],[role=radio],[role=tab]")]
    .filter(isVisible).slice(0, 120)
    .map(el => ({
      tag: el.tagName.toLowerCase(),
      type: el.getAttribute("type") || null,
      role: el.getAttribute("role") || null,
      label: (el.getAttribute("aria-label") || "").slice(0, 60) || null,
      text: (el.textContent || "").trim().slice(0, 40) || null,
      checked: el.getAttribute("aria-checked") ?? (el.type === "checkbox" ? String(el.checked) : null),
      disabled: el.disabled === true || el.getAttribute("aria-disabled") === "true",
    }));
  const text = (main.innerText || "").replace(/\\s+/g, " ").trim();
  return {
    href: location.href,
    title: document.title,
    textFull: text.slice(0, 40000),
    activeLabel: active ? (active.textContent || "").trim() : null,
    // The live panel's own identity. This is the runtime name for the route,
    // and it is what the evidence must be keyed on — not a route key guessed
    // from the static chunk table.
    activeSlug: active ? active.getAttribute("data-settings-panel-slug") : null,
    visibleNav,
    hiddenNav,
    dataAttrs,
    dataAttrsTree,
    headings,
    controls,
    textLen: text.length,
    textHead: text.slice(0, 600),
  };
})()`;

/**
 * Observation of an app-shell surface (home, scheduled, plugins, explore,
 * projects, a conversation). Captures the same shape as the settings snapshot
 * plus the composer and the side-panel inventory, and no form values.
 */
const SHELL_SNAPSHOT_EXPR = `(() => {
  const isVisible = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const rails = [...document.querySelectorAll("nav")].map(n => ({
    ariaLabel: n.getAttribute("aria-label"),
    cls: (n.className || "").toString().split(" ").slice(0, 3).join(" "),
    buttons: [...n.querySelectorAll("button")].filter(isVisible).map(b => ({
      text: (b.textContent || "").replace(/\\s+/g, " ").trim().slice(0, 40),
      ariaLabel: (b.getAttribute("aria-label") || "").slice(0, 40) || null,
      ariaCurrent: b.getAttribute("aria-current"),
    })).slice(0, 40),
  }));
  const main = document.querySelector("main") || document.body;
  const dataAttrsTree = [document.body, ...main.querySelectorAll("*")].slice(0, 4000)
    .flatMap(el => [...el.attributes].filter(a => a.name.startsWith("data-")).map(a => ({
      tag: el.tagName.toLowerCase(),
      name: a.name,
      value: String(a.value).slice(0, 60),
    })))
    .filter((v, i, arr) => arr.findIndex(o => o.name === v.name && o.value === v.value) === i)
    .slice(0, 80);
  const headings = [...document.querySelectorAll("main h1,main h2,main h3")].filter(isVisible)
    .map(h => ({ tag: h.tagName.toLowerCase(), text: (h.textContent || "").trim().slice(0, 60) })).slice(0, 30);
  const editor = document.querySelector('[contenteditable="true"], textarea, [role="textbox"]');
  const composer = editor ? {
    tag: editor.tagName.toLowerCase(),
    role: editor.getAttribute("role") || null,
    dataAttrs: [...editor.attributes].filter(a => a.name.startsWith("data-")).map(a => a.name + "=" + String(a.value).slice(0, 40)),
    placeholder: editor.getAttribute("data-placeholder") || editor.getAttribute("placeholder") || null,
    empty: (editor.innerText || editor.value || "").trim().length === 0,
    rect: (() => { const r = editor.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }; })(),
  } : null;
  const composerButtons = editor ? [...document.querySelectorAll("button")].filter(b => {
    if (!isVisible(b)) return false;
    const r = b.getBoundingClientRect();
    const c = editor.getBoundingClientRect();
    return r.y > c.y - 60 && r.y < c.y + c.height + 60;
  }).map(b => ({
    ariaLabel: (b.getAttribute("aria-label") || "").slice(0, 40) || null,
    text: (b.textContent || "").replace(/\\s+/g, " ").trim().slice(0, 24) || null,
    disabled: b.disabled === true || b.getAttribute("aria-disabled") === "true",
  })).slice(0, 20) : [];
  const text = (main.innerText || "").replace(/\\s+/g, " ").trim();
  return {
    href: location.href,
    hash: location.hash,
    title: document.title,
    textFull: text.slice(0, 40000),
    rails,
    dataAttrsTree,
    headings,
    composer,
    composerButtons,
    textLen: text.length,
    textHead: text.slice(0, 700),
  };
})()`;

const MASK = [
  [/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, "<redacted-email>"],
  [/\b(sk|pk|rk)-[A-Za-z0-9_-]{12,}/g, "<redacted-key>"],
  [/\bey[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{6,}/g, "<redacted-jwt>"],
  [/\b[0-9a-f]{32,}\b/gi, "<redacted-hex>"],
];
function redact(s) {
  if (typeof s !== "string") return s;
  let out = s;
  for (const [re, rep] of MASK) out = out.replace(re, rep);
  return out;
}
function redactDeep(v) {
  if (typeof v === "string") return redact(v);
  if (Array.isArray(v)) return v.map(redactDeep);
  if (v && typeof v === "object") {
    const o = {};
    for (const [k, val] of Object.entries(v)) o[k] = redactDeep(val);
    return o;
  }
  return v;
}

/** Coordinates of the first visible element matching a CSS selector. */
function locateSelectorExpr(selector) {
  return `(() => {
    const el = [...document.querySelectorAll(${JSON.stringify(selector)})]
      .find(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; });
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2), tag: el.tagName.toLowerCase() };
  })()`;
}

/**
 * Coordinates of a visible button whose trimmed text (or aria-label) matches,
 * searched inside a scope selector. Used for the app-shell rail, whose nav
 * index shifts between surfaces.
 */
function locateTextExpr(scopeSelector, text) {
  return `(() => {
    const scope = document.querySelector(${JSON.stringify(scopeSelector)});
    if (!scope) return null;
    const hit = [...scope.querySelectorAll("button,[role=button],a")].find(b => {
      const t = (b.textContent || "").trim();
      const al = b.getAttribute("aria-label") || "";
      if (t !== ${JSON.stringify(text)} && al !== ${JSON.stringify(text)}) return false;
      const r = b.getBoundingClientRect();
      return r.width > 0 && r.height > 0;
    });
    if (!hit) return null;
    const r = hit.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  })()`;
}

/**
 * Coordinates of the smallest visible element whose own text equals `text`.
 * Conversation rows are spans inside a scroll viewport, not buttons, so the
 * button-only locator above cannot reach them.
 */
function locateLeafTextExpr(text) {
  return `(() => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const hits = [...document.querySelectorAll("span,p,button,a,div")]
      .filter(e => vis(e) && (e.textContent || "").trim() === ${JSON.stringify(text)} && e.children.length === 0);
    const el = hits[0];
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2), tag: el.tagName.toLowerCase() };
  })()`;
}

const res = await fetch(`http://127.0.0.1:${PORT}/json`);
const targetList = await res.json();
/**
 * Pick the main application window, not the Spotlight window. Both are
 * `type: "page"` on app://-/index.html, and the spotlight one carries an
 * `initialRoute=/spotlight` query; picking `.at(-1)` would read the command
 * palette instead of the app.
 */
const pages = targetList.filter((t) => t.type === "page");
const page = pages.find((t) => !/initialRoute=.*spotlight/.test(t.url)) ?? pages.at(-1);
if (!page) throw new Error("no page target");
const session = await new Session(page.webSocketDebuggerUrl).open();
await session.send("Runtime.enable");
await session.send("Log.enable");
await session.send("Page.enable");
/**
 * Debugger.enable does not run code; it makes V8 report `scriptParsed` for
 * every script already loaded in this realm (and for every chunk a later
 * navigation pulls in). That gives the runtime's real asset set, which is the
 * only reliable way to cross-check a Phase 12 slice against what actually
 * executes — `performance.getEntriesByType("resource")` is empty under the
 * app:// scheme.
 */
await session.send("Debugger.enable");
await sleep(800);

const scriptUrl = (raw) => {
  if (!raw) return null;
  const m = /\/assets\/([^/?#]+\.js)$/.exec(raw);
  return m ? m[1] : null;
};
/**
 * Accumulated set of chunks V8 has parsed. `session.events` is cleared before
 * each step to scope console output, so the script list is drained into this
 * set first — the runtime's asset set is cumulative and must not be lost.
 */
const allScripts = new Set();
function drainScripts() {
  for (const e of session.events) {
    if (e.method !== "Debugger.scriptParsed") continue;
    const name = scriptUrl(e.params?.url);
    if (name) allScripts.add(name);
  }
}
drainScripts();

const results = [];
let previousScriptCount = allScripts.size;
for (const step of plan.steps) {
  const startedAt = Date.now();
  if (step.dismiss) await pressEscape(session);
  const located = step.x != null && step.y != null
    ? { ok: true, value: { x: step.x, y: step.y } }
    : step.selector
      ? await evaluate(session, locateSelectorExpr(step.selector))
      : step.leafText
        ? await evaluate(session, locateLeafTextExpr(step.leafText))
        : step.text
          ? await evaluate(session, locateTextExpr(step.scope ?? "body", step.text))
          : await evaluate(session, locateExpr(step.label));
  const entry = { slug: step.slug, label: step.label, routeKey: step.routeKey ?? null, status: null };
  if (!located.ok || !located.value) {
    entry.status = "blocked";
    entry.reason = "settings-sidebar entry not present in this build/account";
    results.push(entry);
    console.log(JSON.stringify({ slug: step.slug, status: entry.status }));
    continue;
  }
  drainScripts();
  session.events.length = 0;
  await clickAt(session, located.value.x, located.value.y);
  await sleep(step.settle ?? 900);
  // A transient palette (command menu / spotlight) can be left over from the
  // click; Escape only dismisses overlays and writes nothing.
  if (step.escapeAfter) await pressEscape(session);
  const snap = await evaluate(session, step.shell ? SHELL_SNAPSHOT_EXPR : SNAPSHOT_EXPR);
  if (!snap.ok) {
    entry.status = "error";
    entry.error = snap.error;
    results.push(entry);
    console.log(JSON.stringify({ slug: step.slug, status: "error" }));
    continue;
  }
  entry.status = "observed";
  entry.click = located.value;
  entry.observed = redactDeep(snap.value);
  entry.observeMs = Date.now() - startedAt;

  const shot = await session.send("Page.captureScreenshot", { format: "png" });
  const shotPath = path.join(outDir, "screenshots", `${step.slug}.png`);
  fs.writeFileSync(shotPath, Buffer.from(shot.data, "base64"));
  entry.screenshot = { file: `screenshots/${step.slug}.png`, bytes: fs.statSync(shotPath).size };

  const html = await evaluate(
    session,
    `(() => { const m = document.querySelector("main") || document.body; return m.outerHTML; })()`,
  );
  if (html.ok) {
    const domPath = path.join(outDir, "dom", `${step.slug}.html`);
    fs.writeFileSync(domPath, redact(html.value));
    entry.dom = { file: `dom/${step.slug}.html`, bytes: fs.statSync(domPath).size };
  }

  const consoleEntries = session.events
    .filter((e) => ["Runtime.consoleAPICalled", "Log.entryAdded", "Runtime.exceptionThrown"].includes(e.method))
    .map((e) => ({
      method: e.method,
      level: e.params?.type ?? e.params?.entry?.level ?? e.params?.exceptionDetails?.text ?? null,
      text: redact(
        String(
          e.params?.entry?.text ??
            e.params?.exceptionDetails?.exception?.description ??
            (e.params?.args || []).map((a) => a.value ?? a.description ?? a.type).join(" "),
        ),
      ).slice(0, 400),
    }));
  const consolePath = path.join(outDir, "console", `${step.slug}.json`);
  fs.writeFileSync(consolePath, JSON.stringify(consoleEntries, null, 2));
  entry.console = {
    file: `console/${step.slug}.json`,
    count: consoleEntries.length,
    errors: consoleEntries.filter((c) => c.level === "error" || c.method === "Runtime.exceptionThrown").length,
  };

  drainScripts();
  entry.chunks = { loadedSoFar: allScripts.size, newInThisStep: allScripts.size - previousScriptCount };
  previousScriptCount = allScripts.size;

  results.push(entry);
  console.log(JSON.stringify({
    slug: step.slug,
    status: entry.status,
    active: entry.observed.activeLabel ?? entry.observed.title,
    headings: entry.observed.headings.map((h) => h.text).slice(0, 3),
    composer: entry.observed.composer ? "yes" : "no",
    newChunks: entry.chunks.newInThisStep,
    chunks: entry.chunks.loadedSoFar,
    consoleErrors: entry.console.errors,
  }));
}

session.close();
/**
 * Merge into any existing sweep record so several small plans (settings
 * routes, shell surfaces, a conversation) build one complete file instead of
 * each run overwriting the last.
 */
const rawPath = path.join(outDir, "sweep-raw.json");
let merged = results;
if (fs.existsSync(rawPath)) {
  try {
    const previous = JSON.parse(fs.readFileSync(rawPath, "utf8"));
    const bySlug = new Map(previous.map((r) => [r.slug, r]));
    for (const r of results) bySlug.set(r.slug, r);
    merged = [...bySlug.values()];
  } catch {
    /* unreadable previous record: keep this run's results only */
  }
}
fs.writeFileSync(rawPath, JSON.stringify(merged, null, 2));
const chunksPath = path.join(outDir, "runtime-chunks.json");
const chunkSet = new Set(allScripts);
if (fs.existsSync(chunksPath)) {
  try {
    const previous = JSON.parse(fs.readFileSync(chunksPath, "utf8"));
    for (const c of previous.chunks ?? []) chunkSet.add(c);
  } catch {
    /* unreadable previous record: keep this run's set only */
  }
}
fs.writeFileSync(
  chunksPath,
  JSON.stringify(
    { capturedAt: new Date().toISOString(), windowUrl: page.url, count: chunkSet.size, chunks: [...chunkSet].sort() },
    null,
    2,
  ),
);
console.log(JSON.stringify({ steps: results.length, observed: results.filter((r) => r.status === "observed").length, blocked: results.filter((r) => r.status === "blocked").length, chunks: allScripts.size, mergedTotal: merged.length }));
