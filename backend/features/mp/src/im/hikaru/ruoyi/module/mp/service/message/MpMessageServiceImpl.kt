package im.hikaru.ruoyi.module.mp.service.message

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessageSendReqVO
import im.hikaru.ruoyi.module.mp.convert.message.MpMessageConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.dal.mysql.message.MpMessageDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MESSAGE_SEND_FAIL
import im.hikaru.ruoyi.module.mp.enums.message.MpMessageSendFromEnum
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.MpUtils
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import im.hikaru.ruoyi.module.mp.service.material.MpMaterialService
import im.hikaru.ruoyi.module.mp.service.message.bo.MpMessageSendOutReqBO
import im.hikaru.ruoyi.module.mp.service.user.MpUserService
import jakarta.validation.Validator
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MpMessageServiceImpl(
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val mpUserService: MpUserService,
    private val mpMaterialService: MpMaterialService,
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
    private val validator: Validator,
) : MpMessageService {
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()
    override fun getMessagePage(pageReqVO: MpMessagePageReqVO): PageResult<MpMessageDO> = MpMessageDao.selectPage(pageReqVO)

    override fun receiveMessage(weixinService: WxMpService, appId: String, wxMessage: WxMpXmlMessage) {
        val account = requireNotNull(mpAccountService.getAccountFromCache(appId)) { "Mp account($appId) does not exist" }
        val openid = requireNotNull(wxMessage.fromUser)
        val user = mpUserService.getUser(appId, openid) ?: run {
            mpUserService.saveUser(appId, weixinService.userService.userInfo(openid))
        }
        val message = MpMessageConvert.convert(wxMessage, account, user).apply {
            sendFrom = MpMessageSendFromEnum.USER_TO_MP.from
        }
        downloadMessageMedia(message)
        MpMessageDao.insert(message)
    }

    override fun sendOutMessage(sendReqBO: MpMessageSendOutReqBO): WxMpXmlOutMessage {
        MpUtils.validateMessage(validator, sendReqBO.type, sendReqBO)
        val appId = requireNotNull(sendReqBO.appId)
        val openid = requireNotNull(sendReqBO.openid)
        val account = requireNotNull(mpAccountService.getAccountFromCache(appId)) { "Mp account($appId) does not exist" }
        val user = requireNotNull(mpUserService.getUser(appId, openid)) { "Mp user($appId/$openid) does not exist" }
        val message = MpMessageConvert.convert(sendReqBO, account, user).apply {
            sendFrom = MpMessageSendFromEnum.MP_TO_USER.from
        }
        downloadMessageMedia(message)
        MpMessageDao.insert(message)
        return MpMessageConvert.convertOut(message, account)
    }

    override fun sendKefuMessage(sendReqVO: MpMessageSendReqVO): MpMessageDO {
        MpUtils.validateMessage(validator, sendReqVO.type, sendReqVO)
        val user = mpUserService.getRequiredUser(requireNotNull(sendReqVO.userId))
        val account = mpAccountService.getRequiredAccount(requireNotNull(user.accountId))
        val wxMessage = MpMessageConvert.convert(sendReqVO, user)
        try {
            mpServiceFactory.getRequiredMpService(requireNotNull(user.appId)).kefuService
                .sendKefuMessageWithResponse(wxMessage)
        } catch (ex: Exception) {
            val message = (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
            throw exception(MESSAGE_SEND_FAIL, message)
        }
        val message = MpMessageConvert.convert(wxMessage, account, user).apply {
            sendFrom = MpMessageSendFromEnum.MP_TO_USER.from
        }
        downloadMessageMedia(message)
        MpMessageDao.insert(message)
        return message
    }

    private fun downloadMessageMedia(message: MpMessageDO) {
        message.mediaId?.takeIf(String::isNotBlank)?.let {
            message.mediaUrl = mpMaterialService.downloadMaterialUrl(
                requireNotNull(message.accountId),
                it,
                MpUtils.getMediaFileType(message.type),
            )
        }
        message.thumbMediaId?.takeIf(String::isNotBlank)?.let {
            message.thumbMediaUrl = mpMaterialService.downloadMaterialUrl(
                requireNotNull(message.accountId),
                it,
                WxConsts.MediaFileType.THUMB,
            )
        }
    }
}
