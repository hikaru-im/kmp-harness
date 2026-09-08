package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO

interface SmsCodeService {
    fun sendSmsCode(req: SmsCodeSendReqDTO)
    fun useSmsCode(req: SmsCodeUseReqDTO)
    fun validateSmsCode(req: SmsCodeValidateReqDTO)
}
