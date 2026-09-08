package im.hikaru.ruoyi.module.mp.service.handler.message

import im.hikaru.ruoyi.module.mp.framework.mp.core.context.MpContextHolder
import im.hikaru.ruoyi.module.mp.service.message.MpMessageService
import me.chanjar.weixin.common.session.WxSessionManager
import me.chanjar.weixin.mp.api.WxMpMessageHandler
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MessageReceiveHandler(
    private val mpMessageService: MpMessageService,
) : WxMpMessageHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(
        wxMessage: WxMpXmlMessage,
        context: MutableMap<String, Any>,
        wxMpService: WxMpService,
        sessionManager: WxSessionManager,
    ): WxMpXmlOutMessage? {
        logger.info("Received WeChat message: {}", wxMessage)
        mpMessageService.receiveMessage(wxMpService, MpContextHolder.getAppId(), wxMessage)
        return null
    }
}
