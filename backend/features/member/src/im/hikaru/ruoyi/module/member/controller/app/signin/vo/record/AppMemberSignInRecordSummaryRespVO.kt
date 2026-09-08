package im.hikaru.ruoyi.module.member.controller.app.signin.vo.record

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 App - 个人签到统计 Response VO")
class AppMemberSignInRecordSummaryRespVO {
    @field:Schema(description = "总签到天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var totalDay: Int? = null
    @field:Schema(description = "连续签到第 x 天", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    var continuousDay: Int? = null
    @field:Schema(description = "今天是否已签到", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    var todaySignIn: Boolean? = null
}
