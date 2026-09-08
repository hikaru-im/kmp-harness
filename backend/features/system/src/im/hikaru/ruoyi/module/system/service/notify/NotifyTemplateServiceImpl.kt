package im.hikaru.ruoyi.module.system.service.notify

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO
import im.hikaru.ruoyi.module.system.dal.mysql.notify.NotifyTemplateDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_CODE_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.validation.annotation.Validated

@Service
@Validated
class NotifyTemplateServiceImpl : NotifyTemplateService {
    override fun createNotifyTemplate(req: NotifyTemplateSaveReqVO): Long {
        validateNotifyTemplateCodeDuplicate(null, requireNotNull(req.code))
        return NotifyTemplateDao.insert(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.NOTIFY_TEMPLATE], allEntries = true)
    override fun updateNotifyTemplate(req: NotifyTemplateSaveReqVO) {
        val id = requireNotNull(req.id)
        validateNotifyTemplateExists(id)
        validateNotifyTemplateCodeDuplicate(id, requireNotNull(req.code))
        NotifyTemplateDao.updateById(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.NOTIFY_TEMPLATE], allEntries = true)
    override fun deleteNotifyTemplate(id: Long) {
        validateNotifyTemplateExists(id)
        NotifyTemplateDao.deleteById(id)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.NOTIFY_TEMPLATE], allEntries = true)
    override fun deleteNotifyTemplateList(ids: Collection<Long>) {
        NotifyTemplateDao.deleteByIds(ids)
    }

    override fun getNotifyTemplate(id: Long): NotifyTemplateDO? = NotifyTemplateDao.selectById(id)

    @Cacheable(cacheNames = [RedisKeyConstants.NOTIFY_TEMPLATE], key = "#code", unless = "#result == null")
    override fun getNotifyTemplateByCodeFromCache(code: String): NotifyTemplateDO? = NotifyTemplateDao.selectByCode(code)

    override fun getNotifyTemplatePage(req: NotifyTemplatePageReqVO): PageResult<NotifyTemplateDO> = NotifyTemplateDao.selectPage(req)

    override fun getNotifyTemplateListByStatus(status: Int): List<NotifyTemplateDO> = NotifyTemplateDao.selectListByStatus(status)

    override fun formatNotifyTemplateContent(content: String, params: Map<String, Any?>): String =
        PARAM_PATTERN.replace(content) { match -> params[match.groupValues[1]]?.toString() ?: match.value }

    internal fun parseTemplateContentParams(content: String): List<String> =
        PARAM_PATTERN.findAll(content).map { it.groupValues[1] }.toList()

    internal fun validateNotifyTemplateCodeDuplicate(id: Long?, code: String) {
        val existing = NotifyTemplateDao.selectByCode(code) ?: return
        if (id == null || existing.id != id) {
            throw ServiceExceptionUtil.exception(NOTIFY_TEMPLATE_CODE_DUPLICATE, code)
        }
    }

    private fun validateNotifyTemplateExists(id: Long) {
        if (NotifyTemplateDao.selectById(id) == null) throw ServiceExceptionUtil.exception(NOTIFY_TEMPLATE_NOT_EXISTS)
    }

    private fun NotifyTemplateSaveReqVO.toEntity() = NotifyTemplateDO().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        code = this@toEntity.code
        type = this@toEntity.type
        nickname = this@toEntity.nickname
        content = this@toEntity.content
        params = parseTemplateContentParams(requireNotNull(content))
        status = this@toEntity.status
        remark = this@toEntity.remark
    }

    private companion object {
        val PARAM_PATTERN = Regex("\\{(.*?)}")
    }
}
