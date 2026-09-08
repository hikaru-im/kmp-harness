package im.hikaru.ruoyi.module.pay.controller.admin.refund.vo

import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 退款订单 Excel 导出 Request VO，参数和 PayRefundPageReqVO 是一致的")
class PayRefundExportReqVO {
    @field:Schema(description = "应用编号", example = "1024")
    var appId: Long? = null
    @field:Schema(description = "渠道编码", example = "wx_app")
    var channelCode: String? = null
    @field:Schema(description = "商户支付单号", example = "10")
    var merchantOrderId: String? = null
    @field:Schema(description = "商户退款单号", example = "20")
    var merchantRefundId: String? = null
    @field:Schema(description = "渠道支付单号", example = "30")
    var channelOrderNo: String? = null
    @field:Schema(description = "渠道退款单号", example = "40")
    var channelRefundNo: String? = null
    @field:Schema(description = "退款状态", example = "0")
    var status: Int? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @field:Schema(description = "创建时间")
    var createTime: Array<LocalDateTime>? = null
}
