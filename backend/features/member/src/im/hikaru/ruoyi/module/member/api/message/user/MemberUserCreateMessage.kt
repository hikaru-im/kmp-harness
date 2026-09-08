package im.hikaru.ruoyi.module.member.api.message.user

import jakarta.validation.constraints.NotNull

class MemberUserCreateMessage {
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
}
