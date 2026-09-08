package im.hikaru.ruoyi.module.member.dal.dataobject.point

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import kotlinx.datetime.LocalDateTime

class MemberPointRecordDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var bizId: String? = null
    var bizType: Int? = null
    var title: String? = null
    var description: String? = null
    var point: Int? = null
    var totalPoint: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
