package im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

@Schema(description = "管理后台 - 签到规则更新 Request VO")
class MemberSignInConfigUpdateReqVO : MemberSignInConfigBaseVO() {
    @field:Schema(description = "规则自增主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "13653")
    @field:NotNull(message = "规则自增主键不能为空")
    var id: Long? = null
}
