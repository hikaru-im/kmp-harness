package im.hikaru.ruoyi.module.system.dal.dataobject.social

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

abstract class SocialBaseEntity : BaseEntity, TenantBaseDO {
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

class SocialClientDO : SocialBaseEntity() {
    var id: Long? = null
    var name: String? = null
    var socialType: Int? = null
    var userType: Int? = null
    var clientId: String? = null
    var clientSecret: String? = null
    var agentId: String? = null
    var publicKey: String? = null
    var status: Int? = null
}

class SocialUserDO : SocialBaseEntity() {
    var id: Long? = null
    var type: Int? = null
    var openid: String? = null
    var token: String? = null
    var rawTokenInfo: String? = null
    var nickname: String? = null
    var avatar: String? = null
    var rawUserInfo: String? = null
    var code: String? = null
    var state: String? = null
}

class SocialUserBindDO : SocialBaseEntity() {
    var id: Long? = null
    var userId: Long? = null
    var userType: Int? = null
    var socialUserId: Long? = null
    var socialType: Int? = null
}
