from pathlib import Path
import argparse
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--attempt', required=True)
parser.add_argument('--run-label', required=True)
parser.add_argument('--observer-count', required=True, type=int)
parser.add_argument('--ui-count', required=True, type=int)
args = parser.parse_args()
assert args.observer_count > 0 and args.ui_count > 0
verify = Path(__file__).with_name('harness-worktree-host-observation-parent-verify-20261009.py')
classes = [
    ('jvm-app', 'im.hikaru.harness.desktop.WorktreeCreationInteractionTest', 2),
    ('jvm-app', 'im.hikaru.harness.desktop.WorktreeSessionObservationInteractionTest', args.ui_count),
    ('jvm-app', 'im.hikaru.harness.desktop.git.DesktopWorktreeSessionObserverTest', args.observer_count),
    ('jvm-app', 'im.hikaru.harness.desktop.git.DesktopManagedWorktreeIntegrationTest', 8),
    ('shared', 'im.hikaru.harness.client.git.ManagedWorktreeControllerTest', 4),
    ('jvm-app', 'im.hikaru.harness.desktop.ManagedWorktreeInteractionTest', 3),
    ('jvm-app', 'im.hikaru.harness.desktop.GitWorktreesScreenshotTest', 6),
]
for module, class_name, count in classes:
    command = [sys.executable, str(verify), module, class_name, str(count),
               '--attempt', args.attempt, '--run-label', args.run_label]
    result = subprocess.run(command)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
print('SERIAL SUITES PASSED', sum(count for _, _, count in classes), flush=True)
