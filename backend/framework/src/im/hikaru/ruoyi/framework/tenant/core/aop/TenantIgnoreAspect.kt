package im.hikaru.ruoyi.framework.tenant.core.aop

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser

/**
 * 忽略多租户的 Aspect，基于 [TenantIgnore] 注解实现 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@Aspect
class TenantIgnoreAspect {

    @Around("@annotation(tenantIgnore)")
    fun around(joinPoint: ProceedingJoinPoint, tenantIgnore: TenantIgnore): Any? {
        val oldIgnore = TenantContextHolder.isIgnore()
        try {
            val enable = parseExpression(tenantIgnore.enable)
            if (java.lang.Boolean.TRUE == enable) {
                TenantContextHolder.setIgnore(true)
            }
            return joinPoint.proceed()
        } finally {
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    private fun parseExpression(expr: String): java.lang.Boolean? = try {
        PARSER.parseExpression(expr).getValue(java.lang.Boolean::class.java)
    } catch (e: Exception) {
        log.warn("[parseExpression][表达式({}) 解析失败]", expr, e)
        null
    }

    companion object {
        private val log = LoggerFactory.getLogger(TenantIgnoreAspect::class.java)
        private val PARSER: ExpressionParser = SpelExpressionParser()
    }
}
