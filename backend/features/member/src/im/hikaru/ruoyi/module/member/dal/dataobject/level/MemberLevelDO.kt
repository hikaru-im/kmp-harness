package im.hikaru.ruoyi.module.member.dal.dataobject.level

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class MemberLevelDO : TenantBaseDO {
    var id: Long? = null
    var name: String? = null
    var level: Int? = null
    var experience: Int? = null
    var discountPercent: Int? = null
    var icon: String? = null
    var backgroundUrl: String? = null
    var status: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
