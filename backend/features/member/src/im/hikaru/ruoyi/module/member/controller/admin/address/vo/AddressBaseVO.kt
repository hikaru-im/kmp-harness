package im.hikaru.ruoyi.module.member.controller.admin.address.vo

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.time.LocalDateTime

open class AddressBaseVO {
    @field:Schema(description = "收件人名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    @field:NotNull(message = "收件人名称不能为空")
    var name: String? = null
    @field:Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "手机号不能为空")
    var mobile: String? = null
    @field:Schema(description = "地区编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "15716")
    @field:NotNull(message = "地区编码不能为空")
    var areaId: Long? = null
    @field:Schema(description = "收件详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull(message = "收件详细地址不能为空")
    var detailAddress: String? = null
    @field:Schema(description = "是否默认", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @field:NotNull(message = "是否默认不能为空")
    var defaultStatus: Boolean? = null
}
