package im.hikaru.harness.session.persistence

import androidx.room3.withWriteTransaction
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import im.hikaru.harness.runtime.plugin.SimplePlugin
import java.io.File
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.Dispatchers

/** JVM launcher-owned factory. The caller chooses the database location. */
@OptIn(ExperimentalAtomicApi::class)
fun jvmSessionPersistenceDatabaseFactory(databaseFile: File): SessionPersistenceDatabaseFactory {
    databaseFile.parentFile?.mkdirs()
    val key = databaseFile.canonicalFile.absolutePath
    return object : SessionPersistenceDatabaseFactory {
        override fun create(): SessionDatabaseAccess =
            jvmSessionDatabaseRegistry.acquire(key, this) {
                jvmDatabaseAccess(
                    key = key,
                    database =
                        Room.databaseBuilder<SessionPersistenceDatabase>(key)
                            .addMigrations(*SessionPersistenceMigrations.all)
                            .setDriver(BundledSQLiteDriver())
                            .setQueryCoroutineContext(Dispatchers.IO)
                            .build(),
                    registry = jvmSessionDatabaseRegistry,
                )
            }
        }
}

fun jvmSessionPersistencePlugin(databaseFile: File): SimplePlugin =
    SessionPersistencePlugin(jvmSessionPersistenceDatabaseFactory(databaseFile))

actual fun sessionPersistencePluginForTest(databasePath: String): SimplePlugin =
    jvmSessionPersistencePlugin(File(databasePath))

private val jvmSessionDatabaseRegistry = SessionDatabaseRegistry<String>()

@OptIn(ExperimentalAtomicApi::class)
private fun jvmDatabaseAccess(
    key: String,
    database: SessionPersistenceDatabase,
    registry: SessionDatabaseRegistry<String>,
): SessionDatabaseAccess =
    object : SessionDatabaseAccess {
        private val disposed = AtomicBoolean(false)

        override val dao: SessionPersistenceDao
            get() {
                check(!disposed.load()) { "Session persistence database is disposed" }
                return database.sessions()
            }

        override suspend fun <T> transaction(block: suspend () -> T): T {
            check(!disposed.load()) { "Session persistence database is disposed" }
            return database.withWriteTransaction { block() }
        }

        override suspend fun dispose() {
            if (!disposed.compareAndSet(false, true)) return
            val registered = registry.beginClose(key, this)
            try {
                database.close()
            } finally {
                if (registered) registry.finishClose(key, this)
            }
        }
    }
