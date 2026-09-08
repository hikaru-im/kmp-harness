package im.hikaru.ruoyi.module.system.mq.consumer.mail

import im.hikaru.ruoyi.module.system.mq.message.mail.MailSendMessage
import im.hikaru.ruoyi.module.system.service.mail.MailSendService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MailSendConsumer(
    private val mailSendService: MailSendService,
) {
    @Async
    @EventListener
    fun onMessage(message: MailSendMessage) {
        log.info("Processing mail send message: logId={}", message.logId)
        mailSendService.doSendMail(message)
    }

    private companion object {
        val log = LoggerFactory.getLogger(MailSendConsumer::class.java)
    }
}
