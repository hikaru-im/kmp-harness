package im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 转账单分页 Request VO")
class PayTransferPageReqVO : PageParam() {
    @field:Schema(description = "转账单号")
    var no: String? = null
    @field:Schema(description = "应用编号", example = "12831")
    var appId: Long? = null
    @field:Schema(description = "渠道编码", example = "wx_app")
    var channelCode: String? = null
    @field:Schema(description = "商户转账单编号", example = "17481")
    var merchantOrderId: String? = null
    @field:Schema(description = "转账状态", example = "2")
    var status: Int? = null
    @field:Schema(description = "收款人姓名", example = "王五")
    var userName: String? = null
    @field:Schema(description = "收款人账号", example = "26589")
    var userAccount: String? = null
    @field:Schema(description = "渠道转账单号")
    var channelTransferNo: String? = null
    @field:Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
