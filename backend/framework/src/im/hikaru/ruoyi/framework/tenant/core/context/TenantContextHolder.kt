package im.hikaru.ruoyi.framework.tenant.core.context

import im.hikaru.ruoyi.framework.common.enums.DocumentEnum
import com.alibaba.ttl.TransmittableThreadLocal

/**
 * 多租户上下文 Holder (迁移自 Java, 保留 TransmittableThreadLocal)
 *
 * @author 芋道源码
 */
object TenantContextHolder {

    /** 当前租户编号 */
    private val TENANT_ID: ThreadLocal<Long> = TransmittableThreadLocal()

    /** 是否忽略租户 */
    private val IGNORE: ThreadLocal<Boolean> = TransmittableThreadLocal()

    @JvmStatic
    fun getTenantId(): Long? = TENANT_ID.get()

    @JvmStatic
    fun getRequiredTenantId(): Long {
        val tenantId = getTenantId()
        if (tenantId == null) {
            throw NullPointerException("TenantContextHolder 不存在租户编号！可参考文档：${DocumentEnum.TENANT.url}")
        }
        return tenantId
    }

    @JvmStatic
    fun setTenantId(tenantId: Long?) {
        TENANT_ID.set(tenantId)
    }

    @JvmStatic
    fun setIgnore(ignore: Boolean?) {
        IGNORE.set(ignore)
    }

    @JvmStatic
    fun isIgnore(): Boolean = java.lang.Boolean.TRUE == IGNORE.get()

    @JvmStatic
    fun clear() {
        TENANT_ID.remove()
        IGNORE.remove()
    }
}
