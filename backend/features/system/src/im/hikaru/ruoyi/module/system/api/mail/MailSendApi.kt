package im.hikaru.ruoyi.module.system.api.mail

import im.hikaru.ruoyi.module.system.api.mail.dto.MailSendSingleToUserReqDTO
import jakarta.validation.Valid

interface MailSendApi {
    fun sendSingleMailToAdmin(@Valid reqDTO: MailSendSingleToUserReqDTO): Long
    fun sendSingleMailToMember(@Valid reqDTO: MailSendSingleToUserReqDTO): Long
}
