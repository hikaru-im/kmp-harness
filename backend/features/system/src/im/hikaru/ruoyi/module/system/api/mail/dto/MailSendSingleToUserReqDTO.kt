package im.hikaru.ruoyi.module.system.api.mail.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import java.io.File

class MailSendSingleToUserReqDTO {
    var userId: Long? = null
    var toMails: List<@Email String>? = null
    var ccMails: List<@Email String>? = null
    var bccMails: List<@Email String>? = null

    @field:NotNull(message = "Mail template code must not be null")
    var templateCode: String? = null
    var templateParams: Map<String, Any?>? = null
    var attachments: Array<File>? = null
}
