package im.hikaru.ruoyi.framework.tenant.core.mq.rocketmq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.apache.rocketmq.client.hook.SendMessageContext
import org.apache.rocketmq.client.hook.SendMessageHook

class TenantRocketMQSendMessageHook : SendMessageHook {

    override fun hookName(): String = javaClass.simpleName

    override fun sendMessageBefore(context: SendMessageContext) {
        val tenantId = TenantContextHolder.getTenantId() ?: return
        context.message.putUserProperty(WebFrameworkUtils.HEADER_TENANT_ID, tenantId.toString())
    }

    override fun sendMessageAfter(context: SendMessageContext) = Unit
}
