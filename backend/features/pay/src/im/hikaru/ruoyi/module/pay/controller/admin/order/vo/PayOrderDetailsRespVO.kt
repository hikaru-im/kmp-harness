package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 支付订单详细信息 Response VO")
class PayOrderDetailsRespVO : PayOrderBaseVO() {
    @field:Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道源码")
    var appName: String? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
    @field:Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var updateTime: LocalDateTime? = null
    var extension: PayOrderExtension? = null

    @Schema(description = "支付订单扩展")
    class PayOrderExtension {
        @field:Schema(description = "支付订单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        var no: String? = null
        @field:Schema(description = "支付异步通知的内容")
        var channelNotifyData: String? = null
    }
}
