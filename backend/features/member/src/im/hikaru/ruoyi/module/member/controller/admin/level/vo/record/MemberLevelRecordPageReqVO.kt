package im.hikaru.ruoyi.module.member.controller.admin.level.vo.record

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 会员等级记录分页 Request VO")
class MemberLevelRecordPageReqVO : PageParam() {
    @field:Schema(description = "用户编号", example = "25923")
    var userId: Long? = null
    @field:Schema(description = "等级编号", example = "25985")
    var levelId: Long? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
