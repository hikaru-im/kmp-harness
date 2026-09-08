package im.hikaru.ruoyi.module.member.api.user.dto

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import java.time.LocalDateTime

class MemberUserRespDTO {
    var id: Long? = null
    var nickname: String? = null
    var status: Int? = null
    var avatar: String? = null
    var mobile: String? = null
    var email: String? = null
    var createTime: LocalDateTime? = null
    var levelId: Long? = null
    var point: Int? = null
}
