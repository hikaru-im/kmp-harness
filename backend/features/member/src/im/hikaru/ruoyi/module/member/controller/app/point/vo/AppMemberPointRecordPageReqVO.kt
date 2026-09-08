package im.hikaru.ruoyi.module.member.controller.app.point.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "用户 App - 用户积分记录分页 Request VO")
class AppMemberPointRecordPageReqVO : PageParam() {
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
    @field:Schema(description = "是否增加积分", example = "true")
    var addStatus: Boolean? = null
}
