package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO
import im.hikaru.ruoyi.module.system.dal.mysql.mail.MailTemplateDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_TEMPLATE_CODE_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_TEMPLATE_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MailTemplateServiceImpl : MailTemplateService {
    override fun createMailTemplate(req: MailTemplateSaveReqVO): Long {
        validateCodeUnique(null, requireNotNull(req.code))
        return MailTemplateDao.insert(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.MAIL_TEMPLATE], allEntries = true)
    override fun updateMailTemplate(req: MailTemplateSaveReqVO) {
        val id = requireNotNull(req.id)
        validateMailTemplateExists(id)
        validateCodeUnique(id, requireNotNull(req.code))
        MailTemplateDao.updateById(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.MAIL_TEMPLATE], allEntries = true)
    override fun deleteMailTemplate(id: Long) {
        validateMailTemplateExists(id)
        MailTemplateDao.deleteById(id)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.MAIL_TEMPLATE], allEntries = true)
    override fun deleteMailTemplateList(ids: Collection<Long>) {
        MailTemplateDao.deleteByIds(ids)
    }

    override fun getMailTemplate(id: Long): MailTemplateDO? = MailTemplateDao.selectById(id)

    @Cacheable(cacheNames = [RedisKeyConstants.MAIL_TEMPLATE], key = "#code", unless = "#result == null")
    override fun getMailTemplateByCodeFromCache(code: String): MailTemplateDO? = MailTemplateDao.selectByCode(code)

    override fun getMailTemplatePage(req: MailTemplatePageReqVO): PageResult<MailTemplateDO> = MailTemplateDao.selectPage(req)

    override fun getMailTemplateList(): List<MailTemplateDO> = MailTemplateDao.selectList()

    override fun getMailTemplateListByStatus(status: Int): List<MailTemplateDO> = MailTemplateDao.selectListByStatus(status)

    override fun formatMailTemplateContent(content: String, params: Map<String, Any?>): String {
        var formatted = PARAM_PATTERN.replace(content) { match ->
            params[match.groupValues[1]]?.toString() ?: match.value
        }
        formatted = formatted
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        formatted = CODE_BLOCK_PATTERN.replace(formatted) { match ->
            "<pre style=\"background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto;\"><code>${match.groupValues[1]}</code></pre>"
        }
        return OUTER_PRE_PATTERN.replace(formatted) { match -> "<div>${match.groupValues[1]}</div>" }
    }

    override fun getMailTemplateCountByAccountId(accountId: Long): Long = MailTemplateDao.selectCountByAccountId(accountId)

    internal fun validateCodeUnique(id: Long?, code: String) {
        val existing = MailTemplateDao.selectByCode(code) ?: return
        if (id == null || existing.id != id) throw ServiceExceptionUtil.exception(MAIL_TEMPLATE_CODE_EXISTS, code)
    }

    internal fun parseTemplateTitleAndContentParams(title: String, content: String): List<String> =
        (PARAM_PATTERN.findAll(title).map { it.groupValues[1] } +
            PARAM_PATTERN.findAll(content).map { it.groupValues[1] })
            .distinct()
            .toList()

    private fun validateMailTemplateExists(id: Long) {
        if (MailTemplateDao.selectById(id) == null) throw ServiceExceptionUtil.exception(MAIL_TEMPLATE_NOT_EXISTS)
    }

    private fun MailTemplateSaveReqVO.toEntity() = MailTemplateDO().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        code = this@toEntity.code
        accountId = this@toEntity.accountId
        nickname = this@toEntity.nickname
        title = this@toEntity.title
        content = this@toEntity.content
        params = parseTemplateTitleAndContentParams(requireNotNull(title), requireNotNull(content))
        status = this@toEntity.status
        remark = this@toEntity.remark
    }

    private companion object {
        val PARAM_PATTERN = Regex("\\{(.*?)}")
        val CODE_BLOCK_PATTERN = Regex("<pre\\s*.*?><code\\s*.*?>(.*?)</code></pre>", RegexOption.DOT_MATCHES_ALL)
        val OUTER_PRE_PATTERN = Regex("<pre[^>]*>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)
    }
}
