package im.hikaru.ruoyi.module.mp.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

open class MpTagBaseVO {
    @field:Schema(description = "标签名", requiredMode = Schema.RequiredMode.REQUIRED, example = "土豆")
    @field:NotEmpty(message = "标签名不能为空")
    var name: String? = null
}
