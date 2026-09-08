package im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 会员经验记录分页 Request VO")
class MemberExperienceRecordPageReqVO : PageParam() {
    @field:Schema(description = "用户编号", example = "3638")
    var userId: Long? = null
    @field:Schema(description = "业务编号", example = "12164")
    var bizId: String? = null
    @field:Schema(description = "业务类型", example = "1")
    var bizType: Int? = null
    @field:Schema(description = "标题", example = "增加经验")
    var title: String? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
