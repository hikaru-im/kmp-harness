package im.hikaru.ruoyi.module.member.controller.admin.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 用户修改积分 Request VO")
class MemberUserUpdatePointReqVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23788")
    @field:NotNull(message = "用户编号不能为空")
    var id: Long? = null
    @field:Schema(description = "变动积分，正数为增加，负数为减少", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @field:NotNull(message = "变动积分不能为空")
    var point: Int? = null
}
