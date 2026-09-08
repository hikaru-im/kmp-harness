package im.hikaru.ruoyi.framework.tenant.core.mq.rocketmq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.apache.rocketmq.client.hook.ConsumeMessageContext
import org.apache.rocketmq.client.hook.ConsumeMessageHook

class TenantRocketMQConsumeMessageHook : ConsumeMessageHook {

    override fun hookName(): String = javaClass.simpleName

    override fun consumeMessageBefore(context: ConsumeMessageContext) {
        val messages = context.msgList
        require(messages.size == 1) { "Expected one RocketMQ message, but got ${messages.size}" }
        val tenantId = messages.single().getUserProperty(WebFrameworkUtils.HEADER_TENANT_ID)
        if (!tenantId.isNullOrBlank()) {
            TenantContextHolder.setTenantId(tenantId.toLong())
        }
    }

    override fun consumeMessageAfter(context: ConsumeMessageContext) {
        TenantContextHolder.clear()
    }
}
