package im.hikaru.harness.session.persistence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlinx.coroutines.test.runTest

class SessionPersistenceDatabaseFactoryTest {
    @Test
    fun realRoomFactoryRejectsDuplicateLocationAndReopensAfterClose() = runTest {
        val root = Files.createTempDirectory("harness-session-factory-room-")
        val databaseFile = root.resolve("harness-sessions.db").toFile()
        val firstFactory = jvmSessionPersistenceDatabaseFactory(databaseFile)
        val secondFactory = jvmSessionPersistenceDatabaseFactory(databaseFile)

        val first = firstFactory.create()
        assertFailsWith<IllegalStateException> { firstFactory.create() }
        assertFailsWith<IllegalStateException> { secondFactory.create() }

        first.dispose()
        val reopened = secondFactory.create()
        assertNotSame(first, reopened)
        reopened.dispose()

        val different =
            jvmSessionPersistenceDatabaseFactory(root.resolve("other.db").toFile()).create()
        assertNotSame(reopened, different)
        different.dispose()
        root.toFile().deleteRecursively()
    }
}
