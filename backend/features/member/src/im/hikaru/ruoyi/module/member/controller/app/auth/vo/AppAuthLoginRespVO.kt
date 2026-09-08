package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "用户 APP - 登录 Response VO")
class AppAuthLoginRespVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var userId: Long? = null
    @field:Schema(description = "访问令牌", requiredMode = Schema.RequiredMode.REQUIRED, example = "happy")
    var accessToken: String? = null
    @field:Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED, example = "nice")
    var refreshToken: String? = null
    @field:Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var expiresTime: LocalDateTime? = null
    @field:Schema(description = "社交用户 openid", example = "qq768")
    var openid: String? = null
}
