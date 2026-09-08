package im.hikaru.ruoyi.framework.tenant.core.db

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity

/**
 * 拓展多租户的 BaseDO 基类 (迁移自 Java)
 *
 * 迁移说明：原 extends MyBatis-Plus BaseDO → 改为实现 Exposed 的 [BaseEntity] 接口，
 * 持有 tenantId 字段 + 审计字段。
 *
 * @author 芋道源码
 */
interface TenantBaseDO : BaseEntity {

    /** 多租户编号 */
    var tenantId: Long?
}
