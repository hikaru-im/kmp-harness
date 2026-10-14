#!/usr/bin/env node
/**
 * Read-only Chrome DevTools Protocol probe for the running Codex Desktop.
 *
 * The reference app is an Electron build; it is inspected over the remote debugging port
 * (9222) instead of the Browser pane, which can only attach to plain HTTP servers. This keeps
 * every layout/DOM probe a one-liner instead of a hand-written WebSocket script.
 *
 * Usage:
 *   node scripts/cdp.mjs '<javascript expression>'      # evaluate in the app's main page, print JSON
 *   node scripts/cdp.mjs --file probe.js                # evaluate the file's contents
 *   node scripts/cdp.mjs --move X,Y                    # hover at a point before evaluating
 *   node scripts/cdp.mjs --click X,Y                   # click a point before evaluating
 *   node scripts/cdp.mjs --drag X1,Y1,X2,Y2[,STEPS]     # press-move-release (e.g. a split divider)
 *   node scripts/cdp.mjs --shot /tmp/x.png             # save a full-page screenshot
 *   node scripts/cdp.mjs --target <id|title-substr> ... # pick another page target (default: main window)
 *   node scripts/cdp.mjs --list                         # list page targets
 *
 * The expression is evaluated with `returnByValue`, so it must produce a JSON-serialisable value.
 * Input flags only fire when you pass them, so the default call stays read-only.
 */
import http from 'node:http';
import fs from 'node:fs';

const PORT = Number(process.env.CDP_PORT ?? 9222);

function listTargets() {
  return new Promise((resolve, reject) => {
    http
      .get(`http://127.0.0.1:${PORT}/json`, (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => resolve(JSON.parse(body)));
      })
      .on('error', reject);
  });
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    let seq = 0;
    const pending = new Map();
    ws.addEventListener('open', () =>
      resolve({
        send(method, params) {
          return new Promise((res) => {
            const id = ++seq;
            pending.set(id, res);
            ws.send(JSON.stringify({ id, method, params }));
          });
        },
        close: () => ws.close(),
      }),
    );
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data);
      const done = pending.get(msg.id);
      if (done) {
        pending.delete(msg.id);
        done(msg);
      }
    });
    ws.addEventListener('error', reject);
  });
}

const argv = process.argv.slice(2);
function takeFlag(name) {
  const i = argv.indexOf(name);
  if (i < 0) return null;
  const value = argv[i + 1] ?? null;
  argv.splice(i, value === null ? 1 : 2);
  return value ?? true;
}

const wantsList = takeFlag('--list');
const targetArg = takeFlag('--target');
const fileArg = takeFlag('--file');
const moveArg = takeFlag('--move');
const dragArg = takeFlag('--drag');
const clickArg = takeFlag('--click');
const shotArg = takeFlag('--shot');
const expression = fileArg ? fs.readFileSync(fileArg, 'utf8') : argv.join(' ');

const targets = (await listTargets()).filter((t) => t.type === 'page');
if (wantsList) {
  console.log(JSON.stringify(targets.map((t) => ({ id: t.id, title: t.title, url: t.url })), null, 2));
  process.exit(0);
}

const point = (s) => {
  const [x, y] = String(s).split(',').map(Number);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    console.error('point must be "X,Y": ' + s);
    process.exit(1);
  }
  return { x, y };
};

const target =
  (targetArg && targets.find((t) => t.id === targetArg || t.title.includes(targetArg))) ||
  targets.find((t) => t.url.startsWith('app://-/index.html')) ||
  targets[0];
if (!target) {
  console.error('no page target on port ' + PORT);
  process.exit(1);
}
if (!expression && !shotArg) {
  console.error('nothing to do; pass an expression, --file, or --shot');
  process.exit(1);
}

const client = await connect(target.webSocketDebuggerUrl);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
if (moveArg) {
  const p = point(moveArg);
  await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: p.x, y: p.y });
  await sleep(400);
}
if (dragArg) {
  // --drag X1,Y1,X2,Y2[,STEPS]: press at (X1,Y1), move to (X2,Y2) in steps, release.
  const [x1, y1, x2, y2, stepsRaw] = String(dragArg).split(',').map(Number);
  const steps = Number.isFinite(stepsRaw) && stepsRaw > 0 ? stepsRaw : 12;
  await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: x1, y: y1 });
  await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: x1, y: y1, button: 'left', clickCount: 1 });
  for (let k = 1; k <= steps; k++) {
    const x = Math.round(x1 + ((x2 - x1) * k) / steps);
    const y = Math.round(y1 + ((y2 - y1) * k) / steps);
    await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, button: 'left', buttons: 1 });
    await sleep(30);
  }
  await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: x2, y: y2, button: 'left', clickCount: 1 });
  await sleep(500);
}
if (clickArg) {
  const p = point(clickArg);
  await client.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: p.x, y: p.y });
  await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: p.x, y: p.y, button: 'left', clickCount: 1 });
  await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: p.x, y: p.y, button: 'left', clickCount: 1 });
  await sleep(1000);
}
if (shotArg) {
  const shot = await client.send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(shotArg, Buffer.from(shot.result.data, 'base64'));
  console.error('screenshot -> ' + shotArg);
}
let reply = { result: {} };
if (expression) {
  reply = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
}
client.close();
const result = reply.result?.result;
if (reply.result?.exceptionDetails) {
  console.error(JSON.stringify(reply.result.exceptionDetails, null, 2));
  process.exit(2);
}
console.log(typeof result?.value === 'string' ? result.value : JSON.stringify(result?.value, null, 2));
