package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClient

interface SmsChannelService {
    fun createSmsChannel(req: SmsChannelSaveReqVO): Long
    fun updateSmsChannel(req: SmsChannelSaveReqVO)
    fun deleteSmsChannel(id: Long)
    fun deleteSmsChannelList(ids: List<Long>)
    fun getSmsChannel(id: Long): SmsChannelDO?
    fun getSmsChannelList(): List<SmsChannelDO>
    fun getSmsChannelPage(req: SmsChannelPageReqVO): PageResult<SmsChannelDO>
    fun getSmsClient(id: Long): SmsClient?
    fun getSmsClient(code: String): SmsClient?
}
