package im.hikaru.ruoyi.framework.idempotent.core.annotation

import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.IdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl.DefaultIdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl.UserIdempotentKeyResolver
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 幂等注解 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    /** 幂等的超时时间，默认为 1 秒 */
    val timeout: Int = 1,
    /** 时间单位，默认为 SECONDS 秒 */
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
    /** 提示信息 */
    val message: String = "重复请求，请稍后重试",
    /** Key 解析器 */
    val keyResolver: KClass<out IdempotentKeyResolver> = DefaultIdempotentKeyResolver::class,
    /** Key 参数 */
    val keyArg: String = "",
    /** 异常时删除 Key */
    val deleteKeyWhenException: Boolean = true,
)
