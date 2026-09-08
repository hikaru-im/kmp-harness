package im.hikaru.ruoyi.module.system.api.notify.dto

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class NotifyTemplateReqDTO {
    @field:NotEmpty(message = "Template name must not be empty")
    var name: String? = null

    @field:NotNull(message = "Template code must not be null")
    var code: String? = null

    @field:NotNull(message = "Template type must not be null")
    var type: Int? = null

    @field:NotEmpty(message = "Sender name must not be empty")
    var nickname: String? = null

    @field:NotEmpty(message = "Template content must not be empty")
    var content: String? = null

    @field:NotNull(message = "Status must not be null")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null
    var remark: String? = null
}
