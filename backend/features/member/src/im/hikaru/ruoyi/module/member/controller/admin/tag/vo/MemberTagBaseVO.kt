package im.hikaru.ruoyi.module.member.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

open class MemberTagBaseVO {
    @field:Schema(description = "标签名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @field:NotNull(message = "标签名称不能为空")
    var name: String? = null
}
