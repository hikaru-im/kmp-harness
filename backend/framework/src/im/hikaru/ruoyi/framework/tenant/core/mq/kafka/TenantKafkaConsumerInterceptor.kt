package im.hikaru.ruoyi.framework.tenant.core.mq.kafka

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.RecordInterceptor

class TenantKafkaConsumerInterceptor : RecordInterceptor<Any, Any> {

    private val previousContext = ThreadLocal<PreviousContext>()

    override fun intercept(
        record: ConsumerRecord<Any, Any>,
        consumer: Consumer<Any, Any>,
    ): ConsumerRecord<Any, Any> {
        previousContext.set(PreviousContext(TenantContextHolder.getTenantId(), TenantContextHolder.isIgnore()))
        val header = record.headers().lastHeader(WebFrameworkUtils.HEADER_TENANT_ID)
        val tenantId = header?.value()?.decodeToString()?.takeIf(String::isNotBlank)?.toLongOrNull()
        TenantContextHolder.setTenantId(tenantId)
        TenantContextHolder.setIgnore(false)
        return record
    }

    override fun afterRecord(record: ConsumerRecord<Any, Any>, consumer: Consumer<Any, Any>) {
        val previous = previousContext.get()
        if (previous == null) {
            TenantContextHolder.clear()
        } else {
            TenantContextHolder.setTenantId(previous.tenantId)
            TenantContextHolder.setIgnore(previous.ignore)
            previousContext.remove()
        }
    }

    private data class PreviousContext(val tenantId: Long?, val ignore: Boolean)
}
