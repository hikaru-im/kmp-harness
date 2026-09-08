package im.hikaru.ruoyi.module.system.dal.dataobject.notify

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

abstract class NotifyBaseEntity : BaseEntity {
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}

class NotifyTemplateDO : NotifyBaseEntity() {
    var id: Long? = null
    var name: String? = null
    var code: String? = null
    var type: Int? = null
    var nickname: String? = null
    var content: String? = null
    var params: List<String>? = null
    var status: Int? = null
    var remark: String? = null
}

class NotifyMessageDO : NotifyBaseEntity(), TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var userType: Int? = null
    var templateId: Long? = null
    var templateCode: String? = null
    var templateType: Int? = null
    var templateNickname: String? = null
    var templateContent: String? = null
    var templateParams: Map<String, Any?>? = null
    var readStatus: Boolean? = null
    var readTime: LocalDateTime? = null
    override var tenantId: Long? = null
}
