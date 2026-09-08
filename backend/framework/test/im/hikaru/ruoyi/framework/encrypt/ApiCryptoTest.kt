package im.hikaru.ruoyi.framework.encrypt

import im.hikaru.ruoyi.framework.encrypt.config.ApiEncryptProperties
import im.hikaru.ruoyi.framework.encrypt.core.crypto.ApiCryptoFactory
import im.hikaru.ruoyi.framework.encrypt.core.filter.ApiDecryptRequestWrapper
import im.hikaru.ruoyi.framework.encrypt.core.filter.ApiEncryptResponseWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.security.KeyPairGenerator
import java.util.Base64

class ApiCryptoTest {
    @Test
    fun `aes supports base64 hex and servlet wrappers`() {
        val key = "52549111389893486934626385991395"
        val encryptor = ApiCryptoFactory.createEncryptor("AES", key)
        val decryptor = ApiCryptoFactory.createDecryptor("AES", key)
        val plainText = """{"name":"yudao"}"""
        val encrypted = encryptor.encrypt(plainText.toByteArray())

        assertEquals(plainText, decryptor.decrypt(encrypted).toString(Charsets.UTF_8))
        val hex = Base64.getDecoder().decode(encrypted).joinToString("") { "%02x".format(it) }
        assertEquals(plainText, decryptor.decrypt(hex).toString(Charsets.UTF_8))

        val request = MockHttpServletRequest().apply { setContent(encrypted.toByteArray()) }
        assertEquals(plainText, ApiDecryptRequestWrapper(request, decryptor).reader.readText())

        val rawResponse = MockHttpServletResponse()
        val response = ApiEncryptResponseWrapper(rawResponse)
        response.writer.write(plainText)
        response.encrypt(
            ApiEncryptProperties().apply { header = "X-Test-Encrypt" },
            encryptor,
        )
        assertEquals("true", rawResponse.getHeader("X-Test-Encrypt"))
        assertEquals(plainText, decryptor.decrypt(rawResponse.contentAsString).toString(Charsets.UTF_8))
    }

    @Test
    fun `rsa encrypts and decrypts payloads across multiple blocks`() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val encryptor = ApiCryptoFactory.createEncryptor("RSA", publicKey)
        val decryptor = ApiCryptoFactory.createDecryptor("RSA", privateKey)
        val data = ByteArray(700) { index -> (index % 251).toByte() }

        assertTrue(data.contentEquals(decryptor.decrypt(encryptor.encrypt(data))))
    }

    @Test
    fun `invalid crypto configuration fails during initialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiCryptoFactory.createEncryptor("AES", "short-key")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiCryptoFactory.createEncryptor("SM4", "0123456789abcdef")
        }
    }
}
