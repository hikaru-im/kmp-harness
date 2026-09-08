package im.hikaru.ruoyi.module.member.controller.app.signin.vo.record

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "用户 App - 签到记录 Response VO")
class AppMemberSignInRecordRespVO {
    @field:Schema(description = "签到记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "第几天签到", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var day: Int? = null
    @field:Schema(description = "签到的分数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var point: Int? = null
    @field:Schema(description = "签到的经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var experience: Int? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
