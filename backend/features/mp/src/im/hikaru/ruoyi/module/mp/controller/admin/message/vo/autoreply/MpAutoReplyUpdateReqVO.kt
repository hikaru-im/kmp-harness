package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号自动回复的更新 Request VO")
class MpAutoReplyUpdateReqVO : MpAutoReplyBaseVO() {
    @field:Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "主键不能为空")
    var id: Long? = null
}
