package im.hikaru.ruoyi.framework.tenant.core.mq.kafka

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment

class TenantKafkaEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        try {
            val interceptor = TenantKafkaProducerInterceptor::class.java.name
            val configured = environment.getProperty(PROPERTY_KEY_INTERCEPTOR_CLASSES)
            val value = if (configured.isNullOrBlank()) interceptor else "$configured,$interceptor"
            environment.systemProperties[PROPERTY_KEY_INTERCEPTOR_CLASSES] = value
        } catch (_: LinkageError) {
            // Kafka is an optional runtime integration.
        }
    }

    companion object {
        const val PROPERTY_KEY_INTERCEPTOR_CLASSES =
            "spring.kafka.producer.properties.interceptor.classes"
    }
}
