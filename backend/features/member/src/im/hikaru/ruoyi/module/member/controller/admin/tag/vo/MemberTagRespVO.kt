package im.hikaru.ruoyi.module.member.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 会员标签 Response VO")
class MemberTagRespVO : MemberTagBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "907")
    var id: Long? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
