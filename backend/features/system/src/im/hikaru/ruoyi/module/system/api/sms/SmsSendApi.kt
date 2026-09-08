package im.hikaru.ruoyi.module.system.api.sms

import im.hikaru.ruoyi.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO
import jakarta.validation.Valid

interface SmsSendApi {
    fun sendSingleSmsToAdmin(@Valid reqDTO: SmsSendSingleToUserReqDTO): Long
    fun sendSingleSmsToMember(@Valid reqDTO: SmsSendSingleToUserReqDTO): Long
}
