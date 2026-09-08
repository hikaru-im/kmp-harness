package im.hikaru.harness.session.persistence

import im.hikaru.harness.runtime.plugin.SimplePlugin

actual fun sessionPersistencePluginForTest(databasePath: String): SimplePlugin =
    error("Android persistence tests must provide a Context-owned database factory")
