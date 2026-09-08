package im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "用户 APP - 钱包流水分页 Request VO")
class AppPayWalletTransactionPageReqVO : PageParam() {
    @field:Schema(description = "类型", example = "1")
    var type: Int? = null
    @field:Schema(description = "创建时间")
    @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null

    companion object {
        const val TYPE_INCOME = 1
        const val TYPE_EXPENSE = 2
    }
}
