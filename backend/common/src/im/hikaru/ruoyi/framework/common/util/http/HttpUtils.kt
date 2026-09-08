package im.hikaru.ruoyi.framework.common.util.http

import jakarta.servlet.http.HttpServletRequest
import org.springframework.util.StringUtils
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * HTTP 工具类 (迁移自 Java, 去 Hutool)
 *
 * 迁移说明：
 *  - Hutool StrUtil.isEmpty/contains/SLASH → Kotlin stdlib
 *  - Hutool UrlBuilder → Spring UriComponentsBuilder
 *  - Hutool Base64/HttpRequest/HttpResponse → 仅保留常用方法，HTTP 调用迁移至 RestClient
 *
 * @author 芋道源码
 */
object HttpUtils {

    /**
     * 编码 URL 参数
     */
    @JvmStatic
    fun encodeUtf8(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    /**
     * 解码 URL 参数（query parameter）
     *
     * 注意：此方法会将 + 解码为空格，适用于 query parameter，不适用于 URL path
     */
    @JvmStatic
    fun decodeUtf8(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    /**
     * 解码 URL 路径
     *
     * 与 [decodeUtf8] 不同，此方法不会将 + 解码为空格，保持 + 为字面字符
     */
    @JvmStatic
    fun decodeUrlPath(path: String): String {
        if (path.isEmpty()) return path
        // 先将 + 替换为 %2B，避免被 URLDecoder 解码为空格
        val encoded = path.replace("+", "%2B")
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8)
    }

    /**
     * 编码 URL 路径，按路径段编码，保留 / 分隔符
     */
    @JvmStatic
    fun encodeUrlPath(path: String): String {
        if (path.isEmpty()) return path
        val segments = path.split("/", ignoreCase = false)
        val result = StringBuilder(path.length)
        for (i in segments.indices) {
            if (i > 0) {
                result.append("/")
            }
            result.append(encodeUrlPathSegment(segments[i]))
        }
        return result.toString()
    }

    @JvmStatic
    fun encodeUrlPathSegment(segment: String): String =
        UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8)

    @JvmStatic
    fun removeUrlPathQueryAndFragment(path: String): String {
        if (path.isEmpty()) return path
        var endIndex = path.length
        val queryIndex = path.indexOf('?')
        if (queryIndex >= 0) {
            endIndex = queryIndex
        }
        val fragmentIndex = path.indexOf('#')
        if (fragmentIndex >= 0 && fragmentIndex < endIndex) {
            endIndex = fragmentIndex
        }
        return path.substring(0, endIndex)
    }

    /**
     * 移除 URL 的 query 和 fragment
     */
    @JvmStatic
    fun removeUrlQuery(url: String): String {
        if (!url.contains('?')) {
            return url
        }
        // Hutool UrlBuilder → JDK URI 解析
        val uri = URI(url)
        return uri.scheme + "://" + uri.rawAuthority + uri.rawPath
    }

    /**
     * 替换 URL 的 query 参数
     */
    @JvmStatic
    fun replaceUrlQuery(url: String, key: String, value: String): String {
        // 简化实现：移除原 query 后追加新的
        val cleaned = removeUrlQuery(url)
        val separator = if (cleaned.contains("?")) "&" else "?"
        return cleaned + separator + key + "=" + encodeUtf8(value)
    }

    /**
     * Appends encoded values to either the query string or URI fragment.
     */
    @JvmStatic
    fun append(
        base: String,
        query: Map<String, *>,
        keys: Map<String, String>? = null,
        fragment: Boolean = false,
    ): String {
        val template = UriComponentsBuilder.newInstance()
        var builder = UriComponentsBuilder.fromUriString(base)
        val redirectUri = try {
            builder.build(true).toUri()
        } catch (_: Exception) {
            val uri = builder.build().toUri()
            builder = UriComponentsBuilder.fromUri(uri)
            uri
        }
        template.scheme(redirectUri.scheme)
            .port(redirectUri.port)
            .host(redirectUri.host)
            .userInfo(redirectUri.userInfo)
            .path(redirectUri.path)

        if (fragment) {
            val values = StringBuilder(redirectUri.fragment.orEmpty())
            query.keys.forEach { key ->
                if (values.isNotEmpty()) values.append('&')
                values.append(keys?.get(key) ?: key).append("={").append(key).append('}')
            }
            if (values.isNotEmpty()) template.fragment(values.toString())
            val encoded = template.build().expand(query).encode()
            builder.fragment(encoded.fragment)
        } else {
            query.keys.forEach { key ->
                template.queryParam(keys?.get(key) ?: key, "{$key}")
            }
            template.fragment(redirectUri.fragment)
            val encoded = template.build().expand(query).encode()
            encoded.query?.let(builder::query)
        }
        return builder.build().toUriString()
    }

    /**
     * Reads OAuth2 client credentials from HTTP Basic authentication or request parameters.
     */
    @JvmStatic
    fun obtainBasicAuthorization(request: HttpServletRequest): Array<String>? {
        val header = request.getHeader("Authorization")
        val encoded = header?.takeIf { it.startsWith("Basic ", ignoreCase = true) }?.substring(6)?.trim()
        val credentials = encoded?.takeIf { it.isNotEmpty() }?.let {
            runCatching { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }.getOrNull()
        }
        val clientId = credentials?.substringBefore(':') ?: request.getParameter("client_id")
        val clientSecret = credentials?.substringAfter(':', "") ?: request.getParameter("client_secret")
        return if (!clientId.isNullOrEmpty() && !clientSecret.isNullOrEmpty()) {
            arrayOf(clientId, clientSecret)
        } else {
            null
        }
    }
}
