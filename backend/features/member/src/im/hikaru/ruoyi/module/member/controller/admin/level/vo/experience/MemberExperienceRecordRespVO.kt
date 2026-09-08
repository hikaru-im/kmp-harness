package im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 会员经验记录 Response VO")
class MemberExperienceRecordRespVO : MemberExperienceRecordBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "19610")
    var id: Long? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
