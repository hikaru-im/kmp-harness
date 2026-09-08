package im.hikaru.ruoyi.module.system.api.social

import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderNotifyConfirmReceiveReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderUploadShippingInfoReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeTemplateRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxJsapiSignatureRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxQrcodeReqDTO
import jakarta.validation.Valid

interface SocialClientApi {
    fun getAuthorizeUrl(socialType: Int, userType: Int, redirectUri: String): String
    fun createWxMpJsapiSignature(userType: Int, url: String): SocialWxJsapiSignatureRespDTO?
    fun getWxMaPhoneNumberInfo(userType: Int, phoneCode: String): SocialWxPhoneNumberInfoRespDTO?
    fun getWxaQrcode(@Valid reqVO: SocialWxQrcodeReqDTO): ByteArray
    fun getWxaSubscribeTemplateList(userType: Int): List<SocialWxaSubscribeTemplateRespDTO>
    fun sendWxaSubscribeMessage(reqDTO: SocialWxaSubscribeMessageSendReqDTO)
    fun uploadWxaOrderShippingInfo(userType: Int, reqDTO: SocialWxaOrderUploadShippingInfoReqDTO)
    fun notifyWxaOrderConfirmReceive(userType: Int, reqDTO: SocialWxaOrderNotifyConfirmReceiveReqDTO)
}
