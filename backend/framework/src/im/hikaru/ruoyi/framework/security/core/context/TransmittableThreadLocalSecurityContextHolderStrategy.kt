package im.hikaru.ruoyi.framework.security.core.context

import com.alibaba.ttl.TransmittableThreadLocal
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolderStrategy
import org.springframework.security.core.context.SecurityContextImpl

/**
 * Security context strategy backed by [TransmittableThreadLocal].
 */
class TransmittableThreadLocalSecurityContextHolderStrategy : SecurityContextHolderStrategy {

    override fun clearContext() {
        contextHolder.remove()
    }

    override fun getContext(): SecurityContext =
        contextHolder.get() ?: createEmptyContext().also(contextHolder::set)

    override fun setContext(context: SecurityContext) {
        requireNotNull(context) { "Only non-null SecurityContext instances are permitted" }
        contextHolder.set(context)
    }

    override fun createEmptyContext(): SecurityContext = SecurityContextImpl()

    private companion object {
        val contextHolder: ThreadLocal<SecurityContext> = TransmittableThreadLocal()
    }
}
