#!/usr/bin/env python3
"""Build a first-pass behavior inventory from extracted Codex webview bundles."""

from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path


STRING_RE = re.compile(r"'(?:\\.|[^'\\])*'|\"(?:\\.|[^\"\\])*\"|\x60(?:\\.|[^\x60\\])*\x60")
ACTION_RE = re.compile(
    r"(?P<kind>onClick|onSelect|onOpen|onClose|onChange|onSubmit|onKeyDown|navigate|router\.(?:push|replace)|toggle[A-Z][A-Za-z0-9_]*|ipcRenderer\.(?:invoke|send)|window\.electron|fetch|axios|webSocket|WebSocket|useQuery|useMutation)"
)
ROUTE_RE = re.compile(r"^/(?:[A-Za-z0-9._:-]+)(?:/[A-Za-z0-9._:@?=&%+~-]+)*$")
ENDPOINT_RE = re.compile(
    r"^(?:https?://|wss?://|/api(?:/|$)|/v\d+(?:/|$)|/ws(?:/|$)|/graphql(?:/|$)|ipc:[^ ]+)"
)
STATE_RE = re.compile(
    r"(?:loading|error|empty|unavailable|pending|starting|working|done|failed|success|disconnected|offline|requires-login|no-host|streaming|expanded|collapsed)",
    re.IGNORECASE,
)
ROLE_PREFIXES = {
    "sidebar": "app rail and workspace navigator",
    "projects-index": "projects index",
    "local-conversation": "conversation and thread surface",
    "thread-app-shell": "thread shell chrome and panel outlets",
    "thread-right-panel": "thread right panel state",
    "settings": "settings routing and settings pages",
    "task-summary": "task summary and run state",
    "codex-light": "light theme tokens",
    "codex-dark": "dark theme tokens",
    "app-shared": "shared UI runtime",
    "composer-controls": "composer controls",
    "thread-scroll-layout": "thread scroll and content geometry",
}


def bundle_role(name: str) -> str:
    for prefix, role in ROLE_PREFIXES.items():
        if name.startswith(prefix):
            return role
    return "selected webview asset"


def decode_string(value: str) -> str:
    return value.replace("\\\\'", "'").replace('\\\\"', '"').replace("\\\\n", " ")


def strings_in(source: str):
    for match in STRING_RE.finditer(source):
        value = decode_string(match.group(0)[1:-1])
        if value:
            yield value


def snippets(source: str, limit: int = 24):
    result = []
    seen = set()
    for match in ACTION_RE.finditer(source):
        start = max(0, match.start() - 220)
        end = min(len(source), match.end() + 320)
        snippet = re.sub(r"\s+", " ", source[start:end]).strip()
        kind = match.group("kind")
        key = (kind, snippet)
        if key in seen:
            continue
        seen.add(key)
        result.append({"kind": kind, "offset": match.start(), "snippet": snippet})
        if len(result) >= limit:
            break
    return result


def analyze_bundle(path: Path, root: Path):
    source = path.read_text(encoding="utf-8", errors="replace")
    values = list(strings_in(source))
    routes = sorted({value for value in values if ROUTE_RE.match(value)})
    endpoints = sorted({value for value in values if ENDPOINT_RE.match(value)})
    message_ids = sorted(
        {
            value
            for value in values
            if any(marker in value for marker in ("sidebar.", "settings.", "codex.", "chatgpt.", "appShell.", "status."))
        }
    )
    states = sorted({match.group(0).lower() for value in values for match in STATE_RE.finditer(value)})
    return {
        "file": path.relative_to(root).as_posix(),
        "name": path.name,
        "bytes": path.stat().st_size,
        "role": bundle_role(path.name),
        "routes": routes,
        "endpoints": endpoints,
        "message_ids": message_ids[:160],
        "states": states,
        "actions": snippets(source),
    }


def flatten(bundles, key: str, value_name: str):
    rows = []
    for bundle in bundles:
        for value in bundle[key]:
            rows.append({"bundle": bundle["file"], value_name: value})
    return rows


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_report(path: Path, manifest, bundles):
    route_bundles = [bundle for bundle in bundles if bundle["routes"]]
    action_counts = defaultdict(int)
    for bundle in bundles:
        for action in bundle["actions"]:
            action_counts[action["kind"]] += 1
    endpoint_bundles = defaultdict(set)
    state_bundles = defaultdict(set)
    for bundle in bundles:
        for endpoint in bundle["endpoints"]:
            endpoint_bundles[endpoint].add(bundle["name"])
        for state in bundle["states"]:
            state_bundles[state].add(bundle["name"])

    lines = [
        "# Codex webview behavior inventory",
        "",
        "Static first pass over selected JavaScript and CSS bundles from the local app.asar.",
        "Action snippets are evidence locations; they are not a complete control-flow reconstruction.",
        "",
        "## Archive",
        "",
        f"- Archive: {manifest.get('archive', '')}",
        f"- Bytes: {manifest.get('bytes', '')}",
        f"- SHA-256: {manifest.get('sha256', '')}",
        f"- Selected bundles: {len(bundles)}",
        "",
        "## Routes",
        "",
    ]
    for bundle in route_bundles:
        lines += [f"### {bundle['name']}", ""]
        lines += [f"- {route}" for route in bundle["routes"][:80]]
        lines.append("")
    lines += ["## Action evidence", "", "| Kind | Count |", "| --- | ---: |"]
    lines += [f"| {kind} | {count} |" for kind, count in sorted(action_counts.items(), key=lambda item: (-item[1], item[0]))]
    lines += ["", "Full snippets are in actions.json; each record includes the bundle and byte offset.", "", "## Data source evidence", "", "| Endpoint or channel | Bundles |", "| --- | --- |"]
    lines += [f"| {endpoint} | {', '.join(sorted(names))} |" for endpoint, names in sorted(endpoint_bundles.items())]
    lines += ["", "## State vocabulary", "", "| State | Bundles |", "| --- | --- |"]
    lines += [f"| {state} | {', '.join(sorted(names))} |" for state, names in sorted(state_bundles.items())]
    lines += [
        "",
        "## Interpretation",
        "",
        "- Routes and message IDs are strong evidence of page and state vocabulary.",
        "- Action snippets show where the bundled UI wires user events; minification means imported module names and nearby code must be read together.",
        "- Endpoint strings are incomplete when URLs are assembled dynamically or hidden behind a request helper.",
        "- The next pass should resolve each action to a normalized UiAction and each data source to a repository method.",
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path, help="directory containing extracted app.asar files")
    parser.add_argument("output", type=Path, help="directory for JSON and Markdown results")
    args = parser.parse_args()
    manifest_path = args.input / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    assets = [path for path in sorted(args.input.rglob("*")) if path.is_file() and path.suffix in {".js", ".css"}]
    bundles = [analyze_bundle(path, args.input) for path in assets]
    write_json(args.output / "manifest.json", manifest)
    write_json(args.output / "bundles.json", bundles)
    write_json(args.output / "routes.json", flatten(bundles, "routes", "route"))
    write_json(args.output / "actions.json", flatten(bundles, "actions", "action"))
    write_json(args.output / "data-sources.json", flatten(bundles, "endpoints", "endpoint"))
    write_json(args.output / "states.json", flatten(bundles, "states", "state"))
    write_report(args.output / "report.md", manifest, bundles)
    print(json.dumps({"bundles": len(bundles), "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
