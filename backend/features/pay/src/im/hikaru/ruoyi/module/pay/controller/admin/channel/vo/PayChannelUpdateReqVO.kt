package im.hikaru.ruoyi.module.pay.controller.admin.channel.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

@Schema(description = "管理后台 - 支付渠道 更新 Request VO")
class PayChannelUpdateReqVO : PayChannelBaseVO() {
    @field:Schema(description = "商户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "商户编号不能为空")
    var id: Long? = null
    @field:Schema(description = "渠道配置的json字符串")
    @field:NotBlank(message = "渠道配置不能为空")
    var config: String? = null
}
