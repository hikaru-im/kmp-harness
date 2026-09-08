package im.hikaru.ruoyi.module.pay.controller.admin.notify.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 回调通知分页 Request VO")
class PayNotifyTaskPageReqVO : PageParam() {
    @field:Schema(description = "应用编号", example = "10636")
    var appId: Long? = null
    @field:Schema(description = "通知类型", example = "2")
    var type: Int? = null
    @field:Schema(description = "数据编号", example = "6722")
    var dataId: Long? = null
    @field:Schema(description = "通知状态", example = "1")
    var status: Int? = null
    @field:Schema(description = "商户订单编号", example = "26697")
    var merchantOrderId: String? = null
    @field:Schema(description = "商户退款编号", example = "26697")
    var merchantRefundId: String? = null
    @field:Schema(description = "商户转账编号", example = "26697")
    var merchantTransferId: String? = null
    @field:Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
