package im.hikaru.ruoyi.module.member.dal.dataobject.level

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import kotlinx.datetime.LocalDateTime

class MemberLevelRecordDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var levelId: Long? = null
    var level: Int? = null
    var discountPercent: Int? = null
    var experience: Int? = null
    var userExperience: Int? = null
    var remark: String? = null
    var description: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
