package im.hikaru.ruoyi.module.member.controller.admin.level.vo.level

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 会员等级 Response VO")
class MemberLevelRespVO : MemberLevelBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6103")
    var id: Long? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
