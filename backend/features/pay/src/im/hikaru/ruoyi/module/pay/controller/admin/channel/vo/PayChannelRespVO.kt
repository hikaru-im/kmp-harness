package im.hikaru.ruoyi.module.pay.controller.admin.channel.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 支付渠道 Response VO")
class PayChannelRespVO : PayChannelBaseVO() {
    @field:Schema(description = "商户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var createTime: LocalDateTime? = null
    @field:Schema(description = "渠道编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "alipay_pc")
    var code: String? = null
    @field:Schema(description = "配置", requiredMode = Schema.RequiredMode.REQUIRED)
    var config: String? = null
}
