package im.hikaru.ruoyi.module.member.controller.admin.level.vo.record

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class MemberLevelRecordBaseVO {
    @field:Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "25923")
    @field:NotNull(message = "用户编号不能为空")
    var userId: Long? = null
    @field:Schema(description = "等级编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "25985")
    @field:NotNull(message = "等级编号不能为空")
    var levelId: Long? = null
    @field:Schema(description = "会员等级", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "会员等级不能为空")
    var level: Int? = null
    @field:Schema(description = "享受折扣", requiredMode = Schema.RequiredMode.REQUIRED, example = "13319")
    @field:NotNull(message = "享受折扣不能为空")
    var discountPercent: Int? = null
    @field:Schema(description = "升级经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "13319")
    @field:NotNull(message = "升级经验不能为空")
    var experience: Int? = null
    @field:Schema(description = "会员此时的经验", requiredMode = Schema.RequiredMode.REQUIRED, example = "13319")
    @field:NotNull(message = "会员此时的经验不能为空")
    var userExperience: Int? = null
    @field:Schema(description = "备注", requiredMode = Schema.RequiredMode.REQUIRED, example = "推广需要")
    @field:NotNull(message = "备注不能为空")
    var remark: String? = null
    @field:Schema(description = "描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "升级为金牌会员")
    @field:NotNull(message = "描述不能为空")
    var description: String? = null
}
