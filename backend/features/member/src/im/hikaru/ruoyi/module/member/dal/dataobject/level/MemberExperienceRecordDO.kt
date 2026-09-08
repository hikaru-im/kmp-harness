package im.hikaru.ruoyi.module.member.dal.dataobject.level

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import kotlinx.datetime.LocalDateTime

class MemberExperienceRecordDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var bizType: Int? = null
    var bizId: String? = null
    var title: String? = null
    var description: String? = null
    var experience: Int? = null
    var totalExperience: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
