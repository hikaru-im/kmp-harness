package im.hikaru.ruoyi.module.pay.controller.admin.order.vo

import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.framework.excel.core.convert.MoneyConvert
import im.hikaru.ruoyi.module.pay.enums.DictTypeConstants
import java.time.LocalDateTime

class PayOrderExcelVO {
    @field:ExcelProperty("编号")
    var id: Long? = null
    @field:ExcelProperty("创建时间")
    var createTime: LocalDateTime? = null
    @field:ExcelProperty("支付金额", converter = MoneyConvert::class)
    var price: Int? = null
    @field:ExcelProperty("退款金额", converter = MoneyConvert::class)
    var refundPrice: Int? = null
    @field:ExcelProperty("手续金额", converter = MoneyConvert::class)
    var channelFeePrice: Int? = null
    @field:ExcelProperty("商户单号")
    var merchantOrderId: String? = null
    @field:ExcelProperty("支付单号")
    var no: String? = null
    @field:ExcelProperty("渠道单号")
    var channelOrderNo: String? = null
    @field:ExcelProperty("支付状态", converter = DictConvert::class)
    @DictFormat(DictTypeConstants.ORDER_STATUS)
    var status: Int? = null
    @field:ExcelProperty("渠道编号名称", converter = DictConvert::class)
    @DictFormat(DictTypeConstants.CHANNEL_CODE)
    var channelCode: String? = null
    @field:ExcelProperty("订单支付成功时间")
    var successTime: LocalDateTime? = null
    @field:ExcelProperty("订单失效时间")
    var expireTime: LocalDateTime? = null
    @field:ExcelProperty("应用名称")
    var appName: String? = null
    @field:ExcelProperty("商品标题")
    var subject: String? = null
    @field:ExcelProperty("商品描述")
    var body: String? = null
}
