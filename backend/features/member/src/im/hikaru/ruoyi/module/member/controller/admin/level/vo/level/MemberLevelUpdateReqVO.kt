package im.hikaru.ruoyi.module.member.controller.admin.level.vo.level

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 会员等级更新 Request VO")
class MemberLevelUpdateReqVO : MemberLevelBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6103")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
