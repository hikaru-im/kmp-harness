package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "用户 APP - 微信小程序手机登录 Request VO")
class AppAuthWeixinMiniAppLoginReqVO {
    @field:Schema(description = "手机 code，小程序通过 wx.getPhoneNumber 方法获得", requiredMode = Schema.RequiredMode.REQUIRED, example = "hello")
    @field:NotEmpty(message = "手机 code 不能为空")
    var phoneCode: String? = null
    @field:Schema(description = "登录 code，小程序通过 wx.login 方法获得", requiredMode = Schema.RequiredMode.REQUIRED, example = "word")
    @field:NotEmpty(message = "登录 code 不能为空")
    var loginCode: String? = null
    @field:Schema(description = "state", requiredMode = Schema.RequiredMode.REQUIRED, example = "9b2ffbc1-7425-4155-9894-9d5c08541d62")
    @field:NotEmpty(message = "state 不能为空")
    var state: String? = null
}
