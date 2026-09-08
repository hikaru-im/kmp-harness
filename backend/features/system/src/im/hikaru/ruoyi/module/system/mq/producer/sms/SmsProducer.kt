package im.hikaru.ruoyi.module.system.mq.producer.sms

import im.hikaru.ruoyi.module.system.mq.message.sms.SmsSendMessage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

interface SmsDispatchPublisher {
    fun publish(message: SmsSendMessage)
}

@Component
class SmsProducer(
    private val eventPublisher: ApplicationEventPublisher,
) : SmsDispatchPublisher {
    override fun publish(message: SmsSendMessage) = eventPublisher.publishEvent(message)
}
