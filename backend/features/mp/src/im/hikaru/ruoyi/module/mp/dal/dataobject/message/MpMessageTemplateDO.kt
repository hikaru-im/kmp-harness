package im.hikaru.ruoyi.module.mp.dal.dataobject.message

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import kotlinx.datetime.LocalDateTime

class MpMessageTemplateDO : TenantBaseDO {
    var id: Long? = null
    var accountId: Long? = null
    var appId: String? = null
    var templateId: String? = null
    var title: String? = null
    var content: String? = null
    var example: String? = null
    var primaryIndustry: String? = null
    var deputyIndustry: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
