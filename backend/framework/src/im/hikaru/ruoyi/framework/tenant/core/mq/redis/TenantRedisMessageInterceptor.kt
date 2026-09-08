package im.hikaru.ruoyi.framework.tenant.core.mq.redis

import im.hikaru.ruoyi.framework.mq.redis.core.interceptor.RedisMessageInterceptor
import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils

/**
 * 多租户 [AbstractRedisMessage] 拦截器 (迁移自 Java, 去 Hutool)
 *
 * @author 芋道源码
 */
class TenantRedisMessageInterceptor : RedisMessageInterceptor {

    override fun sendMessageBefore(message: AbstractRedisMessage) {
        val tenantId = TenantContextHolder.getTenantId() ?: return
        message.addHeader(WebFrameworkUtils.HEADER_TENANT_ID, tenantId.toString())
    }

    override fun consumeMessageBefore(message: AbstractRedisMessage) {
        val tenantIdStr = message.getHeader(WebFrameworkUtils.HEADER_TENANT_ID)
        if (!tenantIdStr.isNullOrEmpty()) {
            TenantContextHolder.setTenantId(tenantIdStr.toLong())
        }
    }

    override fun consumeMessageAfter(message: AbstractRedisMessage) {
        TenantContextHolder.clear()
    }
}
