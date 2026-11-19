#!/usr/bin/env node
/**
 * Minimal read-only CDP driver for the Phase 13 runtime evidence pass.
 *
 * No third-party client is installed, so this speaks the DevTools protocol
 * directly over Node's built-in WebSocket. Every command it issues is a read:
 * Runtime.evaluate with a pure DOM-query expression, Page.captureScreenshot,
 * Log/Runtime console capture and target listing. It never dispatches input
 * events, never navigates, and never clicks anything.
 *
 * Usage:
 *   node scripts/codex-full-restore-runtime-cdp-20261105.mjs <command> [args]
 *
 *   list                       list page targets
 *   eval <targetId> <expr>     evaluate an expression in that target
 *   shot <targetId> <out.png>  capture a screenshot
 *   console <targetId> <ms>    attach and collect console/log entries for ms
 */
import fs from "node:fs";
import process from "node:process";

const PORT = process.env.CDP_PORT ?? "9333";

async function targets() {
  const res = await fetch(`http://127.0.0.1:${PORT}/json`);
  if (!res.ok) throw new Error(`GET /json -> ${res.status}`);
  return res.json();
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

async function pageSession(targetId) {
  const all = await targets();
  const target = targetId
    ? all.find((t) => t.id === targetId || t.id.startsWith(targetId))
    : all.filter((t) => t.type === "page").at(-1);
  if (!target) throw new Error(`no target for ${targetId ?? "(last page)"}`);
  const session = await new Session(target.webSocketDebuggerUrl).open();
  return { session, target };
}

/**
 * Expression wrapper. The evaluated body is always a read: it queries the DOM
 * and returns plain JSON. `awaitPromise` is on so a page that returns a
 * promise still resolves deterministically.
 */
async function evaluate(session, expression) {
  const result = await session.send("Runtime.evaluate", {
    expression,
    returnByValue: true,
    awaitPromise: true,
    userGesture: false,
  });
  if (result.exceptionDetails) {
    return {
      ok: false,
      error:
        result.exceptionDetails.exception?.description ??
        result.exceptionDetails.text,
    };
  }
  return { ok: true, value: result.result?.value };
}


/**
 * Dispatch a single trusted left-click at viewport coordinates.
 *
 * Used only to open navigation surfaces (the profile/settings menu and the
 * settings rail) so their DOM can be read. It never targets a form control,
 * a save/delete/run/send button, or anything that writes.
 */
async function clickAt(session, x, y) {
  const base = { x, y, button: "left", clickCount: 1, buttons: 1 };
  await session.send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y, button: "none", buttons: 0 });
  await session.send("Input.dispatchMouseEvent", { type: "mousePressed", ...base });
  await session.send("Input.dispatchMouseEvent", { type: "mouseReleased", ...base, buttons: 0 });
}

/**
 * Send a single non-destructive key. Only ever used for Escape, which
 * dismisses whatever transient overlay is open and writes nothing.
 */
async function pressEscape(session) {
  const key = { key: "Escape", code: "Escape", windowsVirtualKeyCode: 27, nativeVirtualKeyCode: 27 };
  await session.send("Input.dispatchKeyEvent", { type: "keyDown", ...key });
  await session.send("Input.dispatchKeyEvent", { type: "keyUp", ...key });
}

const command = process.argv[2];

if (command === "list") {
  const all = await targets();
  console.log(
    JSON.stringify(
      all.map((t) => ({
        id: t.id,
        type: t.type,
        title: t.title,
        url: t.url,
        attached: t.attached,
      })),
      null,
      2,
    ),
  );
} else if (command === "eval") {
  const [, , , targetId, expression] = process.argv;
  const { session } = await pageSession(targetId);
  const out = await evaluate(session, expression);
  session.close();
  console.log(JSON.stringify(out, null, 2));
} else if (command === "click") {
  const [, , , targetId, xRaw, yRaw] = process.argv;
  const { session } = await pageSession(targetId);
  await clickAt(session, Number(xRaw), Number(yRaw));
  await new Promise((r) => setTimeout(r, 600));
  session.close();
  console.log(JSON.stringify({ clicked: [Number(xRaw), Number(yRaw)] }));
} else if (command === "key") {
  const [, , , targetId, keyName] = process.argv;
  if (keyName !== "Escape") {
    console.error("only Escape is supported");
    process.exit(2);
  }
  const { session } = await pageSession(targetId);
  await pressEscape(session);
  await new Promise((r) => setTimeout(r, 500));
  session.close();
  console.log(JSON.stringify({ pressed: "Escape" }));
} else if (command === "shot") {
  const [, , , targetId, outPath] = process.argv;
  const { session } = await pageSession(targetId);
  const shot = await session.send("Page.captureScreenshot", { format: "png" });
  fs.writeFileSync(outPath, Buffer.from(shot.data, "base64"));
  session.close();
  console.log(JSON.stringify({ out: outPath, bytes: fs.statSync(outPath).size }));
} else if (command === "console") {
  const [, , , targetId, msRaw] = process.argv;
  const ms = Number(msRaw ?? 3000);
  const { session } = await pageSession(targetId);
  await session.send("Runtime.enable");
  await session.send("Log.enable");
  await new Promise((resolve) => setTimeout(resolve, ms));
  const entries = session.events
    .filter((e) =>
      ["Runtime.consoleAPICalled", "Log.entryAdded", "Runtime.exceptionThrown"].includes(e.method),
    )
    .map((e) => ({ method: e.method, params: e.params }));
  session.close();
  console.log(JSON.stringify(entries, null, 2));
} else {
  console.error("usage: list | eval <t> <expr> | click <t> <x> <y> | shot <t> <out> | console <t> [ms]");
  process.exit(2);
}
