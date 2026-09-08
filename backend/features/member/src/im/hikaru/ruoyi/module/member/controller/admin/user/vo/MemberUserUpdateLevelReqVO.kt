package im.hikaru.ruoyi.module.member.controller.admin.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 用户修改等级 Request VO")
class MemberUserUpdateLevelReqVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23788")
    @field:NotNull(message = "用户编号不能为空")
    var id: Long? = null
    @field:Schema(description = "用户等级编号", example = "1")
    var levelId: Long? = null
    @field:Schema(description = "修改原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "推广需要")
    @field:NotBlank(message = "修改原因不能为空")
    var reason: String? = null
}
