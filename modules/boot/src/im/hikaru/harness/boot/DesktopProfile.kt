package im.hikaru.harness.boot

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog

/** Complete, already composed Host profile used by the Boot layer. */
public data class DesktopProfile(
    val hostDescription: HostDescription,
    val catalog: PluginCatalog = PluginCatalog.Empty,
    val entries: List<Entry> = emptyList(),
)
