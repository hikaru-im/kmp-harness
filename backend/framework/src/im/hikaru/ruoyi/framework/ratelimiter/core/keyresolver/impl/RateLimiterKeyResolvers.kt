package im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl

import im.hikaru.ruoyi.framework.common.util.string.StrUtils
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.ratelimiter.core.annotation.RateLimiter
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.aspectj.lang.JoinPoint
import java.lang.management.ManagementFactory
import java.net.InetAddress

/**
 * 默认（全局级别）限流 Key 解析器 (迁移自 Java, 去 Hutool)
 *
 * @author 芋道源码
 */
class DefaultRateLimiterKeyResolver : RateLimiterKeyResolver {
    override fun resolver(joinPoint: JoinPoint, rateLimiter: RateLimiter): String {
        val methodName = joinPoint.signature.toString()
        val argsStr = StrUtils.joinMethodArgs(joinPoint)
        return StrUtils.md5(methodName + argsStr)
    }
}

/**
 * 用户级别的限流 Key 解析器
 */
class UserRateLimiterKeyResolver : RateLimiterKeyResolver {
    override fun resolver(joinPoint: JoinPoint, rateLimiter: RateLimiter): String {
        val methodName = joinPoint.signature.toString()
        val argsStr = StrUtils.joinMethodArgs(joinPoint)
        val userId = WebFrameworkUtils.getLoginUserId()
        val userType = WebFrameworkUtils.getLoginUserType()
        return StrUtils.md5(methodName + argsStr + userId + userType)
    }
}

/**
 * IP 级别的限流 Key 解析器
 */
class ClientIpRateLimiterKeyResolver : RateLimiterKeyResolver {
    override fun resolver(joinPoint: JoinPoint, rateLimiter: RateLimiter): String {
        val methodName = joinPoint.signature.toString()
        val argsStr = StrUtils.joinMethodArgs(joinPoint)
        val clientIp = ServletUtils.getClientIP()
        return StrUtils.md5(methodName + argsStr + clientIp)
    }
}

/**
 * Server 节点级别的限流 Key 解析器
 *
 * 迁移说明：Hutool SystemUtil.getHostInfo/getCurrentPID → InetAddress + ManagementFactory
 */
class ServerNodeRateLimiterKeyResolver : RateLimiterKeyResolver {
    override fun resolver(joinPoint: JoinPoint, rateLimiter: RateLimiter): String {
        val methodName = joinPoint.signature.toString()
        val argsStr = StrUtils.joinMethodArgs(joinPoint)
        val hostAddress = InetAddress.getLocalHost().hostAddress
        val pid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        val serverNode = "$hostAddress@$pid"
        return StrUtils.md5(methodName + argsStr + serverNode)
    }
}

/**
 * 基于 Spring EL 表达式的限流 Key 解析器
 */
class ExpressionRateLimiterKeyResolver : RateLimiterKeyResolver {

    private val parameterNameDiscoverer = org.springframework.core.DefaultParameterNameDiscoverer()
    private val expressionParser = org.springframework.expression.spel.standard.SpelExpressionParser()

    override fun resolver(joinPoint: JoinPoint, rateLimiter: RateLimiter): String {
        val method = getMethod(joinPoint)
        val args = joinPoint.args
        val parameterNames = parameterNameDiscoverer.getParameterNames(method)
        val evaluationContext = org.springframework.expression.spel.support.StandardEvaluationContext()
        if (!parameterNames.isNullOrEmpty()) {
            for (i in parameterNames.indices) {
                evaluationContext.setVariable(parameterNames[i], args[i])
            }
        }
        val expression = expressionParser.parseExpression(rateLimiter.keyArg)
        return expression.getValue(evaluationContext, String::class.java)!!
    }

    companion object {
        private fun getMethod(point: JoinPoint): java.lang.reflect.Method {
            val signature = point.signature as org.aspectj.lang.reflect.MethodSignature
            val method = signature.method
            if (!method.declaringClass.isInterface) return method
            return try {
                point.target.javaClass.getDeclaredMethod(point.signature.name, *method.parameterTypes)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }
        }
    }
}
