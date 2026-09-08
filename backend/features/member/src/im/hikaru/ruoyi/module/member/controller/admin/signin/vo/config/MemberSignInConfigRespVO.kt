package im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 签到规则 Response VO")
class MemberSignInConfigRespVO : MemberSignInConfigBaseVO() {
    @field:Schema(description = "自增主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "20937")
    var id: Int? = null
    @field:Schema(description = "create time")
    var createTime: LocalDateTime? = null
}
