package im.hikaru.ruoyi.framework.idempotent.core.keyresolver

import im.hikaru.ruoyi.framework.idempotent.core.annotation.Idempotent
import org.aspectj.lang.JoinPoint

/**
 * 幂等 Key 解析器接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface IdempotentKeyResolver {
    fun resolver(joinPoint: JoinPoint, idempotent: Idempotent): String
}
