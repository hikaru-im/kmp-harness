import subprocess
import sys

for module, class_name, count in [
    ("jvm-app", "im.hikaru.harness.desktop.PluginLocalUpdateInteractionTest", 10),
    ("jvm-app", "im.hikaru.harness.desktop.PluginManagementInteractionTest", 3),
    ("shared", "im.hikaru.harness.client.plugins.PluginManagementControllerTest", 9),
    ("shared", "im.hikaru.harness.client.plugins.InstalledPluginsControllerTest", 11),
]:
    result = subprocess.run([sys.executable, "/tmp/harness-plugin-local-update-parent-verify-20261008.py",
                             module, class_name, str(count), "--attempt", "initial", "--run-label", "r1"])
    if result.returncode:
        raise SystemExit(result.returncode)
