package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "管理后台 - 支付订单提交 Response VO")
open class PayOrderSubmitRespVO {
    @field:Schema(description = "支付状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var status: Int? = null
    @field:Schema(description = "展示模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "url")
    var displayMode: String? = null
    @field:Schema(description = "展示内容", requiredMode = Schema.RequiredMode.REQUIRED)
    var displayContent: String? = null
}
