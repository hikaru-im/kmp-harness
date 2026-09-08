package im.hikaru.ruoyi.framework.tracer.core.aop

import im.hikaru.ruoyi.framework.tracer.core.annotation.BizTrace
import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BizTraceAspectTest {

    @Test
    fun `aspect evaluates business tags and uses custom operation name`() {
        val tracer = MockTracer()
        val method = SampleService::class.java.getDeclaredMethod("create", Long::class.javaPrimitiveType)
        val joinPoint = joinPoint(method, arrayOf(42L), result = "created")

        val result = BizTraceAspect(tracer).around(joinPoint, method.getAnnotation(BizTrace::class.java))

        assertEquals("created", result)
        val span = tracer.finishedSpans().single()
        assertEquals("Biz/create-order", span.operationName())
        assertEquals("biz", span.tags()[Tags.COMPONENT.key])
        assertEquals("order", span.tags()[BizTrace.TYPE_TAG])
        assertEquals("42", span.tags()[BizTrace.ID_TAG])
        assertFalse(span.tags().containsKey(Tags.ERROR.key))
    }

    @Test
    fun `aspect records and rethrows failures and uses default operation name`() {
        val tracer = MockTracer()
        val method = SampleService::class.java.getDeclaredMethod("fail", Long::class.javaPrimitiveType)
        val failure = IllegalStateException("boom")
        val joinPoint = joinPoint(method, arrayOf(7L), failure = failure)

        val thrown = assertThrows(IllegalStateException::class.java) {
            BizTraceAspect(tracer).around(joinPoint, method.getAnnotation(BizTrace::class.java))
        }

        assertEquals(failure, thrown)
        val span = tracer.finishedSpans().single()
        assertEquals("Biz/SampleService/fail", span.operationName())
        assertEquals(true, span.tags()[Tags.ERROR.key])
        assertTrue(span.logEntries().isNotEmpty())
        assertEquals("7", span.tags()[BizTrace.ID_TAG])
    }

    private fun joinPoint(
        method: java.lang.reflect.Method,
        args: Array<Any>,
        result: Any? = null,
        failure: Throwable? = null,
    ): ProceedingJoinPoint {
        val signature = Mockito.mock(MethodSignature::class.java)
        Mockito.`when`(signature.method).thenReturn(method)
        Mockito.`when`(signature.declaringType).thenReturn(method.declaringClass)
        Mockito.`when`(signature.name).thenReturn(method.name)

        val joinPoint = Mockito.mock(ProceedingJoinPoint::class.java)
        Mockito.`when`(joinPoint.signature).thenReturn(signature)
        Mockito.`when`(joinPoint.args).thenReturn(args)
        if (failure != null) {
            Mockito.`when`(joinPoint.proceed()).thenThrow(failure)
        } else {
            Mockito.`when`(joinPoint.proceed()).thenReturn(result)
        }
        return joinPoint
    }

    private class SampleService {
        @BizTrace(operationName = "create-order", type = "'order'", id = "#id")
        fun create(id: Long): String = id.toString()

        @BizTrace(type = "'order'", id = "#id")
        fun fail(id: Long): String = error(id)
    }
}
