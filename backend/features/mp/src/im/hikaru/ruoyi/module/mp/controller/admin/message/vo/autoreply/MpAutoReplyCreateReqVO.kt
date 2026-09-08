package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号自动回复的创建 Request VO")
class MpAutoReplyCreateReqVO : MpAutoReplyBaseVO() {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
}
