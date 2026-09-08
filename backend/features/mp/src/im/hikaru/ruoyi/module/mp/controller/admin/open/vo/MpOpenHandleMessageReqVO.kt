package im.hikaru.ruoyi.module.mp.controller.admin.open.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "管理后台 - 公众号处理消息 Request VO")
class MpOpenHandleMessageReqVO {
    companion object {
        const val ENCRYPT_TYPE_AES = "aes"
    }

    @field:Schema(description = "微信加密签名", requiredMode = Schema.RequiredMode.REQUIRED, example = "490eb57f448b87bd5f20ccef58aa4de46aa1908e")
    @field:NotEmpty(message = "微信加密签名不能为空")
    var signature: String? = null
    @field:Schema(description = "时间戳", requiredMode = Schema.RequiredMode.REQUIRED, example = "1672587863")
    @field:NotEmpty(message = "时间戳不能为空")
    var timestamp: String? = null
    @field:Schema(description = "随机数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1827365808")
    @field:NotEmpty(message = "随机数不能为空")
    var nonce: String? = null
    @field:Schema(description = "粉丝 openid", requiredMode = Schema.RequiredMode.REQUIRED, example = "oz-Jdtyn-WGm4C4I5Z-nvBMO_ZfY")
    @field:NotEmpty(message = "粉丝 openid 不能为空")
    var openid: String? = null
    @field:Schema(description = "消息加密类型", example = "aes")
    var encrypt_type: String? = null
    @field:Schema(description = "微信签名", example = "QW5kcm9pZCBUaGUgQmFzZTY0IGlzIGEgZ2VuZXJhdGVkIHN0cmluZw==")
    var msg_signature: String? = null
}
