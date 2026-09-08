package im.hikaru.ruoyi.framework.tenant.core.mq.rabbitmq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.amqp.core.Message

class TenantRabbitMQMessageInterceptor : MethodInterceptor {

    override fun invoke(invocation: MethodInvocation): Any? {
        val tenantId = invocation.arguments
            .asSequence()
            .mapNotNull(::findMessage)
            .mapNotNull(::parseTenantId)
            .firstOrNull()
            ?: return invocation.proceed()
        val oldTenantId = TenantContextHolder.getTenantId()
        val oldIgnore = TenantContextHolder.isIgnore()
        return try {
            TenantContextHolder.setTenantId(tenantId)
            TenantContextHolder.setIgnore(false)
            invocation.proceed()
        } finally {
            TenantContextHolder.setTenantId(oldTenantId)
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    private fun findMessage(value: Any?): Message? = when (value) {
        is Message -> value
        is Collection<*> -> value.filterIsInstance<Message>().singleOrNull()
        else -> null
    }

    private fun parseTenantId(message: Message): Long? {
        val value = message.messageProperties.headers[WebFrameworkUtils.HEADER_TENANT_ID] ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLong()
            is ByteArray -> value.decodeToString().toLong()
            else -> error("Unsupported tenant header type: ${value.javaClass.name}")
        }
    }
}
