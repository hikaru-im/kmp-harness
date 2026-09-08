package im.hikaru.ruoyi.module.system.service.social

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxQrcodeReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderNotifyConfirmReceiveReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderUploadShippingInfoReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialClientDO
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo
import me.zhyd.oauth.model.AuthUser
import me.chanjar.weixin.common.bean.WxJsapiSignature
import me.chanjar.weixin.common.bean.subscribemsg.TemplateInfo

interface SocialAuthClient {
    fun getAuthorizeUrl(socialType: Int, userType: Int, redirectUri: String): String

    fun getAuthUser(socialType: Int, userType: Int, code: String, state: String): AuthUser
}

interface SocialClientService : SocialAuthClient {
    fun createWxMpJsapiSignature(userType: Int, url: String): WxJsapiSignature

    fun getWxMaPhoneNumberInfo(userType: Int, phoneCode: String): WxMaPhoneNumberInfo

    fun getWxaQrcode(reqVO: SocialWxQrcodeReqDTO): ByteArray

    fun getSubscribeTemplateList(userType: Int): List<TemplateInfo>

    fun sendSubscribeMessage(reqDTO: SocialWxaSubscribeMessageSendReqDTO, templateId: String, openId: String)

    fun uploadWxaOrderShippingInfo(userType: Int, reqDTO: SocialWxaOrderUploadShippingInfoReqDTO)

    fun notifyWxaOrderConfirmReceive(userType: Int, reqDTO: SocialWxaOrderNotifyConfirmReceiveReqDTO)

    fun createSocialClient(req: SocialClientSaveReqVO): Long

    fun updateSocialClient(req: SocialClientSaveReqVO)

    fun deleteSocialClient(id: Long)

    fun deleteSocialClientList(ids: Collection<Long>)

    fun getSocialClient(id: Long): SocialClientDO?

    fun getSocialClientPage(req: SocialClientPageReqVO): PageResult<SocialClientDO>
}
