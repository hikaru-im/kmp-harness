package im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl

import im.hikaru.ruoyi.framework.common.util.string.StrUtils
import im.hikaru.ruoyi.framework.idempotent.core.annotation.Idempotent
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.IdempotentKeyResolver
import org.aspectj.lang.JoinPoint

/**
 * 默认（全局级别）幂等 Key 解析器 (迁移自 Java, 去 Hutool SecureUtil → StrUtils.md5)
 *
 * @author 芋道源码
 */
class DefaultIdempotentKeyResolver : IdempotentKeyResolver {
    override fun resolver(joinPoint: JoinPoint, idempotent: Idempotent): String {
        val methodName = joinPoint.signature.toString()
        val argsStr = StrUtils.joinMethodArgs(joinPoint)
        return StrUtils.md5(methodName + argsStr)
    }
}
