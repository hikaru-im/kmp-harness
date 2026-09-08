package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
import im.hikaru.ruoyi.module.system.dal.mysql.sms.SmsChannelDao
import im.hikaru.ruoyi.module.system.dal.mysql.sms.SmsTemplateDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CHANNEL_HAS_CHILDREN
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CHANNEL_NOT_EXISTS
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClient
import im.hikaru.ruoyi.module.system.framework.sms.core.client.SmsClientFactory
import im.hikaru.ruoyi.module.system.framework.sms.core.property.SmsChannelProperties
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SmsChannelServiceImpl(
    private val smsClientFactory: SmsClientFactory,
) : SmsChannelService {
    override fun createSmsChannel(req: SmsChannelSaveReqVO): Long {
        val channel = requireNotNull(BeanUtils.toBean(req, SmsChannelDO::class.java))
        val id = SmsChannelDao.insert(channel)
        refreshClient(channel)
        return id
    }

    override fun updateSmsChannel(req: SmsChannelSaveReqVO) {
        validateExists(requireNotNull(req.id))
        val channel = requireNotNull(BeanUtils.toBean(req, SmsChannelDO::class.java))
        SmsChannelDao.updateById(channel)
        refreshClient(requireNotNull(SmsChannelDao.selectById(requireNotNull(req.id))))
    }

    override fun deleteSmsChannel(id: Long) {
        validateExists(id)
        if (SmsTemplateDao.selectCountByChannelId(id) > 0) throw exception(SMS_CHANNEL_HAS_CHILDREN)
        SmsChannelDao.deleteById(id)
    }

    override fun deleteSmsChannelList(ids: List<Long>) {
        ids.forEach { id ->
            validateExists(id)
            if (SmsTemplateDao.selectCountByChannelId(id) > 0) throw exception(SMS_CHANNEL_HAS_CHILDREN)
        }
        ids.forEach(SmsChannelDao::deleteById)
    }

    override fun getSmsChannel(id: Long): SmsChannelDO? = SmsChannelDao.selectById(id)

    override fun getSmsChannelList(): List<SmsChannelDO> = SmsChannelDao.selectList()

    override fun getSmsChannelPage(req: SmsChannelPageReqVO): PageResult<SmsChannelDO> = SmsChannelDao.selectPage(req)

    override fun getSmsClient(id: Long): SmsClient? {
        smsClientFactory.getSmsClient(id)?.let { return it }
        return SmsChannelDao.selectById(id)?.let(::refreshClient)
    }

    override fun getSmsClient(code: String): SmsClient? {
        smsClientFactory.getSmsClient(code)?.let { return it }
        return SmsChannelDao.selectByCode(code)?.let(::refreshClient)
    }

    private fun validateExists(id: Long): SmsChannelDO =
        SmsChannelDao.selectById(id) ?: throw exception(SMS_CHANNEL_NOT_EXISTS)

    private fun refreshClient(channel: SmsChannelDO): SmsClient = smsClientFactory.createOrUpdateSmsClient(
        SmsChannelProperties(
            id = channel.id,
            signature = channel.signature,
            code = channel.code,
            apiKey = channel.apiKey,
            apiSecret = channel.apiSecret,
            callbackUrl = channel.callbackUrl,
        ),
    )
}
