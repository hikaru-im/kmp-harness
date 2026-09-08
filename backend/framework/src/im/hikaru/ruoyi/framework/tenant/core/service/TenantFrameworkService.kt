package im.hikaru.ruoyi.framework.tenant.core.service

/**
 * Tenant 框架 Service 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface TenantFrameworkService {

    /** 获得所有租户编号 */
    fun getTenantIds(): List<Long>

    /** 校验租户是否合法 */
    fun validTenant(id: Long)
}
