package im.hikaru.ruoyi.module.pay.service.channel

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.*
import im.hikaru.ruoyi.module.pay.convert.channel.PayChannelConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.channel.PayChannelDO
import im.hikaru.ruoyi.module.pay.dal.mysql.channel.PayChannelDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.CHANNEL_EXIST_SAME_CHANNEL_ERROR
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.CHANNEL_IS_DISABLE
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.CHANNEL_NOT_FOUND
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientFactory
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.NonePayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin.WxPayClientConfig
import jakarta.validation.Validator
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayChannelServiceImpl(
    private val payClientFactory: PayClientFactory,
    private val validator: Validator,
) : PayChannelService {
    override fun createChannel(reqVO: PayChannelCreateReqVO): Long {
        val appId = requireNotNull(reqVO.appId); val code = requireNotNull(reqVO.code)
        if (PayChannelDao.selectByAppIdAndCode(appId, code) != null) throw exception(CHANNEL_EXIST_SAME_CHANNEL_ERROR)
        return PayChannelDao.insert(PayChannelConvert.convert(reqVO).apply { config = parseConfig(code, requireNotNull(reqVO.config)) })
    }
    override fun updateChannel(updateReqVO: PayChannelUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id); val old = validPayChannel(id)
        PayChannelDao.updateById(PayChannelConvert.convert(updateReqVO).apply { config = parseConfig(requireNotNull(old.code), requireNotNull(updateReqVO.config)) })
    }
    override fun deleteChannel(id: Long) { validPayChannel(id); PayChannelDao.deleteById(id) }
    override fun getChannel(id: Long): PayChannelDO = PayChannelDao.selectById(id) ?: throw exception(CHANNEL_NOT_FOUND)
    override fun getChannelListByAppIds(appIds: Collection<Long>): List<PayChannelDO> = PayChannelDao.selectListByAppIds(appIds)
    override fun getChannelByAppIdAndCode(appId: Long, code: String): PayChannelDO? = PayChannelDao.selectByAppIdAndCode(appId, code)
    override fun validPayChannel(id: Long): PayChannelDO = validate(PayChannelDao.selectById(id))
    override fun validPayChannel(appId: Long, code: String): PayChannelDO = validate(PayChannelDao.selectByAppIdAndCode(appId, code))
    override fun getEnableChannelList(appId: Long): List<PayChannelDO> = PayChannelDao.selectListByAppId(appId, CommonStatusEnum.ENABLE.status)
    override fun getChannelListByAppId(appId: Long): List<PayChannelDO> = PayChannelDao.selectListByAppId(appId)
    override fun getPayClient(id: Long): PayClient<*> { val channel = validPayChannel(id); return payClientFactory.createOrUpdatePayClient(id, channel.code.orEmpty(), channel.config) }

    private fun validate(channel: PayChannelDO?): PayChannelDO { if (channel == null) throw exception(CHANNEL_NOT_FOUND); if (CommonStatusEnum.isDisable(channel.status)) throw exception(CHANNEL_IS_DISABLE); return channel }
    private fun parseConfig(code: String, json: String): PayClientConfig {
        val config: PayClientConfig = when {
            PayChannelEnum.isAlipay(code) -> JsonUtils.parseObject2(json, AlipayPayClientConfig::class.java)
            PayChannelEnum.isWeixin(code) -> JsonUtils.parseObject2(json, WxPayClientConfig::class.java)
            else -> JsonUtils.parseObject2(json, NonePayClientConfig::class.java)
        } ?: throw exception(CHANNEL_NOT_FOUND)
        config.validate(validator); return config
    }
}
