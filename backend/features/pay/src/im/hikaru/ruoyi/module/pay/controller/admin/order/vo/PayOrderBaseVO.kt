package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

open class PayOrderBaseVO {
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "应用编号不能为空")
    var appId: Long? = null
    @field:Schema(description = "渠道编号", example = "2048")
    var channelId: Long? = null
    @field:Schema(description = "渠道编码", example = "wx_app")
    var channelCode: String? = null
    @field:Schema(description = "商户订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "888")
    @field:NotNull(message = "商户订单编号不能为空")
    var merchantOrderId: String? = null
    @field:Schema(description = "商品标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "土豆")
    @field:NotNull(message = "商品标题不能为空")
    var subject: String? = null
    @field:Schema(description = "商品描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "我是土豆")
    @field:NotNull(message = "商品描述不能为空")
    var body: String? = null
    @field:Schema(description = "异步通知地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "http://127.0.0.1:48080/pay/notify")
    @field:NotNull(message = "异步通知地址不能为空")
    var notifyUrl: String? = null
    @field:Schema(description = "支付金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:NotNull(message = "支付金额，单位：分不能为空")
    var price: Long? = null
    @field:Schema(description = "渠道手续费，单位：百分比", example = "10")
    var channelFeeRate: Double? = null
    @field:Schema(description = "渠道手续金额，单位：分", example = "100")
    var channelFeePrice: Int? = null
    @field:Schema(description = "支付状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "支付状态不能为空")
    var status: Int? = null
    @field:Schema(description = "用户 IP", requiredMode = Schema.RequiredMode.REQUIRED, example = "127.0.0.1")
    @field:NotNull(message = "用户 IP不能为空")
    var userIp: String? = null
    @field:Schema(description = "订单失效时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "订单失效时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var expireTime: LocalDateTime? = null
    @field:Schema(description = "订单支付成功时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var successTime: LocalDateTime? = null
    @field:Schema(description = "支付成功的订单拓展单编号", example = "50")
    var extensionId: Long? = null
    @field:Schema(description = "支付订单号", example = "2048888")
    var no: String? = null
    @field:Schema(description = "退款总金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @field:NotNull(message = "退款总金额，单位：分不能为空")
    var refundPrice: Long? = null
    @field:Schema(description = "渠道用户编号", example = "2048")
    var channelUserId: String? = null
    @field:Schema(description = "渠道订单号", example = "4096")
    var channelOrderNo: String? = null
}
