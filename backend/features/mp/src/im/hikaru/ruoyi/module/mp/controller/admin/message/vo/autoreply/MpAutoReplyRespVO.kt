package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 公众号自动回复 Response VO")
class MpAutoReplyRespVO : MpAutoReplyBaseVO() {
    @field:Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var accountId: Long? = null
    @field:Schema(description = "公众号 appId", requiredMode = Schema.RequiredMode.REQUIRED, example = "wx1234567890")
    var appId: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
