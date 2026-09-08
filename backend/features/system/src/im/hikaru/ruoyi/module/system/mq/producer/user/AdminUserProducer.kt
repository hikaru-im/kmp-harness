package im.hikaru.ruoyi.module.system.mq.producer.user

import im.hikaru.ruoyi.module.system.api.message.user.AdminUserProfileUpdateMessage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class AdminUserProducer(
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun sendUserProfileUpdateMessage(userId: Long, nickname: String?, avatar: String?) {
        eventPublisher.publishEvent(AdminUserProfileUpdateMessage().apply {
            this.userId = userId
            this.nickname = nickname
            this.avatar = avatar
        })
    }
}
