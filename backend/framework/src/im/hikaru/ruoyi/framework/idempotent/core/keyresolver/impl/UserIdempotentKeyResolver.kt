package im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl

import im.hikaru.ruoyi.framework.common.util.string.StrUtils
import im.hikaru.ruoyi.framework.idempotent.core.annotation.Idempotent
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.IdempotentKeyResolver
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.aspectj.lang.JoinPoint

/**
 * 用户级别的幂等 Key 解析器 (迁移自 Java, 去 Hutool)
 *
 * @author 芋道源码
 */
class UserIdempotentKeyResolver : IdempotentKeyResolver {
    override fun resolver(joinPoint: JoinPoint, idempotent: Idempotent): String {
        val methodName = joinPoint.signature.toString()
        val argsStr = StrUtils.joinMethodArgs(joinPoint)
        val userId = WebFrameworkUtils.getLoginUserId()
        val userType = WebFrameworkUtils.getLoginUserType()
        return StrUtils.md5(methodName + argsStr + userId + userType)
    }
}
