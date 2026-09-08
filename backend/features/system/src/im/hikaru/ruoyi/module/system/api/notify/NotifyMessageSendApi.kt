package im.hikaru.ruoyi.module.system.api.notify

import im.hikaru.ruoyi.module.system.api.notify.dto.NotifySendSingleToUserReqDTO
import jakarta.validation.Valid

interface NotifyMessageSendApi {
    fun sendSingleMessageToAdmin(@Valid reqDTO: NotifySendSingleToUserReqDTO): Long?
    fun sendSingleMessageToMember(@Valid reqDTO: NotifySendSingleToUserReqDTO): Long?
}
