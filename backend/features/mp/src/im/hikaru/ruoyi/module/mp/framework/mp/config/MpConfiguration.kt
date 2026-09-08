package im.hikaru.ruoyi.module.mp.framework.mp.config

import im.hikaru.ruoyi.module.mp.framework.mp.core.DefaultMpServiceFactory
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.handler.menu.MenuHandler
import im.hikaru.ruoyi.module.mp.service.handler.message.MessageAutoReplyHandler
import im.hikaru.ruoyi.module.mp.service.handler.message.MessageReceiveHandler
import im.hikaru.ruoyi.module.mp.service.handler.other.KfSessionHandler
import im.hikaru.ruoyi.module.mp.service.handler.other.NullHandler
import im.hikaru.ruoyi.module.mp.service.handler.other.ScanHandler
import im.hikaru.ruoyi.module.mp.service.handler.other.StoreCheckNotifyHandler
import im.hikaru.ruoyi.module.mp.service.handler.user.LocationHandler
import im.hikaru.ruoyi.module.mp.service.handler.user.SubscribeHandler
import im.hikaru.ruoyi.module.mp.service.handler.user.UnsubscribeHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MpConfiguration {
    @Bean
    fun mpServiceFactory(
        messageReceiveHandler: MessageReceiveHandler,
        kfSessionHandler: KfSessionHandler,
        storeCheckNotifyHandler: StoreCheckNotifyHandler,
        menuHandler: MenuHandler,
        nullHandler: NullHandler,
        subscribeHandler: SubscribeHandler,
        unsubscribeHandler: UnsubscribeHandler,
        locationHandler: LocationHandler,
        scanHandler: ScanHandler,
        messageAutoReplyHandler: MessageAutoReplyHandler,
    ): MpServiceFactory = DefaultMpServiceFactory(
        messageReceiveHandler,
        kfSessionHandler,
        storeCheckNotifyHandler,
        menuHandler,
        nullHandler,
        subscribeHandler,
        unsubscribeHandler,
        locationHandler,
        scanHandler,
        messageAutoReplyHandler,
    )
}
