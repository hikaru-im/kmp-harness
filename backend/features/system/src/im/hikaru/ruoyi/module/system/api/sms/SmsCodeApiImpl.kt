package im.hikaru.ruoyi.module.system.api.sms

import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO
import im.hikaru.ruoyi.module.system.service.sms.SmsCodeService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SmsCodeApiImpl(
    private val smsCodeService: SmsCodeService,
) : SmsCodeApi {
    override fun sendSmsCode(reqDTO: SmsCodeSendReqDTO) = smsCodeService.sendSmsCode(reqDTO)
    override fun useSmsCode(reqDTO: SmsCodeUseReqDTO) = smsCodeService.useSmsCode(reqDTO)
    override fun validateSmsCode(reqDTO: SmsCodeValidateReqDTO) = smsCodeService.validateSmsCode(reqDTO)
}
