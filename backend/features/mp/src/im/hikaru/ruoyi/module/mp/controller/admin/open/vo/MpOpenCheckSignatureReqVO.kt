package im.hikaru.ruoyi.module.mp.controller.admin.open.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "管理后台 - 公众号校验签名 Request VO")
class MpOpenCheckSignatureReqVO {
    @field:Schema(description = "微信加密签名", requiredMode = Schema.RequiredMode.REQUIRED, example = "490eb57f448b87bd5f20ccef58aa4de46aa1908e")
    @field:NotEmpty(message = "微信加密签名不能为空")
    var signature: String? = null
    @field:Schema(description = "时间戳", requiredMode = Schema.RequiredMode.REQUIRED, example = "1672587863")
    @field:NotEmpty(message = "时间戳不能为空")
    var timestamp: String? = null
    @field:Schema(description = "随机数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1827365808")
    @field:NotEmpty(message = "随机数不能为空")
    var nonce: String? = null
    @field:Schema(description = "随机字符串", requiredMode = Schema.RequiredMode.REQUIRED, example = "2721154047828672511")
    @field:NotEmpty(message = "随机字符串不能为空")
    @SuppressWarnings("SpellCheckingInspection")
    var echostr: String? = null
}
