package im.hikaru.ruoyi.module.mp.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号标签更新 Request VO")
class MpTagUpdateReqVO : MpTagBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
