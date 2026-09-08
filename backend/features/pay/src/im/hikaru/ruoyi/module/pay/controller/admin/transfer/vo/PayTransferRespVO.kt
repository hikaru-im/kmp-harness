package im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.framework.excel.core.convert.MoneyConvert
import im.hikaru.ruoyi.module.pay.enums.DictTypeConstants
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 转账单 Response VO")
@ExcelIgnoreUnannotated
class PayTransferRespVO {
    @field:ExcelProperty("转账单编号")
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2931")
    var id: Long? = null
    @field:ExcelProperty("转账单号")
    @field:Schema(description = "转账单号", requiredMode = Schema.RequiredMode.REQUIRED)
    var no: String? = null
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "12831")
    var appId: Long? = null
    @field:ExcelProperty("应用名称")
    @field:Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋艿")
    var appName: String? = null
    @field:Schema(description = "转账渠道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "24833")
    var channelId: Long? = null
    @field:ExcelProperty("转账渠道", converter = DictConvert::class)
    @DictFormat(DictTypeConstants.CHANNEL_CODE)
    @field:Schema(description = "转账渠道编码", requiredMode = Schema.RequiredMode.REQUIRED)
    var channelCode: String? = null
    @field:ExcelProperty("商户转账单编号")
    @field:Schema(description = "商户转账单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "17481")
    var merchantTransferId: String? = null
    @field:ExcelProperty("转账状态", converter = DictConvert::class)
    @DictFormat(DictTypeConstants.TRANSFER_STATUS)
    @field:Schema(description = "转账状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    var status: Int? = null
    @field:ExcelProperty("转账成功时间")
    @field:Schema(description = "转账成功时间")
    var successTime: LocalDateTime? = null
    @field:Schema(description = "转账金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "964")
    @field:ExcelProperty("转账金额", converter = MoneyConvert::class)
    var price: Int? = null
    @field:ExcelProperty("转账标题")
    @field:Schema(description = "转账标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "冲冲冲！")
    var subject: String? = null
    @field:Schema(description = "收款人姓名", example = "王五")
    @field:ExcelProperty("收款人姓名")
    var userName: String? = null
    @field:Schema(description = "收款人账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "26589")
    @field:ExcelProperty("收款人账号")
    var userAccount: String? = null
    @field:Schema(description = "异步通知商户地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.iocoder.cn")
    var notifyUrl: String? = null
    @field:ExcelProperty("用户 IP")
    @field:Schema(description = "用户 IP", requiredMode = Schema.RequiredMode.REQUIRED)
    var userIp: String? = null
    @field:Schema(description = "渠道的额外参数")
    var channelExtras: Map<String, String>? = null
    @field:Schema(description = "渠道转账单号")
    @field:ExcelProperty("渠道转账单号")
    var channelTransferNo: String? = null
    @field:Schema(description = "调用渠道的错误码")
    var channelErrorCode: String? = null
    @field:ExcelProperty("渠道错误提示")
    @field:Schema(description = "调用渠道的错误提示")
    var channelErrorMsg: String? = null
    @field:Schema(description = "渠道的同步/异步通知的内容")
    var channelNotifyData: String? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:ExcelProperty("创建时间")
    var createTime: LocalDateTime? = null
}
