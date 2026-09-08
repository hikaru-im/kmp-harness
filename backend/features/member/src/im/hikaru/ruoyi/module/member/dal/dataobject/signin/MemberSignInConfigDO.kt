package im.hikaru.ruoyi.module.member.dal.dataobject.signin

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class MemberSignInConfigDO : TenantBaseDO {
    var id: Long? = null
    var day: Int? = null
    var point: Int? = null
    var experience: Int? = null
    var status: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
