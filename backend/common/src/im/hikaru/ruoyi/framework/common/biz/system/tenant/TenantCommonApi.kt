package im.hikaru.ruoyi.framework.common.biz.system.tenant

/**
 * 多租户的 API 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface TenantCommonApi {

    /**
     * 获得所有租户
     *
     * @return 租户编号数组
     */
    fun getTenantIdList(): List<Long>

    /**
     * 校验租户是否合法
     *
     * @param id 租户编号
     */
    fun validateTenant(id: Long)
}
