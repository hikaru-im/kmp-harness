package im.hikaru.ruoyi.module.system.api.sms

import im.hikaru.ruoyi.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO
import im.hikaru.ruoyi.module.system.service.sms.SmsSendService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SmsSendApiImpl(
    private val smsSendService: SmsSendService,
) : SmsSendApi {
    override fun sendSingleSmsToAdmin(reqDTO: SmsSendSingleToUserReqDTO): Long =
        smsSendService.sendSingleSmsToAdmin(
            reqDTO.mobile,
            reqDTO.userId,
            requireNotNull(reqDTO.templateCode),
            reqDTO.templateParams.orEmpty(),
        )

    override fun sendSingleSmsToMember(reqDTO: SmsSendSingleToUserReqDTO): Long =
        smsSendService.sendSingleSmsToMember(
            reqDTO.mobile,
            reqDTO.userId,
            requireNotNull(reqDTO.templateCode),
            reqDTO.templateParams.orEmpty(),
        )
}
