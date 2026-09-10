package im.hikaru.harness.client.account

import android.content.Context
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private const val PREFERENCES_NAME = "harness-member-account-v1"
private const val SESSION_KEY = "member-session"
private const val TENANT_KEY = "tenant-id"
private const val KEY_ALIAS = "harness-member-account-v1-key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

private val sessionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
class AndroidAppSessionStore(
    context: Context,
) : AppSessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override val durable = true

    override fun read(): AppSession? =
        preferences.getString(SESSION_KEY, null)
            ?.let(::decrypt)
            ?.let { sessionJson.decodeFromString<AppSession>(it) }


    override fun write(session: AppSession) {
        val encoded = sessionJson.encodeToString(session)
        check(preferences.edit().putString(SESSION_KEY, encrypt(encoded)).commit()) { "Secure session save failed" }
    }

    override fun clear() {
        check(preferences.edit().remove(SESSION_KEY).commit()) { "Secure session clear failed" }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.encodeToByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val encrypted = Base64.decode(value, Base64.NO_WRAP)
        require(encrypted.size > GCM_IV_LENGTH_BYTES)
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val payload = encrypted.copyOfRange(GCM_IV_LENGTH_BYTES, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(payload).decodeToString()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
