package im.hikaru.ruoyi.module.mp.service.handler.menu

import im.hikaru.ruoyi.module.mp.framework.mp.core.context.MpContextHolder
import im.hikaru.ruoyi.module.mp.service.menu.MpMenuService
import me.chanjar.weixin.common.session.WxSessionManager
import me.chanjar.weixin.mp.api.WxMpMessageHandler
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.springframework.stereotype.Component

@Component
class MenuHandler(
    private val mpMenuService: MpMenuService,
) : WxMpMessageHandler {
    override fun handle(
        wxMessage: WxMpXmlMessage,
        context: MutableMap<String, Any>,
        wxMpService: WxMpService,
        sessionManager: WxSessionManager,
    ): WxMpXmlOutMessage? = mpMenuService.reply(
        MpContextHolder.getAppId(),
        wxMessage.eventKey,
        wxMessage.fromUser,
    )
}
