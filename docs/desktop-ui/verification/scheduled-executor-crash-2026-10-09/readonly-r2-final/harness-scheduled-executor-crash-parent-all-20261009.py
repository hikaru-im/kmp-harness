from pathlib import Path
import argparse
import subprocess
import sys

p = argparse.ArgumentParser()
p.add_argument('--attempt', required=True)
p.add_argument('--run-label', required=True)
a = p.parse_args()
verify = Path(__file__).with_name('harness-scheduled-executor-crash-parent-verify-20261009.py')
classes = [('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorCrashProcessTest', 5), ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorIntegrationTest', 7), ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledLeaderLeaseProcessTest', 2), ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduleEngineTest', 3), ('shared', 'im.hikaru.harness.client.scheduling.ScheduledExecutorOwnerTest', 13), ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledStoreTest', 15), ('jvm-app', 'im.hikaru.harness.desktop.scheduling.DesktopScheduledSessionEvidenceTest', 3)]
for module, class_name, count in classes:
    result = subprocess.run([sys.executable, str(verify), module, class_name, str(count),
                             '--attempt', a.attempt, '--run-label', a.run_label])
    if result.returncode != 0:
        raise SystemExit(result.returncode)
print('SERIAL SUITES PASSED', sum(row[2] for row in classes), flush=True)
