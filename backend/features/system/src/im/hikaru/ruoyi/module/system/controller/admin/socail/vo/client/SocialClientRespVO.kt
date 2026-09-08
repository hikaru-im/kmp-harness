package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client

import java.time.LocalDateTime

class SocialClientRespVO {
    var id: Long? = null
    var name: String? = null
    var socialType: Int? = null
    var userType: Int? = null
    var clientId: String? = null
    var clientSecret: String? = null
    var agentId: String? = null
    var publicKey: String? = null
    var status: Int? = null
    var createTime: LocalDateTime? = null
}
