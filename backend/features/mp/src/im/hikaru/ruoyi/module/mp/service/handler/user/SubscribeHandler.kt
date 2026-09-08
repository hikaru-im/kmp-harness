package im.hikaru.ruoyi.module.mp.service.handler.user

import im.hikaru.ruoyi.module.mp.framework.mp.core.context.MpContextHolder
import im.hikaru.ruoyi.module.mp.service.message.MpAutoReplyService
import im.hikaru.ruoyi.module.mp.service.user.MpUserService
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.common.error.WxMpErrorMsgEnum
import me.chanjar.weixin.common.session.WxSessionManager
import me.chanjar.weixin.mp.api.WxMpMessageHandler
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import me.chanjar.weixin.mp.bean.result.WxMpUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SubscribeHandler(
    private val mpUserService: MpUserService,
    private val mpAutoReplyService: MpAutoReplyService,
) : WxMpMessageHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(
        wxMessage: WxMpXmlMessage,
        context: MutableMap<String, Any>,
        wxMpService: WxMpService,
        sessionManager: WxSessionManager,
    ): WxMpXmlOutMessage? {
        val wxUser = try {
            wxMpService.userService.userInfo(wxMessage.fromUser)
        } catch (ex: WxErrorException) {
            if (ex.error?.errorCode != WxMpErrorMsgEnum.CODE_48001.code) throw ex
            logger.warn("No permission to query WeChat user {}, using event data", wxMessage.fromUser)
            WxMpUser().apply {
                openId = wxMessage.fromUser
                subscribe = true
                subscribeTime = System.currentTimeMillis() / 1000L
            }
        }
        mpUserService.saveUser(MpContextHolder.getAppId(), wxUser)
        return mpAutoReplyService.replyForSubscribe(MpContextHolder.getAppId(), wxMessage)
    }
}
