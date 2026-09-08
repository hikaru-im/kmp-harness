package im.hikaru.ruoyi.module.pay.service.channel

import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.PayChannelCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.PayChannelUpdateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import jakarta.validation.Valid

interface PayChannelService {
    fun createChannel(@Valid createReqVO: PayChannelCreateReqVO): Long
    fun updateChannel(@Valid updateReqVO: PayChannelUpdateReqVO): Unit
    fun deleteChannel(id: Long): Unit
    fun getChannel(id: Long): PayChannelDO
    fun getChannelListByAppIds(appIds: Collection<Long>): List<PayChannelDO>
    fun getChannelByAppIdAndCode(appId: Long, code: String): PayChannelDO?
    fun validPayChannel(id: Long): PayChannelDO
    fun validPayChannel(appId: Long, code: String): PayChannelDO
    fun getEnableChannelList(appId: Long): List<PayChannelDO>
    fun getChannelListByAppId(appId: Long): List<PayChannelDO>
    fun getPayClient(id: Long): PayClient<*>
}
