package im.hikaru.ruoyi.module.pay.controller.admin.app.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

@Schema(description = "管理后台 - 支付应用信息更新 Request VO")
class PayAppUpdateReqVO : PayAppBaseVO() {
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "应用编号不能为空")
    var id: Long? = null
}
