package im.hikaru.ruoyi.module.system.api.mail

import im.hikaru.ruoyi.module.system.api.mail.dto.MailSendSingleToUserReqDTO
import im.hikaru.ruoyi.module.system.service.mail.MailSendService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MailSendApiImpl(
    private val mailSendService: MailSendService,
) : MailSendApi {
    override fun sendSingleMailToAdmin(reqDTO: MailSendSingleToUserReqDTO): Long =
        mailSendService.sendSingleMailToAdmin(
            reqDTO.userId, reqDTO.toMails, reqDTO.ccMails, reqDTO.bccMails,
            requireNotNull(reqDTO.templateCode), reqDTO.templateParams, reqDTO.attachments,
        )

    override fun sendSingleMailToMember(reqDTO: MailSendSingleToUserReqDTO): Long =
        mailSendService.sendSingleMailToMember(
            reqDTO.userId, reqDTO.toMails, reqDTO.ccMails, reqDTO.bccMails,
            requireNotNull(reqDTO.templateCode), reqDTO.templateParams, reqDTO.attachments,
        )
}
