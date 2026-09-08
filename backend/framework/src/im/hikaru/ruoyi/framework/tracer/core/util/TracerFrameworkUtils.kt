package im.hikaru.ruoyi.framework.tracer.core.util

import io.opentracing.Span
import io.opentracing.tag.Tags
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 链路追踪 Util (迁移自 Java)
 *
 * @author 芋道源码
 */
object TracerFrameworkUtils {

    /**
     * 将异常记录到 Span 中，参考自 com.aliyuncs.utils.TraceUtils
     */
    @JvmStatic
    fun onError(throwable: Throwable?, span: Span) {
        Tags.ERROR.set(span, true)
        if (throwable != null) {
            span.log(errorLogs(throwable))
        }
    }

    private fun errorLogs(throwable: Throwable): Map<String, Any> {
        val errorLogs = HashMap<String, Any>(10)
        errorLogs["event"] = Tags.ERROR.key
        errorLogs["error.object"] = throwable
        errorLogs["error.kind"] = throwable.javaClass.name
        val message = throwable.cause?.message ?: throwable.message
        if (message != null) {
            errorLogs["message"] = message
        }
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        errorLogs["stack"] = sw.toString()
        return errorLogs
    }
}
