package im.hikaru.ruoyi.framework.common.util.spring

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.expression.BeanFactoryResolver
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

/** Utilities for evaluating Spring EL expressions used by framework annotations. */
@Component
class SpringExpressionUtils : ApplicationContextAware {

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        Companion.applicationContext = applicationContext
    }

    companion object {
        private val expressionParser = SpelExpressionParser()
        private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()

        @Volatile
        private var applicationContext: ApplicationContext? = null

        @JvmStatic
        fun parseExpression(joinPoint: JoinPoint, expressionString: String): Any? =
            parseExpressions(joinPoint, listOf(expressionString))[expressionString]

        @JvmStatic
        fun parseExpressions(joinPoint: JoinPoint, expressionStrings: Collection<String>): Map<String, Any?> {
            if (expressionStrings.isEmpty()) return emptyMap()

            val signature = joinPoint.signature as MethodSignature
            val parameterNames = parameterNameDiscoverer.getParameterNames(signature.method).orEmpty()
            val context = StandardEvaluationContext()
            parameterNames.forEachIndexed { index, name ->
                context.setVariable(name, joinPoint.args[index])
            }
            return expressionStrings.associateWith { expressionParser.parseExpression(it).getValue(context) }
        }

        @JvmStatic
        fun parseExpression(expressionString: String): Any? = parseExpression(expressionString, null)

        @JvmStatic
        fun parseExpression(expressionString: String, variables: Map<String, Any?>?): Any? {
            if (expressionString.isBlank()) return null

            val context = StandardEvaluationContext()
            applicationContext?.let { context.setBeanResolver(BeanFactoryResolver(it)) }
            variables.orEmpty().forEach(context::setVariable)
            return expressionParser.parseExpression(expressionString).getValue(context)
        }
    }
}
