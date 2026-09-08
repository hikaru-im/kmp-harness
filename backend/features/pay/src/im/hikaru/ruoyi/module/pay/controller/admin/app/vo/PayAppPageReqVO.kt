package im.hikaru.ruoyi.module.pay.controller.admin.app.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 支付应用信息分页 Request VO")
class PayAppPageReqVO : PageParam() {
    @field:Schema(description = "应用名", example = "小豆")
    var name: String? = null
    @field:Schema(description = "应用标识", example = "yudao")
    var appKey: String? = null
    @field:Schema(description = "开启状态", example = "0")
    var status: Int? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @field:Schema(description = "创建时间")
    var createTime: Array<LocalDateTime>? = null
}
