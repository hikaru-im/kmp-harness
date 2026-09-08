package im.hikaru.ruoyi.module.system.api.sms

import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO
import jakarta.validation.Valid

interface SmsCodeApi {
    fun sendSmsCode(@Valid reqDTO: SmsCodeSendReqDTO)
    fun useSmsCode(@Valid reqDTO: SmsCodeUseReqDTO)
    fun validateSmsCode(@Valid reqDTO: SmsCodeValidateReqDTO)
}
