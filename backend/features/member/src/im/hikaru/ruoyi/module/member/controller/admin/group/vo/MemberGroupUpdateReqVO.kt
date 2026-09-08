package im.hikaru.ruoyi.module.member.controller.admin.group.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 用户分组更新 Request VO")
class MemberGroupUpdateReqVO : MemberGroupBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20357")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
