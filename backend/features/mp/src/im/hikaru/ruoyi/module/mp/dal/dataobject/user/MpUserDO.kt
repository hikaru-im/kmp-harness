package im.hikaru.ruoyi.module.mp.dal.dataobject.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.tag.MpTagDO
import kotlinx.datetime.LocalDateTime

class MpUserDO : TenantBaseDO {
    var id: Long? = null
    var openid: String? = null
    var unionId: String? = null
    var subscribeStatus: Int? = null
    var subscribeTime: LocalDateTime? = null
    var unsubscribeTime: LocalDateTime? = null
    var nickname: String? = null
    var headImageUrl: String? = null
    var language: String? = null
    var country: String? = null
    var province: String? = null
    var city: String? = null
    var remark: String? = null
    var tagIds: List<Long>? = null
    var accountId: Long? = null
    var appId: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
