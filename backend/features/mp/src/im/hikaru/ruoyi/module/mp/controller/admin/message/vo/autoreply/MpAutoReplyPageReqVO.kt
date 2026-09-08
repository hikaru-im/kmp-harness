package im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号自动回复的分页 Request VO")
class MpAutoReplyPageReqVO : PageParam() {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
}
