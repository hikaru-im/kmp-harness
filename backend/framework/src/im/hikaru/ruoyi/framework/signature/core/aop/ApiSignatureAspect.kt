package im.hikaru.ruoyi.framework.signature.core.aop

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.signature.core.annotation.ApiSignature
import im.hikaru.ruoyi.framework.signature.core.redis.ApiSignatureRedisDAO
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.TreeMap

/**
 * 拦截声明了 [ApiSignature] 注解的方法，实现签名 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：
 *  - Hutool DigestUtil.sha256Hex → JDK MessageDigest("SHA-256")
 *  - Hutool MapUtil.join → Kotlin joinToString
 *  - Hutool StrUtil → Kotlin stdlib
 *  - Hutool Assert.notNull → require
 *
 * @author Zhougang
 */
@Aspect
class ApiSignatureAspect(
    private val signatureRedisDAO: ApiSignatureRedisDAO,
) {

    @Before(value = "@annotation(signature)")
    fun beforePointCut(joinPoint: JoinPoint, signature: ApiSignature) {
        // 1. 验证通过，直接结束
        if (verifySignature(signature, ServletUtils.getRequest()!!)) {
            return
        }
        // 2. 验证不通过，抛出异常
        log.error("[beforePointCut][方法{} 参数({}) 签名失败]", joinPoint.signature.toString(), joinPoint.args)
        throw ServiceException(
            GlobalErrorCodeConstants.BAD_REQUEST.code,
            signature.message.ifBlank { GlobalErrorCodeConstants.BAD_REQUEST.msg },
        )
    }

    fun verifySignature(signature: ApiSignature, request: HttpServletRequest): Boolean {
        // 1.1 校验 Header
        if (!verifyHeaders(signature, request)) {
            return false
        }
        // 1.2 校验 appId 是否能获取到对应的 appSecret
        val appId = request.getHeader(signature.appId)
        val appSecret = signatureRedisDAO.getAppSecret(appId)
        requireNotNull(appSecret) { "[appId($appId)] 找不到对应的 appSecret" }

        // 2. 校验签名
        val clientSignature = request.getHeader(signature.sign) // 客户端签名
        val serverSignatureString = buildSignatureString(signature, request, appSecret) // 服务端签名字符串
        val serverSignature = sha256Hex(serverSignatureString) // 服务端签名
        if (clientSignature != serverSignature) {
            return false
        }

        // 3. 将 nonce 记入缓存，防止重复使用
        val nonce = request.getHeader(signature.nonce)
        val setSuccess = signatureRedisDAO.setNonce(appId, nonce, signature.timeout * 2, signature.timeUnit)
        if (setSuccess != true) {
            val timestamp = request.getHeader(signature.timestamp)
            log.info("[verifySignature][appId({}) timestamp({}) nonce({}) sign({}) 存在重复请求]", appId, timestamp, nonce, clientSignature)
            throw ServiceException(GlobalErrorCodeConstants.REPEATED_REQUESTS.code, "存在重复请求")
        }
        return true
    }

    private fun verifyHeaders(signature: ApiSignature, request: HttpServletRequest): Boolean {
        // 1. 非空校验
        val appId = request.getHeader(signature.appId) ?: return false
        if (appId.isBlank()) return false
        val timestamp = request.getHeader(signature.timestamp) ?: return false
        if (timestamp.isBlank()) return false
        val nonce = request.getHeader(signature.nonce) ?: return false
        if (nonce.length < 10) return false
        val sign = request.getHeader(signature.sign) ?: return false
        if (sign.isBlank()) return false

        // 2. 检查 timestamp 是否超出允许的范围
        val expireTime = signature.timeUnit.toMillis(signature.timeout.toLong())
        val requestTimestamp = timestamp.toLong()
        val timestampDisparity = Math.abs(System.currentTimeMillis() - requestTimestamp)
        if (timestampDisparity > expireTime) {
            return false
        }
        // 3. 检查 nonce 是否存在，有且仅能使用一次
        return signatureRedisDAO.getNonce(appId, nonce) == null
    }

    private fun buildSignatureString(signature: ApiSignature, request: HttpServletRequest, appSecret: String): String {
        val parameterMap = getRequestParameterMap(request) // 请求参数
        val headerMap = getRequestHeaderMap(signature, request) // 请求头
        val requestBody = ServletUtils.getBody(request) ?: "" // 请求体
        return joinSortedMap(parameterMap) + requestBody + joinSortedMap(headerMap) + appSecret
    }

    private fun getRequestHeaderMap(signature: ApiSignature, request: HttpServletRequest): TreeMap<String, String> {
        val sortedMap = TreeMap<String, String>()
        sortedMap[signature.appId] = request.getHeader(signature.appId)
        sortedMap[signature.timestamp] = request.getHeader(signature.timestamp)
        sortedMap[signature.nonce] = request.getHeader(signature.nonce)
        return sortedMap
    }

    private fun getRequestParameterMap(request: HttpServletRequest): TreeMap<String, String> {
        val sortedMap = TreeMap<String, String>()
        for (entry in request.parameterMap.entries) {
            sortedMap[entry.key] = entry.value.firstOrNull() ?: ""
        }
        return sortedMap
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApiSignatureAspect::class.java)

        /** 替代 Hutool DigestUtil.sha256Hex */
        private fun sha256Hex(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** 替代 Hutool MapUtil.join(map, "&", "=") */
        private fun joinSortedMap(map: Map<String, String>): String =
            map.entries.joinToString("&") { "${it.key}=${it.value}" }
    }
}
