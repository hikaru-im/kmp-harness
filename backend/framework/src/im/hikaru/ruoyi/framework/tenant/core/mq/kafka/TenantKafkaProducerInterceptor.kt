package im.hikaru.ruoyi.framework.tenant.core.mq.kafka

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

class TenantKafkaProducerInterceptor : ProducerInterceptor<Any, Any> {

    override fun onSend(record: ProducerRecord<Any, Any>): ProducerRecord<Any, Any> {
        val tenantId = TenantContextHolder.getTenantId() ?: return record
        record.headers().add(WebFrameworkUtils.HEADER_TENANT_ID, tenantId.toString().toByteArray())
        return record
    }

    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) = Unit

    override fun close() = Unit

    override fun configure(configs: MutableMap<String, *>) = Unit
}
