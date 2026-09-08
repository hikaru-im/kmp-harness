package im.hikaru.ruoyi.module.member.controller.admin.group.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 用户分组分页 Request VO")
class MemberGroupPageReqVO : PageParam() {
    @field:Schema(description = "名称", example = "购物达人")
    var name: String? = null
    @field:Schema(description = "状态", example = "1")
    var status: Int? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
