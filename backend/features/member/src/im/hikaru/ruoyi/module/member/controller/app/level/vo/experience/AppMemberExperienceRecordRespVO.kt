package im.hikaru.ruoyi.module.member.controller.app.level.vo.experience

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "用户 App - 会员经验记录 Response VO")
class AppMemberExperienceRecordRespVO {
    @field:Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "增加经验")
    var title: String? = null
    @field:Schema(description = "经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var experience: Int? = null
    @field:Schema(description = "描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "下单增加 100 经验")
    var description: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
