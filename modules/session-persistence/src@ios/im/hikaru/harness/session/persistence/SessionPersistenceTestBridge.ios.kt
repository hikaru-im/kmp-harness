package im.hikaru.harness.session.persistence

import im.hikaru.harness.runtime.plugin.SimplePlugin

actual fun sessionPersistencePluginForTest(databasePath: String): SimplePlugin =
    error("iOS persistence tests must provide a launcher-owned database factory")
