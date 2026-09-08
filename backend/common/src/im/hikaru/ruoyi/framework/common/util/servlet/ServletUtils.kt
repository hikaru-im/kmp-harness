package im.hikaru.ruoyi.framework.common.util.servlet

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.IOException

/**
 * 客户端工具类 (迁移自 Java, 去 Hutool JakartaServletUtil)
 *
 * 迁移说明：Hutool JakartaServletUtil.getClientIP/getBody/getParamMap/getHeaderMap → 手写实现
 *
 * @author 芋道源码
 */
object ServletUtils {

    /** 疑似代理请求头 (用于 getClientIP，从后向前取第一个非 unknown 的 IP) */
    private const val UNKNOWN = "unknown"
    private val IP_HEADERS = arrayOf(
        "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR",
    )

    /**
     * 返回 JSON 字符串
     *
     * @param response 响应
     * @param object 对象，会序列化成 JSON 字符串
     */
    @JvmStatic
    @Throws(IOException::class)
    fun writeJSON(response: HttpServletResponse, obj: Any?) {
        val content = JsonUtils.toJsonString(obj)
        response.contentType = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
        response.writer.write(content)
    }

    /**
     * @param request 请求
     * @return ua
     */
    @JvmStatic
    fun getUserAgent(request: HttpServletRequest): String = request.getHeader("User-Agent") ?: ""

    /**
     * 获得请求
     */
    @JvmStatic
    fun getRequest(): HttpServletRequest? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() ?: return null
        return (requestAttributes as? ServletRequestAttributes)?.request
    }

    @JvmStatic
    fun getUserAgent(): String? {
        val request = getRequest() ?: return null
        return getUserAgent(request)
    }

    @JvmStatic
    fun getClientIP(): String? {
        val request = getRequest() ?: return null
        return getClientIP(request)
    }

    @JvmStatic
    fun isJsonRequest(request: ServletRequest): Boolean =
        request.contentType?.startsWith(MediaType.APPLICATION_JSON_VALUE, ignoreCase = true) == true

    @JvmStatic
    fun getBody(request: HttpServletRequest): String? {
        // 只有在 json 请求在读取，因为只有 CacheRequestBodyFilter 才会进行缓存，支持重复读取
        if (!isJsonRequest(request)) return null
        return request.reader.readText()
    }

    @JvmStatic
    fun getBodyBytes(request: HttpServletRequest): ByteArray? {
        // 只有在 json 请求在读取
        if (!isJsonRequest(request)) return null
        return getBody(request)?.toByteArray(Charsets.UTF_8)
    }

    @JvmStatic
    fun getClientIP(request: HttpServletRequest): String {
        for (header in IP_HEADERS) {
            val ip = request.getHeader(header)
            if (!ip.isNullOrBlank() && !UNKNOWN.equals(ip, ignoreCase = true)) {
                // 多级代理时，取第一个非 unknown 的
                return ip.substringBefore(",").trim()
            }
        }
        return request.remoteAddr ?: UNKNOWN
    }

    @JvmStatic
    fun getParamMap(request: HttpServletRequest): Map<String, String> =
        request.parameterMap.entries.associate { it.key to (it.value.firstOrNull() ?: "") }

    @JvmStatic
    fun getHeaderMap(request: HttpServletRequest): Map<String, String> {
        val map = HashMap<String, String>()
        val names = request.headerNames ?: return map
        while (names.hasMoreElements()) {
            val name = names.nextElement()
            map[name] = request.getHeader(name) ?: ""
        }
        return map
    }
}
