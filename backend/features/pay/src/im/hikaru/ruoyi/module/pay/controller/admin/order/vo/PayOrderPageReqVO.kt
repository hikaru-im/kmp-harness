package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 支付订单分页 Request VO")
class PayOrderPageReqVO : PageParam() {
    @field:Schema(description = "应用编号", example = "1024")
    var appId: Long? = null
    @field:Schema(description = "渠道编码", example = "wx_app")
    var channelCode: String? = null
    @field:Schema(description = "商户订单编号", example = "4096")
    var merchantOrderId: String? = null
    @field:Schema(description = "渠道编号", example = "1888")
    var channelOrderNo: String? = null
    @field:Schema(description = "支付单号", example = "2014888")
    var no: String? = null
    @field:Schema(description = "支付状态", example = "0")
    var status: Int? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @field:Schema(description = "创建时间")
    var createTime: Array<LocalDateTime>? = null
}
