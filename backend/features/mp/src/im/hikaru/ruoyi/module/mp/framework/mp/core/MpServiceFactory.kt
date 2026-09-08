package im.hikaru.ruoyi.module.mp.framework.mp.core

import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import me.chanjar.weixin.mp.api.WxMpMessageRouter
import me.chanjar.weixin.mp.api.WxMpService

interface MpServiceFactory {
    fun init(accounts: List<MpAccountDO>)
    fun getMpService(id: Long): WxMpService?
    fun getMpService(appId: String): WxMpService?
    fun getMpMessageRouter(appId: String): WxMpMessageRouter?

    fun getRequiredMpService(id: Long): WxMpService =
        requireNotNull(getMpService(id)) { "No WxMpService configured for account id($id)" }

    fun getRequiredMpService(appId: String): WxMpService =
        requireNotNull(getMpService(appId)) { "No WxMpService configured for appId($appId)" }

    fun getRequiredMpMessageRouter(appId: String): WxMpMessageRouter =
        requireNotNull(getMpMessageRouter(appId)) { "No WxMpMessageRouter configured for appId($appId)" }
}
