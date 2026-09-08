package im.hikaru.ruoyi.module.pay.controller.admin.refund.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

open class PayRefundBaseVO {
    @field:Schema(description = "外部退款号", requiredMode = Schema.RequiredMode.REQUIRED, example = "110")
    var no: String? = null
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var appId: Long? = null
    @field:Schema(description = "渠道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    var channelId: Long? = null
    @field:Schema(description = "渠道编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx_app")
    var channelCode: String? = null
    @field:Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var orderId: Long? = null
    @field:Schema(description = "商户订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "225")
    var merchantOrderId: String? = null
    @field:Schema(description = "商户退款订单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "512")
    var merchantRefundId: String? = null
    @field:Schema(description = "异步通知地址", requiredMode = Schema.RequiredMode.REQUIRED)
    var notifyUrl: String? = null
    @field:Schema(description = "退款状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    var status: Int? = null
    @field:Schema(description = "支付金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var payPrice: Long? = null
    @field:Schema(description = "退款金额,单位分", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    var refundPrice: Long? = null
    @field:Schema(description = "退款原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "我要退了")
    var reason: String? = null
    @field:Schema(description = "用户 IP", requiredMode = Schema.RequiredMode.REQUIRED, example = "127.0.0.1")
    var userIp: String? = null
    @field:Schema(description = "渠道订单号", requiredMode = Schema.RequiredMode.REQUIRED, example = "233")
    var channelOrderNo: String? = null
    @field:Schema(description = "渠道退款单号", example = "2022")
    var channelRefundNo: String? = null
    @field:Schema(description = "退款成功时间")
    var successTime: LocalDateTime? = null
    @field:Schema(description = "调用渠道的错误码")
    var channelErrorCode: String? = null
    @field:Schema(description = "调用渠道的错误提示")
    var channelErrorMsg: String? = null
    @field:Schema(description = "支付渠道的额外参数")
    var channelNotifyData: String? = null
}
