package im.hikaru.ruoyi.module.member.dal.dataobject.signin

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class MemberSignInRecordDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var day: Int? = null
    var point: Int? = null
    var experience: Int? = null
    var signDate: LocalDate? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
