@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package im.hikaru.harness.client.account

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSUserDefaults
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SESSION_ACCOUNT = "member-session"
private const val SESSION_SERVICE = "im.hikaru.harness.account.v1"
private const val SIGNED_OUT_KEY = "harness-member-account-v1.signed-out"
private const val TENANT_KEY = "harness-member-account-v1.tenant-id"

private val sessionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class IosAppSessionStore : AppSessionStore {
    override val durable = true
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): AppSession? = if (defaults.boolForKey(SIGNED_OUT_KEY)) null
        else readKeychain()?.let { sessionJson.decodeFromString<AppSession>(it) }

    override fun write(session: AppSession) {
        writeKeychain(sessionJson.encodeToString(session))
        defaults.setBool(false, forKey = SIGNED_OUT_KEY)
    }

    override fun clear() {
        // The marker is ordinary, non-sensitive revocation state. It is written
        // before SecItemDelete so a locked keychain cannot resurrect a token.
        defaults.setBool(true, forKey = SIGNED_OUT_KEY)
        val status = withBaseQuery { query -> SecItemDelete(query) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain clear failed" }
    }

    private fun readKeychain(): String? = withBaseQuery { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<COpaquePointerVar>()
            result.value = null
            when (SecItemCopyMatching(query, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val rawResult = checkNotNull(result.value) { "Keychain returned no session data" }
                    val data: CFDataRef = rawResult.reinterpret()
                    try { data.decodeUtf8() } finally { CFRelease(rawResult) }
                }
                else -> error("Keychain read failed")
            }
        }
    }

    private fun writeKeychain(value: String) {
        withBaseQuery { query ->
            withUtf8Data(value) { data ->
                withMutableDictionary { attributes ->
                    CFDictionarySetValue(attributes, kSecValueData, data)
                    when (SecItemUpdate(query, attributes)) {
                        errSecSuccess -> Unit
                        errSecItemNotFound -> {
                            CFDictionarySetValue(query, kSecValueData, data)
                            check(SecItemAdd(query, null) == errSecSuccess) { "Keychain save failed" }
                        }
                        else -> error("Keychain save failed")
                    }
                }
            }
        }
    }
}

class IosAppTenantStore : AppTenantStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(): Long? = defaults.stringForKey(TENANT_KEY)?.toLongOrNull()?.takeIf { it > 0L }
    override fun write(tenantId: Long?) {
        if (tenantId == null) defaults.removeObjectForKey(TENANT_KEY)
        else defaults.setObject(tenantId.toString(), forKey = TENANT_KEY)
    }
}

private inline fun <T> withBaseQuery(block: (CFMutableDictionaryRef) -> T): T =
    withMutableDictionary { query ->
        val service = checkNotNull(
            CFStringCreateWithCString(kCFAllocatorDefault, SESSION_SERVICE, kCFStringEncodingUTF8),
        )
        val account = checkNotNull(
            CFStringCreateWithCString(kCFAllocatorDefault, SESSION_ACCOUNT, kCFStringEncodingUTF8),
        )
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, account)
            block(query)
        } finally {
            CFRelease(account)
            CFRelease(service)
        }
    }

private inline fun <T> withMutableDictionary(block: (CFMutableDictionaryRef) -> T): T {
    val dictionary = checkNotNull(
        CFDictionaryCreateMutable(
            allocator = kCFAllocatorDefault,
            capacity = 0,
            keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
            valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
        ),
    )
    try { return block(dictionary) } finally { CFRelease(dictionary) }
}

private inline fun <T> withUtf8Data(value: String, block: (CFDataRef) -> T): T {
    val bytes = value.encodeToByteArray()
    val data = checkNotNull(
        if (bytes.isEmpty()) {
            CFDataCreate(kCFAllocatorDefault, null, 0)
        } else {
            bytes.usePinned { pinned ->
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
            }
        },
    )
    try { return block(data) } finally { CFRelease(data) }
}

private fun CFDataRef.decodeUtf8(): String {
    val length = CFDataGetLength(this)
    require(length in 0..Int.MAX_VALUE.toLong()) { "Keychain session data is too large" }
    if (length == 0L) return ""
    val bytes = checkNotNull(CFDataGetBytePtr(this)) { "Keychain returned empty session bytes" }
    return ByteArray(length.toInt()) { index -> bytes[index].toByte() }.decodeToString()
}
