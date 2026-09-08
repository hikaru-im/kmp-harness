package im.hikaru.ruoyi.module.member.controller.app.social.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "用户 APP - 社交用户 Response VO")
class AppSocialUserRespVO {
    @field:Schema(description = "社交用户的 openid", requiredMode = Schema.RequiredMode.REQUIRED, example = "IPRmJ0wvBptiPIlGEZiPewGwiEiE")
    var openid: String? = null
    @field:Schema(description = "社交用户的昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道源码")
    var nickname: String? = null
    @field:Schema(description = "社交用户的头像", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn/1.png")
    var avatar: String? = null
}
