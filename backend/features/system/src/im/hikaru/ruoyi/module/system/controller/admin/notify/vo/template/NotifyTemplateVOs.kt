package im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.notify.NotifyTemplateTypeEnum
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class NotifyTemplatePageReqVO : PageParam() {
    var code: String? = null
    var name: String? = null
    var status: Int? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

class NotifyTemplateSaveReqVO {
    var id: Long? = null

    @field:NotBlank(message = "Template name must not be blank")
    var name: String? = null

    @field:NotBlank(message = "Template code must not be blank")
    var code: String? = null

    @field:NotNull(message = "Template type must not be null")
    @field:InEnum(NotifyTemplateTypeEnum::class)
    var type: Int? = null

    @field:NotBlank(message = "Sender nickname must not be blank")
    var nickname: String? = null

    @field:NotBlank(message = "Template content must not be blank")
    var content: String? = null

    @field:NotNull(message = "Status must not be null")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null

    var remark: String? = null
}

open class NotifyTemplateSimpleRespVO {
    @ExcelProperty("Id")
    var id: Long? = null
    @ExcelProperty("Name")
    var name: String? = null
    @ExcelProperty("Code")
    var code: String? = null
}

@ExcelIgnoreUnannotated
class NotifyTemplateRespVO : NotifyTemplateSimpleRespVO() {
    @ExcelProperty("Type")
    var type: Int? = null
    @ExcelProperty("Sender nickname")
    var nickname: String? = null
    @ExcelProperty("Content")
    var content: String? = null
    var params: List<String>? = null
    @ExcelProperty("Status")
    var status: Int? = null
    @ExcelProperty("Remark")
    var remark: String? = null
    @ExcelProperty("Created at")
    var createTime: LocalDateTime? = null
}

class NotifyTemplateSendReqVO {
    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null

    @field:NotNull(message = "User type must not be null")
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null

    @field:NotBlank(message = "Template code must not be blank")
    var templateCode: String? = null

    var templateParams: Map<String, Any?>? = null
}
