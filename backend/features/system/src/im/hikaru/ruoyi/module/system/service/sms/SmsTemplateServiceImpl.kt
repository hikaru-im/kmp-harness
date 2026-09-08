package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO
import im.hikaru.ruoyi.module.system.dal.mysql.sms.SmsTemplateDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CHANNEL_DISABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CHANNEL_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_TEMPLATE_API_AUDIT_CHECKING
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_TEMPLATE_API_AUDIT_FAIL
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_TEMPLATE_API_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_TEMPLATE_API_NOT_FOUND
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_TEMPLATE_CODE_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_TEMPLATE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SmsTemplateServiceImpl(
    private val smsChannelService: SmsChannelService,
) : SmsTemplateService {
    override fun createSmsTemplate(req: SmsTemplateSaveReqVO): Long {
        val channel = validateSmsChannel(requireNotNull(req.channelId))
        validateCodeUnique(null, requireNotNull(req.code))
        validateApiTemplate(requireNotNull(req.channelId), requireNotNull(req.apiTemplateId))
        return SmsTemplateDao.insert(requireNotNull(BeanUtils.toBean(req, SmsTemplateDO::class.java)).apply {
            params = parseTemplateContentParams(requireNotNull(content))
            channelCode = channel.code
        })
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.SMS_TEMPLATE], allEntries = true)
    override fun updateSmsTemplate(req: SmsTemplateSaveReqVO) {
        val id = requireNotNull(req.id)
        validateExists(id)
        val channel = validateSmsChannel(requireNotNull(req.channelId))
        validateCodeUnique(id, requireNotNull(req.code))
        validateApiTemplate(requireNotNull(req.channelId), requireNotNull(req.apiTemplateId))
        SmsTemplateDao.updateById(requireNotNull(BeanUtils.toBean(req, SmsTemplateDO::class.java)).apply {
            params = parseTemplateContentParams(requireNotNull(content))
            channelCode = channel.code
        })
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.SMS_TEMPLATE], allEntries = true)
    override fun deleteSmsTemplate(id: Long) {
        validateExists(id)
        SmsTemplateDao.deleteById(id)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.SMS_TEMPLATE], allEntries = true)
    override fun deleteSmsTemplateList(ids: List<Long>) {
        SmsTemplateDao.deleteByIds(ids)
    }

    override fun getSmsTemplate(id: Long): SmsTemplateDO? = SmsTemplateDao.selectById(id)

    @Cacheable(cacheNames = [RedisKeyConstants.SMS_TEMPLATE], key = "#code", unless = "#result == null")
    override fun getSmsTemplateByCodeFromCache(code: String): SmsTemplateDO? = SmsTemplateDao.selectByCode(code)

    override fun getSmsTemplatePage(req: SmsTemplatePageReqVO): PageResult<SmsTemplateDO> = SmsTemplateDao.selectPage(req)

    override fun getSmsTemplateListByStatus(status: Int): List<SmsTemplateDO> = SmsTemplateDao.selectListByStatus(status)

    override fun getSmsTemplateCountByChannelId(channelId: Long): Long = SmsTemplateDao.selectCountByChannelId(channelId)

    override fun formatSmsTemplateContent(content: String, params: Map<String, Any?>): String =
        PARAM_PATTERN.replace(content) { match -> params[match.groupValues[1]]?.toString() ?: match.value }

    internal fun parseTemplateContentParams(content: String): List<String> =
        PARAM_PATTERN.findAll(content).map { it.groupValues[1] }.toList()

    private fun validateExists(id: Long) {
        if (SmsTemplateDao.selectById(id) == null) throw exception(SMS_TEMPLATE_NOT_EXISTS)
    }

    internal fun validateSmsChannel(channelId: Long): SmsChannelDO {
        val channel = smsChannelService.getSmsChannel(channelId) ?: throw exception(SMS_CHANNEL_NOT_EXISTS)
        if (CommonStatusEnum.isDisable(channel.status)) throw exception(SMS_CHANNEL_DISABLE)
        return channel
    }

    internal fun validateCodeUnique(id: Long?, code: String) {
        val existing = SmsTemplateDao.selectByCode(code) ?: return
        if (id == null || existing.id != id) throw exception(SMS_TEMPLATE_CODE_DUPLICATE, code)
    }

    internal fun validateApiTemplate(channelId: Long, apiTemplateId: String) {
        val client = smsChannelService.getSmsClient(channelId) ?: throw exception(SMS_CHANNEL_NOT_EXISTS)
        val template = try {
            client.getSmsTemplate(apiTemplateId)
        } catch (ex: Exception) {
            throw exception(SMS_TEMPLATE_API_ERROR, rootMessage(ex))
        } ?: throw exception(SMS_TEMPLATE_API_NOT_FOUND)
        when (template.auditStatus) {
            SmsTemplateAuditStatusEnum.CHECKING.status -> throw exception(SMS_TEMPLATE_API_AUDIT_CHECKING)
            SmsTemplateAuditStatusEnum.FAIL.status -> throw exception(SMS_TEMPLATE_API_AUDIT_FAIL, template.auditReason.orEmpty())
            SmsTemplateAuditStatusEnum.SUCCESS.status -> Unit
            else -> throw exception(SMS_TEMPLATE_API_ERROR, "Unexpected audit status ${template.auditStatus}")
        }
    }

    private fun rootMessage(ex: Throwable): String = generateSequence(ex) { it.cause }.last().message ?: ex.toString()

    private companion object {
        val PARAM_PATTERN = Regex("\\{(.*?)}")
    }
}
