import argparse,subprocess
p=argparse.ArgumentParser()
p.add_argument('--attempt',required=True)
p.add_argument('--run-label',required=True)
a=p.parse_args()
cases=[('desktop','im.hikaru.harness.bundle.desktop.DesktopUserPluginsTest',21),('jvm-app','im.hikaru.harness.desktop.plugins.DesktopInstalledPluginRepositoryTest',11),('jvm-app','im.hikaru.harness.desktop.PluginLocalUpdateInteractionTest',10),('jvm-app','im.hikaru.harness.desktop.PluginManagementInteractionTest',3),('shared','im.hikaru.harness.client.plugins.PluginManagementControllerTest',9),('shared','im.hikaru.harness.client.plugins.InstalledPluginsControllerTest',11)]
for m,c,n in cases:
 subprocess.run(['python3','/tmp/harness-plugin-local-update-parent-verify-20261008.py',m,c,str(n),'--attempt',a.attempt,'--run-label',a.run_label],check=True)
