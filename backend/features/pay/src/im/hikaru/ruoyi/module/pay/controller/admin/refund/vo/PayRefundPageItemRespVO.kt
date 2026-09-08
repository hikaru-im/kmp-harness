package im.hikaru.ruoyi.module.pay.controller.admin.refund.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 退款订单分页查询 Response VO")
class PayRefundPageItemRespVO : PayRefundBaseVO() {
    @field:Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "我是芋艿")
    var appName: String? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
