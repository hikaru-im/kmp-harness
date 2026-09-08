package im.hikaru.ruoyi.module.member.controller.admin.user.vo

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 会员用户分页 Request VO")
class MemberUserPageReqVO : PageParam() {
    @field:Schema(description = "手机号", example = "15601691300")
    var mobile: String? = null
    @field:Schema(description = "邮箱", example = "member@iocoder.cn")
    var email: String? = null
    @field:Schema(description = "用户昵称", example = "李四")
    var nickname: String? = null
    @field:Schema(description = "最后登录时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var loginDate: Array<LocalDateTime>? = null
    @field:Schema(description = "会员标签编号列表", example = "[1, 2]")
    var tagIds: List<Long>? = null
    @field:Schema(description = "会员等级编号", example = "1")
    var levelId: Long? = null
    @field:Schema(description = "用户分组编号", example = "1")
    var groupId: Long? = null
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    var createTime: Array<LocalDateTime>? = null
}
