package im.hikaru.ruoyi.module.system.mq.producer.mail

import im.hikaru.ruoyi.module.system.mq.message.mail.MailSendMessage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

interface MailDispatchPublisher {
    fun publish(message: MailSendMessage)
}

@Component
class MailProducer(
    private val eventPublisher: ApplicationEventPublisher,
) : MailDispatchPublisher {
    override fun publish(message: MailSendMessage) {
        eventPublisher.publishEvent(message)
    }
}
