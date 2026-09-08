package im.hikaru.ruoyi.module.member.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 会员标签更新 Request VO")
class MemberTagUpdateReqVO : MemberTagBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "907")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
