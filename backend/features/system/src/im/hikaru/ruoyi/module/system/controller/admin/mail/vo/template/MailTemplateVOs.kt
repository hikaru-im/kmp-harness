package im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import java.time.LocalDateTime

class MailTemplatePageReqVO : PageParam() {
    var status: Int? = null
    var code: String? = null
    var name: String? = null
    var accountId: Long? = null
    var createTime: List<KotlinLocalDateTime>? = null
}

class MailTemplateSaveReqVO {
    var id: Long? = null

    @field:NotBlank(message = "Template name must not be blank")
    var name: String? = null

    @field:NotBlank(message = "Template code must not be blank")
    var code: String? = null

    @field:NotNull(message = "Mail account id must not be null")
    var accountId: Long? = null

    var nickname: String? = null

    @field:NotBlank(message = "Title must not be blank")
    var title: String? = null

    @field:NotBlank(message = "Content must not be blank")
    var content: String? = null

    @field:NotNull(message = "Status must not be null")
    @field:InEnum(CommonStatusEnum::class)
    var status: Int? = null

    var remark: String? = null
}

open class MailTemplateSimpleRespVO {
    var id: Long? = null
    var name: String? = null
    var code: String? = null
}

class MailTemplateRespVO : MailTemplateSimpleRespVO() {
    var accountId: Long? = null
    var nickname: String? = null
    var title: String? = null
    var content: String? = null
    var params: List<String>? = null
    var status: Int? = null
    var remark: String? = null
    var createTime: LocalDateTime? = null
}

class MailTemplateSendReqVO {
    @field:NotEmpty(message = "At least one recipient email is required")
    var toMails: List<@Email String>? = null
    var ccMails: List<@Email String>? = null
    var bccMails: List<@Email String>? = null

    @field:NotBlank(message = "Template code must not be blank")
    var templateCode: String? = null
    var templateParams: Map<String, Any?>? = null
}
