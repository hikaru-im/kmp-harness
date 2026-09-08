package im.hikaru.ruoyi.module.system.dal.dataobject.tenant

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class TenantDO : BaseEntity {
    companion object { const val PACKAGE_ID_SYSTEM = 0L }
    var id: Long? = null; var name: String? = null; var contactUserId: Long? = null; var contactName: String? = null; var contactMobile: String? = null
    var status: Int? = null; var websites: List<String>? = null; var packageId: Long? = null; var expireTime: LocalDateTime? = null; var accountCount: Int? = null
    override var createTime: LocalDateTime? = null; override var updateTime: LocalDateTime? = null; override var creator: String? = null; override var updater: String? = null; override var deleted: Boolean = false
}
