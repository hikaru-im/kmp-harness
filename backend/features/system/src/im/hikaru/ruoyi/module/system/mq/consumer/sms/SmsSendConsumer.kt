package im.hikaru.ruoyi.module.system.mq.consumer.sms

import im.hikaru.ruoyi.module.system.mq.message.sms.SmsSendMessage
import im.hikaru.ruoyi.module.system.service.sms.SmsSendService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class SmsSendConsumer(
    private val smsSendService: SmsSendService,
) {
    @Async
    @EventListener
    fun onMessage(message: SmsSendMessage) = smsSendService.doSendSms(message)
}
