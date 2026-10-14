from pathlib import Path
import argparse
import json
import re
import subprocess
import sys

def suites(source):
    fixed = [
        ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorCrashProcessTest', 5),
        ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorIntegrationTest', 7),
        ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledLeaderLeaseProcessTest', 2),
        ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduleEngineTest', 3),
        ('shared', 'im.hikaru.harness.client.scheduling.ScheduledExecutorOwnerTest', 13),
        ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledStoreTest', 15),
        ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledSessionEvidenceTest', 3),
        ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceContractTest', 7),
        ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceHeaderCatalogTest', 10),
        ('session-persistence', 'im.hikaru.harness.session.persistence.SessionPersistenceDatabaseFactoryTest', 1),
    ]
    added = []
    for module, folder, class_name in (
        ('session-persistence', 'modules/session-persistence/test/im/hikaru/harness/session/persistence', 'SessionPersistenceReadOnlyEventsTest'),
        ('jvm-app', 'apps/jvm-app/test/im/hikaru/harness/desktop/scheduling', 'DesktopScheduledSessionEvidenceReadOnlyTest'),
    ):
        text = (source / folder / (class_name + '.kt')).read_text()
        names = re.findall(r'@Test\s+fun\s+(\w+)\s*\(', text)
        assert names and len(set(names)) == len(names), class_name
        assert len(names) == len(re.findall(r'@Test\b', text)), 'Uncounted Test annotation'
        package = re.search(r'^package\s+(\S+)', text, re.MULTILINE).group(1)
        added.append((module, package + '.' + class_name, len(names)))
    return fixed[:1] + added + fixed[1:]

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--attempt', required=True)
    p.add_argument('--run-label', required=True)
    a = p.parse_args()
    source = Path('/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-' + a.attempt)
    classes = suites(source)
    root = Path('/tmp/harness-pages-verified-20261009/scheduled-executor-crash') / a.run_label
    root.mkdir(parents=True, exist_ok=False)
    (root / 'suite-plan.json').write_text(json.dumps(classes, indent=2) + '\n')
    verify = Path(__file__).with_name('harness-scheduled-executor-crash-parent-verify-20261009.py')
    for module, class_name, count in classes:
        result = subprocess.run([sys.executable, str(verify), module, class_name, str(count), '--attempt', a.attempt, '--run-label', a.run_label])
        if result.returncode != 0:
            raise SystemExit(result.returncode)
    print('SERIAL SUITES PASSED', sum(row[2] for row in classes), flush=True)
