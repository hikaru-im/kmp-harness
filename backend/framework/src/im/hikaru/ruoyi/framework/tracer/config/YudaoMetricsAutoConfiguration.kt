package im.hikaru.ruoyi.framework.tracer.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean

/**
 * Metrics 配置类 (迁移自 Java)
 *
 * @author 芋道源码
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistryCustomizer::class)
@ConditionalOnProperty(prefix = "yudao.metrics", value = ["enable"], matchIfMissing = true)
class YudaoMetricsAutoConfiguration {

    @Bean
    fun metricsCommonTags(@Value("\${spring.application.name}") applicationName: String):
        MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry: MeterRegistry ->
            registry.config().commonTags("application", applicationName)
        }
    }
}
