package im.hikaru.ruoyi.module.member.controller.admin.user.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 会员用户更新 Request VO")
class MemberUserUpdateReqVO : MemberUserBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23788")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
