package im.hikaru.ruoyi.module.member.controller.app.signin.vo.config

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 App - 签到规则 Response VO")
class AppMemberSignInConfigRespVO {
    @field:Schema(description = "签到第 x 天", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    var day: Int? = null
    @field:Schema(description = "奖励积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var point: Int? = null
}
