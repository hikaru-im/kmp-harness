package im.hikaru.ruoyi.framework.signature.core.annotation

import java.util.concurrent.TimeUnit

/**
 * HTTP API 签名注解 (迁移自 Java)
 *
 * @author Zhougang
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiSignature(
    /** 同一个请求多长时间内有效 默认 60 秒 */
    val timeout: Int = 60,
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
    /** 提示信息 */
    val message: String = "签名不正确",
    /** 签名字段：appId */
    val appId: String = "appId",
    /** 签名字段：timestamp */
    val timestamp: String = "timestamp",
    /** 签名字段：nonce */
    val nonce: String = "nonce",
    /** sign 客户端签名 */
    val sign: String = "sign",
)
