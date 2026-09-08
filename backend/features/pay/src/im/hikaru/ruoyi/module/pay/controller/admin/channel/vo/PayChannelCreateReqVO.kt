package im.hikaru.ruoyi.module.pay.controller.admin.channel.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 支付渠道 创建 Request VO")
class PayChannelCreateReqVO : PayChannelBaseVO() {
    @field:Schema(description = "渠道编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "alipay_pc")
    @field:NotNull(message = "渠道编码不能为空")
    var code: String? = null
    @field:Schema(description = "渠道配置的 json 字符串")
    @field:NotBlank(message = "渠道配置不能为空")
    var config: String? = null
}
