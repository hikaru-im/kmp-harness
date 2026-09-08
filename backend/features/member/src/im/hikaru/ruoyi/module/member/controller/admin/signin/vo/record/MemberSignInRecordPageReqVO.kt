package im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 签到记录分页 Request VO")
class MemberSignInRecordPageReqVO : PageParam() {
    @field:Schema(description = "签到用户", example = "土豆")
    var nickname: String? = null
    @field:Schema(description = "第几天签到", example = "10")
    var day: Int? = null
    @field:Schema(description = "用户编号", example = "123")
    var userId: Long? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
