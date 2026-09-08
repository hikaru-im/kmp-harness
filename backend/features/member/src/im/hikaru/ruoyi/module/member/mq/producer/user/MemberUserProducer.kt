package im.hikaru.ruoyi.module.member.mq.producer.user

import im.hikaru.ruoyi.module.member.api.message.user.MemberUserCreateMessage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class MemberUserProducer(
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun sendUserCreateMessage(userId: Long) {
        eventPublisher.publishEvent(MemberUserCreateMessage().apply { this.userId = userId })
    }
}
