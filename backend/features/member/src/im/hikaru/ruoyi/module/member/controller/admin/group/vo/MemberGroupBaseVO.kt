package im.hikaru.ruoyi.module.member.controller.admin.group.vo

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class MemberGroupBaseVO {
    @field:Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "购物达人")
    @field:NotNull(message = "名称不能为空")
    var name: String? = null
    @field:Schema(description = "备注", requiredMode = Schema.RequiredMode.REQUIRED, example = "你猜")
    var remark: String? = null
    @field:Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "状态不能为空")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
}
