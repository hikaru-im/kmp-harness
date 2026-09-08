package im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 签到记录 Response VO")
class MemberSignInRecordRespVO {
    @field:Schema(description = "签到自增 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "11903")
    var id: Long? = null
    @field:Schema(description = "签到用户", requiredMode = Schema.RequiredMode.REQUIRED, example = "6507")
    var userId: Long? = null
    @field:Schema(description = "昵称", example = "张三")
    var nickname: String? = null
    @field:Schema(description = "第几天签到", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var day: Int? = null
    @field:Schema(description = "签到的积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var point: Int? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
