package im.hikaru.ruoyi.module.system.api.social

import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderNotifyConfirmReceiveReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderUploadShippingInfoReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeTemplateRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxJsapiSignatureRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxQrcodeReqDTO
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import im.hikaru.ruoyi.module.system.service.social.SocialClientService
import im.hikaru.ruoyi.module.system.service.social.SocialUserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SocialClientApiImpl(
    private val socialClientService: SocialClientService,
    private val socialUserService: SocialUserService,
) : SocialClientApi {
    override fun getAuthorizeUrl(socialType: Int, userType: Int, redirectUri: String): String =
        socialClientService.getAuthorizeUrl(socialType, userType, redirectUri)

    override fun createWxMpJsapiSignature(userType: Int, url: String): SocialWxJsapiSignatureRespDTO {
        val signature = socialClientService.createWxMpJsapiSignature(userType, url)
        return SocialWxJsapiSignatureRespDTO().apply {
            appId = signature.appId
            nonceStr = signature.nonceStr
            timestamp = signature.timestamp
            this.url = signature.url
            this.signature = signature.signature
        }
    }

    override fun getWxMaPhoneNumberInfo(userType: Int, phoneCode: String): SocialWxPhoneNumberInfoRespDTO {
        val info = socialClientService.getWxMaPhoneNumberInfo(userType, phoneCode)
        return SocialWxPhoneNumberInfoRespDTO().apply {
            phoneNumber = info.phoneNumber
            purePhoneNumber = info.purePhoneNumber
            countryCode = info.countryCode
        }
    }

    override fun getWxaQrcode(reqVO: SocialWxQrcodeReqDTO): ByteArray = socialClientService.getWxaQrcode(reqVO)

    override fun getWxaSubscribeTemplateList(userType: Int): List<SocialWxaSubscribeTemplateRespDTO> =
        socialClientService.getSubscribeTemplateList(userType).map { template ->
            SocialWxaSubscribeTemplateRespDTO().apply {
                id = template.priTmplId
                title = template.title
                content = template.content
                example = template.example
                type = template.type
            }
        }

    override fun sendWxaSubscribeMessage(reqDTO: SocialWxaSubscribeMessageSendReqDTO) {
        val template = socialClientService.getSubscribeTemplateList(requireNotNull(reqDTO.userType))
            .firstOrNull { it.title == reqDTO.templateTitle }
        if (template == null) {
            log.warn("Skipping WeChat subscription message because no matching template exists: title={}", reqDTO.templateTitle)
            return
        }
        val socialUser = socialUserService.getSocialUserByUserId(
            requireNotNull(reqDTO.userType),
            requireNotNull(reqDTO.userId),
            SocialTypeEnum.WECHAT_MINI_PROGRAM.type,
        )
        val openId = socialUser?.openid?.takeIf { it.isNotBlank() }
        if (openId == null) {
            log.warn("Skipping WeChat subscription message because the user has no Mini Program openid: userId={}", reqDTO.userId)
            return
        }
        socialClientService.sendSubscribeMessage(reqDTO, requireNotNull(template.priTmplId), openId)
    }

    override fun uploadWxaOrderShippingInfo(userType: Int, reqDTO: SocialWxaOrderUploadShippingInfoReqDTO) =
        socialClientService.uploadWxaOrderShippingInfo(userType, reqDTO)

    override fun notifyWxaOrderConfirmReceive(userType: Int, reqDTO: SocialWxaOrderNotifyConfirmReceiveReqDTO) =
        socialClientService.notifyWxaOrderConfirmReceive(userType, reqDTO)

    private companion object {
        val log = LoggerFactory.getLogger(SocialClientApiImpl::class.java)
    }
}
