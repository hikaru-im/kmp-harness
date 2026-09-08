package im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl

import im.hikaru.ruoyi.framework.idempotent.core.annotation.Idempotent
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.IdempotentKeyResolver
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import java.lang.reflect.Method

/**
 * 基于 Spring EL 表达式的幂等 Key 解析器 (迁移自 Java, 去 Hutool ArrayUtil)
 *
 * @author 芋道源码
 */
class ExpressionIdempotentKeyResolver : IdempotentKeyResolver {

    private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()
    private val expressionParser: ExpressionParser = SpelExpressionParser()

    override fun resolver(joinPoint: JoinPoint, idempotent: Idempotent): String {
        val method = getMethod(joinPoint)
        val args = joinPoint.args
        val parameterNames = parameterNameDiscoverer.getParameterNames(method)
        val evaluationContext = StandardEvaluationContext()
        if (!parameterNames.isNullOrEmpty()) {
            for (i in parameterNames.indices) {
                evaluationContext.setVariable(parameterNames[i], args[i])
            }
        }
        val expression = expressionParser.parseExpression(idempotent.keyArg)
        return expression.getValue(evaluationContext, String::class.java)!!
    }

    companion object {
        private fun getMethod(point: JoinPoint): Method {
            val signature = point.signature as MethodSignature
            val method = signature.method
            if (!method.declaringClass.isInterface) {
                return method
            }
            return try {
                point.target.javaClass.getDeclaredMethod(
                    point.signature.name, *method.parameterTypes,
                )
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }
        }
    }
}
