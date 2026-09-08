package im.hikaru.ruoyi.module.member.controller.app.address.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

@Schema(description = "用户 APP - 用户收件地址更新 Request VO")
class AppAddressUpdateReqVO : AppAddressBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "编号不能为空")
    var id: Long? = null
}
