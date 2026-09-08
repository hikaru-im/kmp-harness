package im.hikaru.ruoyi.framework.tenant.core.mq.kafka

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory
import org.springframework.kafka.listener.CompositeRecordInterceptor
import org.springframework.kafka.listener.RecordInterceptor

class TenantKafkaInitializer : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is AbstractKafkaListenerContainerFactory<*, *, *>) {
            @Suppress("UNCHECKED_CAST")
            configure(bean as AbstractKafkaListenerContainerFactory<*, Any, Any>)
        }
        return bean
    }

    private fun configure(factory: AbstractKafkaListenerContainerFactory<*, Any, Any>) {
        val tenantInterceptor = TenantKafkaConsumerInterceptor()
        val existing = factory.recordInterceptor
        factory.setRecordInterceptor(
            if (existing == null) tenantInterceptor else composite(tenantInterceptor, existing),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun composite(
        tenantInterceptor: RecordInterceptor<Any, Any>,
        existing: RecordInterceptor<Any, Any>,
    ): RecordInterceptor<Any, Any> = CompositeRecordInterceptor(tenantInterceptor, existing)
}
