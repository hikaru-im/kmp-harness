package im.hikaru.ruoyi.module.pay.controller.admin.refund.vo

import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.framework.excel.core.convert.MoneyConvert
import im.hikaru.ruoyi.module.pay.enums.DictTypeConstants
import java.time.LocalDateTime

class PayRefundExcelVO {
    @field:ExcelProperty("支付退款编号")
    var id: Long? = null
    @field:ExcelProperty("创建时间")
    var createTime: LocalDateTime? = null
    @field:ExcelProperty("支付金额", converter = MoneyConvert::class)
    var payPrice: Int? = null
    @field:ExcelProperty("退款金额", converter = MoneyConvert::class)
    var refundPrice: Int? = null
    @field:ExcelProperty("商户退款单号")
    var merchantRefundId: String? = null
    @field:ExcelProperty("退款单号")
    var no: String? = null
    @field:ExcelProperty("渠道退款单号")
    var channelRefundNo: String? = null
    @field:ExcelProperty("商户支付单号")
    var merchantOrderId: String? = null
    @field:ExcelProperty("渠道支付单号")
    var channelOrderNo: String? = null
    @field:ExcelProperty("退款状态", converter = DictConvert::class)
    @DictFormat(DictTypeConstants.REFUND_STATUS)
    var status: Int? = null
    @field:ExcelProperty("退款渠道", converter = DictConvert::class)
    @DictFormat(DictTypeConstants.CHANNEL_CODE)
    var channelCode: String? = null
    @field:ExcelProperty("成功时间")
    var successTime: LocalDateTime? = null
    @field:ExcelProperty("支付应用")
    var appName: String? = null
    @field:ExcelProperty("退款原因")
    var reason: String? = null
}
