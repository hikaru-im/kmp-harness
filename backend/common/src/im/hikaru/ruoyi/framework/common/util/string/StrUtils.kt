package im.hikaru.ruoyi.framework.common.util.string

import org.aspectj.lang.JoinPoint

/**
 * 字符串工具类 (迁移自 Java, 去 Hutool)
 *
 * 迁移说明：
 *  - Hutool StrUtil.maxLength → Kotlin substring
 *  - Hutool StrUtil.isEmpty/startWith → Kotlin stdlib
 *  - Hutool ArrayUtil.isEmpty/join → Kotlin stdlib
 *  - Hutool StrUtil.splitToLong → Kotlin split + map
 *
 * @author 芋道源码
 */
object StrUtils {

    @JvmStatic
    fun maxLength(str: CharSequence?, maxLength: Int): String? {
        if (str == null) return null
        return if (str.length <= maxLength - 3) str.toString() else str.substring(0, maxLength - 3) + "..."
    }

    /**
     * 给定字符串是否以任何一个字符串开始
     */
    @JvmStatic
    fun startWithAny(str: String?, prefixes: Collection<String>?): Boolean {
        if (str.isNullOrEmpty() || prefixes.isNullOrEmpty()) return false
        return prefixes.any { str.startsWith(it) }
    }

    @JvmStatic
    fun splitToLong(value: String, separator: CharSequence): List<Long> =
        value.split(separator.toString()).filter { it.isNotBlank() }.map { it.trim().toLong() }

    @JvmStatic
    fun splitToLongSet(value: String): Set<Long> = splitToLongSet(value, ",")

    @JvmStatic
    fun splitToLongSet(value: String, separator: CharSequence): Set<Long> =
        value.split(separator.toString()).filter { it.isNotBlank() }.map { it.trim().toLong() }.toSet()

    /**
     * 拼接 AOP 方法参数为字符串 (用于幂等/限流 Key 生成)
     *
     * 过滤掉 servlet/spring web 相关参数 (避免序列化异常)
     */
    @JvmStatic
    fun joinMethodArgs(joinPoint: JoinPoint): String {
        val args = joinPoint.args
        if (args.isNullOrEmpty()) return ""
        return args.joinToString(",") { item ->
            if (item == null) {
                ""
            } else {
                val clazzName = item.javaClass.name
                if (startWithAny(clazzName, listOf("javax.servlet", "jakarta.servlet", "org.springframework.web"))) {
                    ""
                } else {
                    item.toString()
                }
            }
        }
    }

    /**
     * MD5 加密 (替代 Hutool SecureUtil.md5)
     */
    @JvmStatic
    fun md5(value: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
