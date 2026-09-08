package im.hikaru.ruoyi.module.system.api.notify

import im.hikaru.ruoyi.module.system.api.notify.dto.NotifySendSingleToUserReqDTO
import im.hikaru.ruoyi.module.system.service.notify.NotifySendService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class NotifyMessageSendApiImpl(
    private val notifySendService: NotifySendService,
) : NotifyMessageSendApi {
    override fun sendSingleMessageToAdmin(reqDTO: NotifySendSingleToUserReqDTO): Long? =
        notifySendService.sendSingleNotifyToAdmin(
            requireNotNull(reqDTO.userId), requireNotNull(reqDTO.templateCode), reqDTO.templateParams,
        )

    override fun sendSingleMessageToMember(reqDTO: NotifySendSingleToUserReqDTO): Long? =
        notifySendService.sendSingleNotifyToMember(
            requireNotNull(reqDTO.userId), requireNotNull(reqDTO.templateCode), reqDTO.templateParams,
        )
}
