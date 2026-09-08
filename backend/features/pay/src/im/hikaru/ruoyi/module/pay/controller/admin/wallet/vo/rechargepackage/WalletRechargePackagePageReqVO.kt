package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 充值套餐分页 Request VO")
class WalletRechargePackagePageReqVO : PageParam() {
    @field:Schema(description = "套餐名", example = "李四")
    var name: String? = null
    @field:Schema(description = "状态", example = "2")
    var status: Int? = null
    @field:Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
