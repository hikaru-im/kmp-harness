package im.hikaru.ruoyi.module.mp.service.handler.other

import me.chanjar.weixin.common.session.WxSessionManager
import me.chanjar.weixin.mp.api.WxMpMessageHandler
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.springframework.stereotype.Component

@Component
class KfSessionHandler : WxMpMessageHandler {
    override fun handle(
        wxMessage: WxMpXmlMessage,
        context: MutableMap<String, Any>,
        wxMpService: WxMpService,
        sessionManager: WxSessionManager,
    ): WxMpXmlOutMessage? = null
}
