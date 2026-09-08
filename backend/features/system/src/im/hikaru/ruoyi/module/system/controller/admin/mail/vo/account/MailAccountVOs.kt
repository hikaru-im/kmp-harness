package im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

class MailAccountPageReqVO : PageParam() {
    var mail: String? = null
    var username: String? = null
}

class MailAccountSaveReqVO {
    var id: Long? = null

    @field:NotNull(message = "Email must not be null")
    @field:Email(message = "Email format is invalid")
    var mail: String? = null

    @field:NotBlank(message = "Username must not be blank")
    var username: String? = null

    @field:NotBlank(message = "Password must not be blank")
    var password: String? = null

    @field:NotBlank(message = "SMTP host must not be blank")
    var host: String? = null

    @field:NotNull(message = "SMTP port must not be null")
    var port: Int? = null

    @field:NotNull(message = "SSL setting must not be null")
    var sslEnable: Boolean? = null

    @field:NotNull(message = "STARTTLS setting must not be null")
    var starttlsEnable: Boolean? = null
}

open class MailAccountSimpleRespVO {
    var id: Long? = null
    var mail: String? = null
}

class MailAccountRespVO : MailAccountSimpleRespVO() {
    var username: String? = null
    var password: String? = null
    var host: String? = null
    var port: Int? = null
    var sslEnable: Boolean? = null
    var starttlsEnable: Boolean? = null
    var createTime: LocalDateTime? = null
}
