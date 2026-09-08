package im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 会员钱包分页 Request VO")
class PayWalletPageReqVO : PageParam() {
    @field:Schema(description = "用户编号", example = "1024")
    var userId: Long? = null
    @field:Schema(description = "用户类型", example = "1")
    @field:InEnum(value = UserTypeEnum::class)
    var userType: Int? = null
    @field:Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
