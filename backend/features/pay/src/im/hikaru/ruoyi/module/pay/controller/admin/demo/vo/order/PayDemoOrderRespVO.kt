package im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.order

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

class PayDemoOrderRespVO {
    @field:Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "23199")
    var userId: Long? = null
    @field:Schema(description = "商品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "17682")
    var spuId: Long? = null
    @field:Schema(description = "商家备注", example = "李四")
    var spuName: String? = null
    @field:Schema(description = "价格，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "30381")
    var price: Int? = null
    @field:Schema(description = "是否已支付", requiredMode = Schema.RequiredMode.REQUIRED)
    var payStatus: Boolean? = null
    @field:Schema(description = "支付订单编号", example = "16863")
    var payOrderId: Long? = null
    @field:Schema(description = "订单支付时间")
    var payTime: LocalDateTime? = null
    @field:Schema(description = "支付渠道", example = "alipay_qr")
    var payChannelCode: String? = null
    @field:Schema(description = "支付退款编号", example = "23366")
    var payRefundId: Long? = null
    @field:Schema(description = "退款金额，单位：分", requiredMode = Schema.RequiredMode.REQUIRED, example = "14039")
    var refundPrice: Int? = null
    @field:Schema(description = "退款时间")
    var refundTime: LocalDateTime? = null
    @field:Schema(description = "渠道 package 信息")
    var transferChannelPackageInfo: String? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
