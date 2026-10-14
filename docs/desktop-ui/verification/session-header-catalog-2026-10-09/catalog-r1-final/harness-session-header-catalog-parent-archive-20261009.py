from pathlib import Path
import argparse
import hashlib
import json
import shutil

p = argparse.ArgumentParser()
p.add_argument("--attempt", required=True)
p.add_argument("--run-label", required=True)
a = p.parse_args()
repo = Path("/Volumes/Data/IdeaProjects/kmp-harness")
source = Path("/tmp/harness-session-header-catalog-parent-candidate-20261009-" + a.attempt)
manifest_path = Path(str(source) + "-manifest.json")
manifest = json.loads(manifest_path.read_text())
for name, digest in manifest["inputs"].items():
    assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, name
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, "Workspace drift " + name
results = Path("/tmp/harness-pages-verified-20261009/session-header-catalog") / a.run_label
expected = {"SessionPersistenceHeaderCatalogTest": 10, "SessionPersistenceContractTest": 7, "SessionPersistenceDatabaseFactoryTest": 1}
summaries = sorted(results.glob("*/summary.json"))
assert {s.parent.name for s in summaries} == set(expected), "Missing class"
totals = dict(tests=0, failures=0, errors=0, skipped=0)
classes = []
for summary in summaries:
    data = json.loads(summary.read_text())
    assert data["exit"] == 0 and data["totals"] == dict(tests=expected[summary.parent.name], failures=0, errors=0, skipped=0), summary
    assert (summary.parent / "source-manifest.json").read_bytes() == manifest_path.read_bytes(), summary
    classes.append(dict(name=data["class"], totals=data["totals"]))
    for key in totals:
        totals[key] += data["totals"][key]
assert totals == dict(tests=18, failures=0, errors=0, skipped=0)
out = repo / "docs/desktop-ui/verification/session-header-catalog-2026-10-09" / (a.run_label + "-final")
assert not out.exists(), "Archive exists"
shutil.copytree(results, out)
for name in ("harness-session-header-catalog-delivery-20261009.json", "harness-session-header-catalog-delivery-20261009.md", "harness-session-header-catalog-parent-freeze-20261009.py", "harness-session-header-catalog-parent-verify-20261009.py", "harness-session-header-catalog-parent-archive-20261009.py"):
    shutil.copy2(Path("/tmp") / name, out / name)
shutil.copy2(manifest_path, out / "source-manifest.json")
(out / "final-summary.json").write_text(json.dumps(dict(status="passed", totals=totals, classes=classes, frozenInputs=len(manifest["inputs"]), boundary="Real temporary Room database, JVM; durable headers only, not full Host inventory, path ownership or deletion safety"), indent=2) + chr(10))
print(json.dumps(dict(totals=totals, frozenInputs=len(manifest["inputs"]), evidence=str(out))))
