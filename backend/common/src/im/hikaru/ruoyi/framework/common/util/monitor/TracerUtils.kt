package im.hikaru.ruoyi.framework.common.util.monitor

import org.apache.skywalking.apm.toolkit.trace.TraceContext

/** Access to the current SkyWalking trace identifier. */
object TracerUtils {

    @JvmStatic
    fun getTraceId(): String = TraceContext.traceId()
}
