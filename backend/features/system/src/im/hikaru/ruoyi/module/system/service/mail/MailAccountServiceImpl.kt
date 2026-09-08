package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO
import im.hikaru.ruoyi.module.system.dal.mysql.mail.MailAccountDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_ACCOUNT_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_ACCOUNT_RELATE_TEMPLATE_EXISTS
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MailAccountServiceImpl(
    private val mailTemplateService: MailTemplateService,
) : MailAccountService {
    override fun createMailAccount(req: MailAccountSaveReqVO): Long = MailAccountDao.insert(req.toEntity())

    @CacheEvict(cacheNames = [RedisKeyConstants.MAIL_ACCOUNT], key = "#req.id")
    override fun updateMailAccount(req: MailAccountSaveReqVO) {
        val id = requireNotNull(req.id)
        validateMailAccountExists(id)
        MailAccountDao.updateById(req.toEntity())
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.MAIL_ACCOUNT], key = "#id")
    override fun deleteMailAccount(id: Long) {
        validateMailAccountExists(id)
        validateNoRelatedTemplate(id)
        MailAccountDao.deleteById(id)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.MAIL_ACCOUNT], allEntries = true)
    override fun deleteMailAccountList(ids: Collection<Long>) {
        ids.forEach(::validateNoRelatedTemplate)
        MailAccountDao.deleteByIds(ids)
    }

    override fun getMailAccount(id: Long): MailAccountDO? = MailAccountDao.selectById(id)

    @Cacheable(cacheNames = [RedisKeyConstants.MAIL_ACCOUNT], key = "#id", unless = "#result == null")
    override fun getMailAccountFromCache(id: Long): MailAccountDO? = getMailAccount(id)

    override fun getMailAccountPage(req: MailAccountPageReqVO): PageResult<MailAccountDO> = MailAccountDao.selectPage(req)

    override fun getMailAccountList(): List<MailAccountDO> = MailAccountDao.selectList()

    private fun validateMailAccountExists(id: Long) {
        if (MailAccountDao.selectById(id) == null) throw ServiceExceptionUtil.exception(MAIL_ACCOUNT_NOT_EXISTS)
    }

    private fun validateNoRelatedTemplate(id: Long) {
        if (mailTemplateService.getMailTemplateCountByAccountId(id) > 0) {
            throw ServiceExceptionUtil.exception(MAIL_ACCOUNT_RELATE_TEMPLATE_EXISTS)
        }
    }

    private fun MailAccountSaveReqVO.toEntity() = MailAccountDO().apply {
        id = this@toEntity.id
        mail = this@toEntity.mail
        username = this@toEntity.username
        password = this@toEntity.password
        host = this@toEntity.host
        port = this@toEntity.port
        sslEnable = this@toEntity.sslEnable
        starttlsEnable = this@toEntity.starttlsEnable
    }
}
