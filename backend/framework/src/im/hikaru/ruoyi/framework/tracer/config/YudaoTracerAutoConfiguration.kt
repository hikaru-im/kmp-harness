package im.hikaru.ruoyi.framework.tracer.config

import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.tracer.core.aop.BizTraceAspect
import im.hikaru.ruoyi.framework.tracer.core.filter.TraceFilter
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import jakarta.servlet.Filter
import org.apache.skywalking.apm.toolkit.opentracing.SkywalkingTracer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(SkywalkingTracer::class, Filter::class)
@EnableConfigurationProperties(TracerProperties::class)
@ConditionalOnProperty(prefix = "yudao.tracer", value = ["enable"], matchIfMissing = true)
class YudaoTracerAutoConfiguration {

    @Bean
    fun tracer(): Tracer = SkywalkingTracer().also { tracer ->
        if (!GlobalTracer.isRegistered()) GlobalTracer.register(tracer)
    }

    @Bean
    fun bizTracingAop(tracer: Tracer): BizTraceAspect = BizTraceAspect(tracer)

    @Bean
    fun traceFilter(): FilterRegistrationBean<TraceFilter> =
        FilterRegistrationBean(TraceFilter()).apply {
            order = WebFilterOrderEnum.TRACE_FILTER
        }
}
