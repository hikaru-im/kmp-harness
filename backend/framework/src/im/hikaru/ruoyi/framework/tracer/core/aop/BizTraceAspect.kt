package im.hikaru.ruoyi.framework.tracer.core.aop

import im.hikaru.ruoyi.framework.common.util.spring.SpringExpressionUtils
import im.hikaru.ruoyi.framework.tracer.core.annotation.BizTrace
import im.hikaru.ruoyi.framework.tracer.core.util.TracerFrameworkUtils
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory

/** Creates an OpenTracing span for methods annotated with [BizTrace]. */
@Aspect
class BizTraceAspect(
    private val tracer: Tracer,
) {

    @Around("@annotation(trace)")
    fun around(joinPoint: ProceedingJoinPoint, trace: BizTrace): Any? {
        val span = tracer.buildSpan(getOperationName(joinPoint, trace))
            .withTag(Tags.COMPONENT.key, "biz")
            .start()
        try {
            return joinPoint.proceed()
        } catch (throwable: Throwable) {
            TracerFrameworkUtils.onError(throwable, span)
            throw throwable
        } finally {
            setBizTags(span, joinPoint, trace)
            span.finish()
        }
    }

    private fun getOperationName(joinPoint: ProceedingJoinPoint, trace: BizTrace): String {
        if (trace.operationName.isNotEmpty()) return BIZ_OPERATION_NAME_PREFIX + trace.operationName
        return BIZ_OPERATION_NAME_PREFIX +
            joinPoint.signature.declaringType.simpleName + "/" + joinPoint.signature.name
    }

    private fun setBizTags(span: Span, joinPoint: ProceedingJoinPoint, trace: BizTrace) {
        try {
            val values = SpringExpressionUtils.parseExpressions(joinPoint, listOf(trace.type, trace.id))
            span.setTag(BizTrace.TYPE_TAG, values[trace.type]?.toString())
            span.setTag(BizTrace.ID_TAG, values[trace.id]?.toString())
        } catch (ex: Exception) {
            logger.error("[setBizTags][Failed to evaluate biz type and id]", ex)
        }
    }

    companion object {
        private const val BIZ_OPERATION_NAME_PREFIX = "Biz/"
        private val logger = LoggerFactory.getLogger(BizTraceAspect::class.java)
    }
}
