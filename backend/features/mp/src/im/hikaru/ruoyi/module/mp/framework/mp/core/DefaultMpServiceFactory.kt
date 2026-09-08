package im.hikaru.ruoyi.module.mp.framework.mp.core

import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
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
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.mp.api.WxMpMessageRouter
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl
import me.chanjar.weixin.mp.constant.WxMpEventConstants

class DefaultMpServiceFactory(
    private val messageReceiveHandler: MessageReceiveHandler,
    private val kfSessionHandler: KfSessionHandler,
    private val storeCheckNotifyHandler: StoreCheckNotifyHandler,
    private val menuHandler: MenuHandler,
    private val nullHandler: NullHandler,
    private val subscribeHandler: SubscribeHandler,
    private val unsubscribeHandler: UnsubscribeHandler,
    private val locationHandler: LocationHandler,
    private val scanHandler: ScanHandler,
    private val messageAutoReplyHandler: MessageAutoReplyHandler,
) : MpServiceFactory {

    @Volatile
    private var appIdServices: Map<String, WxMpService> = emptyMap()

    @Volatile
    private var idServices: Map<Long, WxMpService> = emptyMap()

    @Volatile
    private var messageRouters: Map<String, WxMpMessageRouter> = emptyMap()

    override fun init(accounts: List<MpAccountDO>) {
        val servicesByAppId = linkedMapOf<String, WxMpService>()
        val servicesById = linkedMapOf<Long, WxMpService>()
        val routersByAppId = linkedMapOf<String, WxMpMessageRouter>()
        accounts.forEach { account ->
            val appId = requireNotNull(account.appId)
            val id = requireNotNull(account.id)
            val service = buildMpService(account)
            servicesByAppId[appId] = service
            servicesById[id] = service
            routersByAppId[appId] = buildMpMessageRouter(service)
        }
        appIdServices = servicesByAppId.toMap()
        idServices = servicesById.toMap()
        messageRouters = routersByAppId.toMap()
    }

    override fun getMpService(id: Long): WxMpService? = idServices[id]
    override fun getMpService(appId: String): WxMpService? = appIdServices[appId]
    override fun getMpMessageRouter(appId: String): WxMpMessageRouter? = messageRouters[appId]

    private fun buildMpService(account: MpAccountDO): WxMpService {
        val config = WxMpDefaultConfigImpl().apply {
            appId = account.appId
            secret = account.appSecret
            token = account.token
            aesKey = account.aesKey
        }
        return WxMpServiceImpl().apply { wxMpConfigStorage = config }
    }

    private fun buildMpMessageRouter(service: WxMpService): WxMpMessageRouter = WxMpMessageRouter(service).apply {
        rule().handler(messageReceiveHandler).next()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxMpEventConstants.CustomerService.KF_CREATE_SESSION).handler(kfSessionHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxMpEventConstants.CustomerService.KF_CLOSE_SESSION).handler(kfSessionHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxMpEventConstants.CustomerService.KF_SWITCH_SESSION).handler(kfSessionHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxMpEventConstants.POI_CHECK_NOTIFY).handler(storeCheckNotifyHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxConsts.MenuButtonType.CLICK).handler(menuHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxConsts.MenuButtonType.VIEW).handler(nullHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxConsts.EventType.SUBSCRIBE).handler(subscribeHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxConsts.EventType.UNSUBSCRIBE).handler(unsubscribeHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxConsts.EventType.LOCATION).handler(locationHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.LOCATION).handler(locationHandler).end()
        rule().async(false).msgType(WxConsts.XmlMsgType.EVENT)
            .event(WxConsts.EventType.SCAN).handler(scanHandler).end()
        rule().async(false).handler(messageAutoReplyHandler).end()
    }
}
