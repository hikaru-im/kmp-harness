package im.hikaru.harness.client.account

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

private const val SERVICE = "im.hikaru.harness.account.v1"
private const val ACCOUNT = "member-session"

/** OS adapters only operate on Harness's own namespace. The supplied directory is app-owned, not Host home. */
fun desktopAppSessionStore(appDataDirectory: Path): AppSessionStore {
    val os = System.getProperty("os.name").lowercase()
    val backend: SecureBlobStore = try {
        when {
            os.startsWith("windows") -> DpapiBlobStore(appDataDirectory.resolve("account.dpapi"))
            os.contains("mac") -> MacKeychainBlobStore()
            Files.isExecutable(Path.of("/usr/bin/secret-tool")) && System.getenv("DBUS_SESSION_BUS_ADDRESS") != null -> SecretToolBlobStore()
            else -> return MemoryAppSessionStore()
        }
    } catch (_: LinkageError) { return MemoryAppSessionStore()
    } catch (_: Exception) { return MemoryAppSessionStore() }
    return DesktopAppSessionStore(backend, appDataDirectory.resolve("account-signed-out"))
}

/** Public for deterministic storage/restart tests with a fake OS backend. */
interface SecureBlobStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    fun clear()
}

class DesktopAppSessionStore(
    private val backend: SecureBlobStore,
    private val signedOutMarker: Path,
) : AppSessionStore {
    override val durable = true
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override fun read(): AppSession? = if (Files.exists(signedOutMarker)) null
        else backend.read()?.let { json.decodeFromString<AppSession>(it.decodeToString()) }
    override fun write(session: AppSession) {
        backend.write(json.encodeToString(session).encodeToByteArray())
        Files.deleteIfExists(signedOutMarker)
    }
    override fun clear() {
        // Persist a non-secret tombstone first: a locked keyring must never resurrect a logged-out session.
        atomicWrite(signedOutMarker, byteArrayOf(1))
        try { backend.clear() } catch (_: Exception) { /* Tombstone prevents recovery; next save replaces the item. */ }
    }
}

private fun atomicWrite(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.toAbsolutePath().parent)
    val temporary = Files.createTempFile(path.toAbsolutePath().parent, ".account-", ".tmp")
    try {
        Files.write(temporary, bytes)
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(temporary) }
}

private class DpapiBlobStore(private val path: Path) : SecureBlobStore {
    override fun read(): ByteArray? = if (Files.exists(path)) Crypt32Util.cryptUnprotectData(Files.readAllBytes(path)) else null
    override fun write(bytes: ByteArray) = atomicWrite(path, Crypt32Util.cryptProtectData(bytes))
    override fun clear() { Files.deleteIfExists(path) }
}

private class SecretToolBlobStore : SecureBlobStore {
    override fun read(): ByteArray? = run(listOf("lookup", "application", SERVICE), null, true)
    override fun write(bytes: ByteArray) { run(listOf("store", "--label=Harness member account", "application", SERVICE), bytes, false) }
    override fun clear() { run(listOf("clear", "application", SERVICE), null, true) }
    private fun run(arguments: List<String>, input: ByteArray?, allowMissing: Boolean): ByteArray? {
        val process = ProcessBuilder(listOf("/usr/bin/secret-tool") + arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            process.outputStream.use { if (input != null) it.write(input) }
            check(process.waitFor(10, TimeUnit.SECONDS)) { "Secure storage timed out" }
            val output = process.inputStream.use { it.readBytes() }
            if (allowMissing && process.exitValue() == 1 && output.isEmpty()) return null
            check(process.exitValue() == 0) { "Secure storage unavailable" }
            return output.takeIf { it.isNotEmpty() }
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}

private interface MacSecurity : Library {
    fun SecKeychainFindGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray,
        accountLength: Int, account: ByteArray, length: IntByReference?, data: PointerByReference?, item: PointerByReference): Int
    fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray,
        accountLength: Int, account: ByteArray, length: Int, data: ByteArray, item: PointerByReference?): Int
    fun SecKeychainItemModifyAttributesAndData(item: Pointer, attributes: Pointer?, length: Int, data: ByteArray): Int
    fun SecKeychainItemDelete(item: Pointer): Int
    fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer): Int
}
private interface MacCoreFoundation : Library { fun CFRelease(value: Pointer) }
private class MacKeychainBlobStore : SecureBlobStore {
    private val security = Native.load("/System/Library/Frameworks/Security.framework/Security", MacSecurity::class.java)
    private val core = Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", MacCoreFoundation::class.java)
    private val service = SERVICE.encodeToByteArray()
    private val account = ACCOUNT.encodeToByteArray()
    private fun find(length: IntByReference? = null, data: PointerByReference? = null, item: PointerByReference): Int =
        security.SecKeychainFindGenericPassword(null, service.size, service, account.size, account, length, data, item)
    override fun read(): ByteArray? {
        val length = IntByReference(); val data = PointerByReference(); val item = PointerByReference()
        val status = find(length, data, item)
        if (status == -25300) return null
        check(status == 0) { "Keychain unavailable" }
        try { return data.value.getByteArray(0, length.value) }
        finally { security.SecKeychainItemFreeContent(null, data.value); core.CFRelease(item.value) }
    }
    override fun write(bytes: ByteArray) {
        val item = PointerByReference()
        when (find(item = item)) {
            -25300 -> check(security.SecKeychainAddGenericPassword(null, service.size, service, account.size, account, bytes.size, bytes, null) == 0)
            0 -> try { check(security.SecKeychainItemModifyAttributesAndData(item.value, null, bytes.size, bytes) == 0) }
                finally { core.CFRelease(item.value) }
            else -> error("Keychain unavailable")
        }
    }
    override fun clear() {
        val item = PointerByReference()
        when (find(item = item)) {
            -25300 -> Unit
            0 -> try { check(security.SecKeychainItemDelete(item.value) == 0) } finally { core.CFRelease(item.value) }
            else -> error("Keychain unavailable")
        }
    }
}
