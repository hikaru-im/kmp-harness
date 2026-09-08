package im.hikaru.ruoyi.module.pay.controller.admin.refund.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 退款订单详情 Response VO")
class PayRefundDetailsRespVO : PayRefundBaseVO() {
    @field:Schema(description = "支付退款编号", requiredMode = Schema.RequiredMode.REQUIRED)
    var id: Long? = null
    @field:Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "我是芋艿")
    var appName: String? = null
    @field:Schema(description = "支付订单", requiredMode = Schema.RequiredMode.REQUIRED)
    var order: Order? = null
    @field:Schema(description = "创建时间")
    var createTime: LocalDateTime? = null
    @field:Schema(description = "更新时间")
    var updateTime: LocalDateTime? = null

    @Schema(description = "管理后台 - 支付订单")
    class Order {
        @field:Schema(description = "商品标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "土豆")
        var subject: String? = null
    }
}
