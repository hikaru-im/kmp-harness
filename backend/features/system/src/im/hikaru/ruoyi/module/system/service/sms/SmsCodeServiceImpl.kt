package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsCodeDO
import im.hikaru.ruoyi.module.system.dal.mysql.sms.SmsCodeDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CODE_EXCEED_SEND_MAXIMUM_QUANTITY_PER_DAY
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CODE_EXPIRED
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CODE_NOT_FOUND
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CODE_SEND_TOO_FAST
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CODE_USED
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import im.hikaru.ruoyi.module.system.framework.sms.config.SmsCodeProperties
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

@Service
@Validated
class SmsCodeServiceImpl(
    private val properties: SmsCodeProperties,
    private val smsSendService: SmsSendService,
    environment: Environment,
) : SmsCodeService {
    init {
        require(properties.localFixedCode == null || environment.acceptsProfiles(Profiles.of("local"))) {
            "A fixed SMS verification code can only be enabled with the local profile"
        }
    }

    override fun sendSmsCode(req: SmsCodeSendReqDTO) {
        val scene = SmsSceneEnum.getCodeByScene(req.scene)
            ?: throw IllegalArgumentException("Unknown SMS scene: ${req.scene}")
        val code = createSmsCode(
            requireNotNull(req.mobile), requireNotNull(req.scene), requireNotNull(req.createIp),
        )
        if (properties.localFixedCode == null) {
            smsSendService.sendSingleSms(
                requireNotNull(req.mobile), null, null, scene.templateCode, mapOf("code" to code),
            )
        } else {
            log.info("Local SMS delivery skipped for scene {}", scene.scene)
        }
    }

    internal fun createSmsCode(mobile: String, scene: Int, ip: String): String {
        val now = LocalDateTime.now()
        val last = SmsCodeDao.selectLastByMobile(mobile)
        if (last != null) {
            val createTime = requireNotNull(last.createTime).toJavaLocalDateTime()
            if (Duration.between(createTime, now) < properties.sendFrequency) {
                throw exception(SMS_CODE_SEND_TOO_FAST)
            }
            if (createTime.toLocalDate() == LocalDate.now() && requireNotNull(last.todayIndex) >= properties.sendMaximumQuantityPerDay) {
                throw exception(SMS_CODE_EXCEED_SEND_MAXIMUM_QUANTITY_PER_DAY)
            }
        }
        val code = properties.localFixedCode ?: run {
            require(properties.beginCode <= properties.endCode) { "SMS code range is invalid" }
            val number = if (properties.beginCode == properties.endCode) {
                properties.beginCode
            } else {
                Random.nextInt(properties.beginCode, properties.endCode + 1)
            }
            number.toString().padStart(properties.endCode.toString().length, '0')
        }
        SmsCodeDao.insert(SmsCodeDO().apply {
            this.mobile = mobile
            this.code = code
            this.scene = scene
            createIp = ip
            todayIndex = if (last?.createTime?.toJavaLocalDateTime()?.toLocalDate() == LocalDate.now()) {
                requireNotNull(last.todayIndex) + 1
            } else {
                1
            }
            used = false
        })
        return code
    }

    override fun useSmsCode(req: SmsCodeUseReqDTO) {
        val smsCode = validateSmsCode0(
            requireNotNull(req.mobile), requireNotNull(req.code), requireNotNull(req.scene),
        )
        SmsCodeDao.updateById(SmsCodeDO().apply {
            id = smsCode.id
            used = true
            usedTime = LocalDateTime.now().toKotlinLocalDateTime()
            usedIp = req.usedIp
        })
    }

    override fun validateSmsCode(req: SmsCodeValidateReqDTO) {
        validateSmsCode0(requireNotNull(req.mobile), requireNotNull(req.code), requireNotNull(req.scene))
    }

    internal fun validateSmsCode0(mobile: String, code: String, scene: Int): SmsCodeDO {
        val smsCode = SmsCodeDao.selectLastByMobile(mobile, code, scene) ?: throw exception(SMS_CODE_NOT_FOUND)
        val age = Duration.between(requireNotNull(smsCode.createTime).toJavaLocalDateTime(), LocalDateTime.now())
        if (age >= properties.expireTimes) throw exception(SMS_CODE_EXPIRED)
        if (smsCode.used == true) throw exception(SMS_CODE_USED)
        return smsCode
    }

    private companion object {
        val log = LoggerFactory.getLogger(SmsCodeServiceImpl::class.java)
    }
}
