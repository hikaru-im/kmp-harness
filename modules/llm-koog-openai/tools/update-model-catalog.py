#!/usr/bin/env python3
"""Generate the bundled OpenAI catalog from a pinned models.dev checkout."""
import argparse, json, subprocess, tempfile, tomllib
from pathlib import Path

REVISION = "26703fa74cc2a990d4a095d5dce6d79dd2202524"
REPOSITORY = "https://github.com/anomalyco/models.dev"
OUTPUT = Path(__file__).parents[1] / "resources/model-catalog/openai.json"

def merge(base, override):
    result = dict(base)
    for key, value in override.items():
        result[key] = merge(result.get(key, {}), value) if isinstance(value, dict) and isinstance(result.get(key), dict) else value
    return result

def load(path):
    with path.open("rb") as stream:
        return tomllib.load(stream)

def generate(root):
    models = []
    for provider_path in sorted((root / "providers/openai/models").glob("*.toml")):
        provider = load(provider_path)
        base_ref = provider.pop("base_model", None)
        base = load(root / "models" / (base_ref + ".toml")) if base_ref else {}
        item = merge(base, provider)
        status = str(item.get("status", "")).lower()
        inputs = item.get("modalities", {}).get("input", [])
        outputs = item.get("modalities", {}).get("output", [])
        if status == "deprecated" or item.get("family") == "text-embedding" or "text" not in inputs or "text" not in outputs:
            continue
        limits = item.get("limit", {})
        context = limits.get("context")
        output = limits.get("output")
        if not isinstance(context, int) or context < 1 or not isinstance(output, int) or output < 1:
            continue
        entry = {
            "id": provider_path.stem,
            "name": item.get("name") or provider_path.stem,
            "description": item.get("description") or item.get("name") or provider_path.stem,
            "contextWindow": context,
            "maxOutputTokens": output,
            "inputModalities": [value for value in ("text", "image") if value in inputs],
            "toolCall": bool(item.get("tool_call", False)),
            "structuredOutput": bool(item.get("structured_output", False)),
            "temperature": bool(item.get("temperature", False)),
            "reasoning": bool(item.get("reasoning", False)),
        }
        efforts = []
        for option in provider.get("reasoning_options", []):
            if option.get("type") == "effort" and all(isinstance(value, str) and value for value in option.get("values", [])):
                efforts = option["values"]
        if efforts:
            entry["reasoningEfforts"] = efforts
        models.append(entry)
    return {"schemaVersion": 1, "source": {"repository": REPOSITORY, "revision": REVISION, "license": "MIT", "notice": "Generated from models.dev; see repository license."}, "models": models}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, help="models.dev checkout at the pinned revision")
    args = parser.parse_args()
    if args.source:
        root = args.source.resolve()
        actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
        if actual != REVISION: raise SystemExit(f"expected models.dev revision {REVISION}, got {actual}")
        data = generate(root)
    else:
        with tempfile.TemporaryDirectory(prefix="models-dev-") as temp:
            root = Path(temp) / "models.dev"
            subprocess.run(["git", "clone", "--quiet", REPOSITORY, str(root)], check=True)
            subprocess.run(["git", "-C", str(root), "checkout", "--quiet", REVISION], check=True)
            data = generate(root)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")

if __name__ == "__main__": main()
