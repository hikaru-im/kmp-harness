package im.hikaru.ruoyi.module.member.controller.app.auth.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.framework.common.validation.Mobile
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "用户 APP - 发送手机验证码 Request VO")
class AppAuthSmsSendReqVO {
    @field:Schema(description = "手机号", example = "15601691234")
    @field:Mobile
    var mobile: String? = null
    @field:Schema(description = "发送场景,对应 SmsSceneEnum 枚举", example = "1")
    @field:NotNull(message = "发送场景不能为空")
    @field:InEnum(SmsSceneEnum::class)
    var scene: Int? = null
}
