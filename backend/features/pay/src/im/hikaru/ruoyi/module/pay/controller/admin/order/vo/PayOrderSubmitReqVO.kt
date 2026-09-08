package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.URL

@Schema(description = "管理后台 - 支付订单提交 Request VO")
open class PayOrderSubmitReqVO {
    @field:Schema(description = "支付单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "支付单编号不能为空")
    var id: Long? = null
    @field:Schema(description = "支付渠道", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx_pub")
    @field:NotEmpty(message = "支付渠道不能为空")
    var channelCode: String? = null
    @field:Schema(description = "支付渠道的额外参数，例如说，微信公众号需要传递 openid 参数")
    var channelExtras: Map<String, String>? = null
    @field:Schema(description = "展示模式", example = "url")
    var displayMode: String? = null
    @field:Schema(description = "回跳地址")
    @URL(message = "回跳地址的格式必须是 URL")
    var returnUrl: String? = null
}
