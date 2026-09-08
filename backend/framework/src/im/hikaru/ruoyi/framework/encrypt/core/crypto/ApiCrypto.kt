package im.hikaru.ruoyi.framework.encrypt.core.crypto

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

fun interface ApiDecryptor {
    fun decrypt(cipherText: String): ByteArray
}

fun interface ApiEncryptor {
    fun encrypt(data: ByteArray): String
}

object ApiCryptoFactory {
    fun createDecryptor(algorithm: String, key: String): ApiDecryptor = when {
        algorithm.equals("AES", ignoreCase = true) -> createAesDecryptor(key)
        algorithm.equals("RSA", ignoreCase = true) -> createRsaDecryptor(key)
        else -> throw IllegalArgumentException("不支持的加密算法：$algorithm")
    }

    fun createEncryptor(algorithm: String, key: String): ApiEncryptor = when {
        algorithm.equals("AES", ignoreCase = true) -> createAesEncryptor(key)
        algorithm.equals("RSA", ignoreCase = true) -> createRsaEncryptor(key)
        else -> throw IllegalArgumentException("不支持的加密算法：$algorithm")
    }

    private fun createAesDecryptor(key: String): ApiDecryptor {
        val secretKey = aesKey(key)
        return ApiDecryptor { cipherText ->
            Cipher.getInstance(AES_TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, secretKey)
                doFinal(decodeCipherText(cipherText))
            }
        }
    }

    private fun createAesEncryptor(key: String): ApiEncryptor {
        val secretKey = aesKey(key)
        return ApiEncryptor { data ->
            val encrypted = Cipher.getInstance(AES_TRANSFORMATION).run {
                init(Cipher.ENCRYPT_MODE, secretKey)
                doFinal(data)
            }
            Base64.getEncoder().encodeToString(encrypted)
        }
    }

    private fun aesKey(key: String): SecretKeySpec {
        val bytes = key.toByteArray(Charsets.UTF_8)
        require(bytes.size in AES_KEY_LENGTHS) { "AES 密钥长度必须为 16、24 或 32 字节" }
        return SecretKeySpec(bytes, "AES")
    }

    private fun createRsaDecryptor(key: String): ApiDecryptor {
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(decodePemKey(key)),
        )
        val blockSize = rsaBlockSize(privateKey as RSAKey)
        return ApiDecryptor { cipherText ->
            processRsaBlocks(decodeCipherText(cipherText), blockSize) { block ->
                Cipher.getInstance(RSA_TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, privateKey)
                    doFinal(block)
                }
            }
        }
    }

    private fun createRsaEncryptor(key: String): ApiEncryptor {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(decodePemKey(key)),
        )
        val blockSize = rsaBlockSize(publicKey as RSAKey) - RSA_PKCS1_PADDING_SIZE
        return ApiEncryptor { data ->
            val encrypted = processRsaBlocks(data, blockSize) { block ->
                Cipher.getInstance(RSA_TRANSFORMATION).run {
                    init(Cipher.ENCRYPT_MODE, publicKey)
                    doFinal(block)
                }
            }
            Base64.getEncoder().encodeToString(encrypted)
        }
    }

    private fun processRsaBlocks(
        data: ByteArray,
        blockSize: Int,
        transform: (ByteArray) -> ByteArray,
    ): ByteArray {
        require(blockSize > 0) { "RSA 分块大小必须大于 0" }
        val output = ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            val length = minOf(blockSize, data.size - offset)
            output.write(transform(data.copyOfRange(offset, offset + length)))
            offset += length
        }
        return output.toByteArray()
    }

    private fun decodePemKey(key: String): ByteArray = Base64.getMimeDecoder().decode(
        key.replace(PEM_MARKER_REGEX, "").filterNot(Char::isWhitespace),
    )

    private fun decodeCipherText(cipherText: String): ByteArray {
        val value = cipherText.trim()
        if (value.length % 2 == 0 && value.isNotEmpty() && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
        return Base64.getMimeDecoder().decode(value)
    }

    private fun rsaBlockSize(key: RSAKey): Int = (key.modulus.bitLength() + 7) / 8

    private const val AES_TRANSFORMATION = "AES/ECB/PKCS5Padding"
    private val AES_KEY_LENGTHS = setOf(16, 24, 32)
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val RSA_PKCS1_PADDING_SIZE = 11
    private val PEM_MARKER_REGEX = Regex("-----BEGIN [^-]+-----|-----END [^-]+-----")
}
