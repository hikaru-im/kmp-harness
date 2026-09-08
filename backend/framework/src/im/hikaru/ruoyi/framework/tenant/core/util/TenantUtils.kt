package im.hikaru.ruoyi.framework.tenant.core.util

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import java.util.concurrent.Callable

/**
 * 多租户 Util (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
object TenantUtils {

    @JvmStatic
    fun execute(tenantId: Long?, runnable: Runnable) {
        val oldTenantId = TenantContextHolder.getTenantId()
        val oldIgnore = TenantContextHolder.isIgnore()
        try {
            TenantContextHolder.setTenantId(tenantId)
            TenantContextHolder.setIgnore(false)
            runnable.run()
        } finally {
            TenantContextHolder.setTenantId(oldTenantId)
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    @JvmStatic
    fun <V> execute(tenantId: Long?, callable: Callable<V>): V {
        val oldTenantId = TenantContextHolder.getTenantId()
        val oldIgnore = TenantContextHolder.isIgnore()
        try {
            TenantContextHolder.setTenantId(tenantId)
            TenantContextHolder.setIgnore(false)
            return callable.call()
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            TenantContextHolder.setTenantId(oldTenantId)
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    @JvmStatic
    fun executeIgnore(runnable: Runnable) {
        val oldIgnore = TenantContextHolder.isIgnore()
        try {
            TenantContextHolder.setIgnore(true)
            runnable.run()
        } finally {
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    @JvmStatic
    fun <V> executeIgnore(callable: Callable<V>): V {
        val oldIgnore = TenantContextHolder.isIgnore()
        try {
            TenantContextHolder.setIgnore(true)
            return callable.call()
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    @JvmStatic
    fun addTenantHeader(headers: MutableMap<String, String>, tenantId: Long?) {
        if (tenantId != null) {
            headers[WebFrameworkUtils.HEADER_TENANT_ID] = tenantId.toString()
        }
    }
}
