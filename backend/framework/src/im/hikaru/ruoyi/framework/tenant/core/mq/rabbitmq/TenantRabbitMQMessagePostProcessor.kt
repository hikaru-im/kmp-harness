package im.hikaru.ruoyi.framework.tenant.core.mq.rabbitmq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor

/**
 * RabbitMQ 多租户 MessagePostProcessor (迁移自 Java)
 *
 * @author 芋道源码
 */
class TenantRabbitMQMessagePostProcessor : MessagePostProcessor {

    @Throws(AmqpException::class)
    override fun postProcessMessage(message: Message): Message {
        val tenantId = TenantContextHolder.getTenantId() ?: return message
        message.messageProperties.headers[WebFrameworkUtils.HEADER_TENANT_ID] = tenantId
        return message
    }
}
