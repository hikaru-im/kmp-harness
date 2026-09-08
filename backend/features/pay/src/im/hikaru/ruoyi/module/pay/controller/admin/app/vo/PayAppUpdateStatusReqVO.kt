package im.hikaru.ruoyi.module.pay.controller.admin.app.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 应用更新状态 Request VO")
class PayAppUpdateStatusReqVO {
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "应用编号不能为空")
    var id: Long? = null
    @field:Schema(description = "状态，见 SysCommonStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @field:NotNull(message = "状态不能为空")
    var status: Int? = null
}
