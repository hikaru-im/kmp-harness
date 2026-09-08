package im.hikaru.ruoyi.framework.signature.core.redis

import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit

/**
 * HTTP API 签名 Redis DAO (迁移自 Java, 去 Lombok)
 *
 * @author Zhougang
 */
class ApiSignatureRedisDAO(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    // ========== 验签随机数 ==========

    fun getNonce(appId: String, nonce: String): String? =
        stringRedisTemplate.opsForValue().get(formatNonceKey(appId, nonce))

    fun setNonce(appId: String, nonce: String, time: Int, timeUnit: TimeUnit): Boolean? =
        stringRedisTemplate.opsForValue().setIfAbsent(formatNonceKey(appId, nonce), "", time.toLong(), timeUnit)

    private fun formatNonceKey(appId: String, nonce: String): String =
        String.format(SIGNATURE_NONCE, appId, nonce)

    // ========== 签名密钥 ==========

    fun getAppSecret(appId: String): String? =
        stringRedisTemplate.opsForHash<String, String>().get(SIGNATURE_APPID, appId)

    companion object {
        private const val SIGNATURE_NONCE = "api_signature_nonce:%s:%s"
        private const val SIGNATURE_APPID = "api_signature_app"
    }
}
