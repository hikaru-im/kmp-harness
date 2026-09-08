package im.hikaru.ruoyi.module.mp.dal.dataobject.account

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class MpAccountDO : TenantBaseDO {
    var id: Long? = null
    var name: String? = null
    var account: String? = null
    var appId: String? = null
    var url: String? = null
    var appSecret: String? = null
    var token: String? = null
    var aesKey: String? = null
    var qrCodeUrl: String? = null
    var remark: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}
