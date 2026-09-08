package im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class MemberExperienceRecordBaseVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "3638")
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
    @field:Schema(description = "业务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "12164")
    @field:NotNull(message = "业务编号不能为空")
    var bizId: String? = null
    @field:Schema(description = "业务类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "业务类型不能为空")
    var bizType: Int? = null
    @field:Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "增加经验")
    @field:NotNull(message = "标题不能为空")
    var title: String? = null
    @field:Schema(description = "经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @field:NotNull(message = "经验不能为空")
    var experience: Int? = null
    @field:Schema(description = "变更后的经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "200")
    @field:NotNull(message = "变更后的经验不能为空")
    var totalExperience: Int? = null
    @field:Schema(description = "描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "下单增加 100 经验")
    @field:NotNull(message = "描述不能为空")
    var description: String? = null
}
