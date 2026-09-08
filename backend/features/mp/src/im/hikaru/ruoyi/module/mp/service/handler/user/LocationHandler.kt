package im.hikaru.ruoyi.module.mp.service.handler.user

import im.hikaru.ruoyi.module.mp.framework.mp.core.context.MpContextHolder
import im.hikaru.ruoyi.module.mp.service.message.MpAutoReplyService
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.common.session.WxSessionManager
import me.chanjar.weixin.mp.api.WxMpMessageHandler
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.springframework.stereotype.Component

@Component
class LocationHandler(
    private val mpAutoReplyService: MpAutoReplyService,
) : WxMpMessageHandler {
    override fun handle(
        wxMessage: WxMpXmlMessage,
        context: MutableMap<String, Any>,
        wxMpService: WxMpService,
        sessionManager: WxSessionManager,
    ): WxMpXmlOutMessage? {
        if (wxMessage.msgType != WxConsts.XmlMsgType.LOCATION) return null
        return mpAutoReplyService.replyForMessage(MpContextHolder.getAppId(), wxMessage)
    }
}
