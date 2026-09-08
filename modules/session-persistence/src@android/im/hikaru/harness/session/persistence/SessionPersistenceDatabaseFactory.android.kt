package im.hikaru.harness.session.persistence

import android.content.Context
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.Dispatchers

/** Android launcher-owned factory. The database name is independent from the app DB. */
@OptIn(ExperimentalAtomicApi::class)
fun androidSessionPersistenceDatabaseFactory(
    context: Context,
    databaseName: String = "harness-sessions.db",
): SessionPersistenceDatabaseFactory {
    val applicationContext = context.applicationContext
    val key = applicationContext.getDatabasePath(databaseName).absolutePath
    return object : SessionPersistenceDatabaseFactory {
        override fun create(): SessionDatabaseAccess =
            androidSessionDatabaseRegistry.acquire(key, this) {
                androidDatabaseAccess(
                    key = key,
                    database =
                        Room.databaseBuilder<SessionPersistenceDatabase>(applicationContext, databaseName)
                            .addMigrations(*SessionPersistenceMigrations.all)
                            .setDriver(BundledSQLiteDriver())
                            .setQueryCoroutineContext(Dispatchers.IO)
                            .build(),
                    registry = androidSessionDatabaseRegistry,
                )
            }
        }
}

private val androidSessionDatabaseRegistry = SessionDatabaseRegistry<String>()

@OptIn(ExperimentalAtomicApi::class)
private fun androidDatabaseAccess(
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
