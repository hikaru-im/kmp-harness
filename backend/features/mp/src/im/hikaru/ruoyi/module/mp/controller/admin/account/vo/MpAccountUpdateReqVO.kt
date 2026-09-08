package im.hikaru.ruoyi.module.mp.controller.admin.account.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 公众号账号更新 Request VO")
class MpAccountUpdateReqVO : MpAccountBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
