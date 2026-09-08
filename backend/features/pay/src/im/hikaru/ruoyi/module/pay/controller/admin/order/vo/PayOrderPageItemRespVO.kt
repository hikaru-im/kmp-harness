package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 支付订单分页 Request VO")
class PayOrderPageItemRespVO : PayOrderBaseVO() {
    @field:Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
    @field:Schema(description = "应用名称", example = "wx_pay")
    var appName: String? = null
}
