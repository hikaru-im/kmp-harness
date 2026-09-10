package im.hikaru.harness.client.account

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAppSessionStoreTest {
    @Test
    fun `logout tombstone prevents recovery when secure backend cannot clear`() {
        val directory = Files.createTempDirectory("harness-account-test")
        val backend = FakeSecureBlobStore()
        val marker = directory.resolve("signed-out")
        val store = DesktopAppSessionStore(backend, marker)
        val session = AppSession(
            MemberIdentity(BackendTenant("https://api.example.test", 7), 11),
            "access-secret",
            "refresh-secret",
            null,
        )

        store.write(session)
        assertEquals(session, store.read())
        backend.failClear = true
        store.clear()

        assertNull(store.read())
        assertEquals(byteArrayOf(1).toList(), Files.readAllBytes(marker).toList())
    }

    private class FakeSecureBlobStore : SecureBlobStore {
        var value: ByteArray? = null
        var failClear = false
        override fun read(): ByteArray? = value
        override fun write(bytes: ByteArray) { value = bytes.copyOf() }
        override fun clear() {
            if (failClear) error("locked")
            value = null
        }
    }
}
