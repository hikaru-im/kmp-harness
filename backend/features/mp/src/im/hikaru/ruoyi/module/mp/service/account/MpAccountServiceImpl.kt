package im.hikaru.ruoyi.module.mp.service.account

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountUpdateReqVO
import im.hikaru.ruoyi.module.mp.convert.account.MpAccountConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.mysql.account.MpAccountDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.ACCOUNT_CLEAR_QUOTA_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.ACCOUNT_GENERATE_QR_CODE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.ACCOUNT_NOT_EXISTS
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_USERNAME_EXISTS
import jakarta.annotation.PostConstruct
import me.chanjar.weixin.common.error.WxErrorException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.DependsOn
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.util.concurrent.TimeUnit

@Service
@Validated
@DependsOn("springTransactionManager")
class MpAccountServiceImpl(
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
) : MpAccountService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()

    @Volatile
    private var accountCache: Map<String, MpAccountDO> = emptyMap()

    @PostConstruct
    override fun initLocalCache() {
        ignoringTenant {
            val accounts = try {
                MpAccountDao.selectList()
            } catch (ex: Throwable) {
                if (!isMissingAccountTable(ex)) throw ex
                logger.warn("mp_account has not been created yet; WeChat account cache stays empty")
                emptyList()
            }
            mpServiceFactory.init(accounts)
            accountCache = accounts.mapNotNull { account -> account.appId?.let { it to account } }.toMap()
        }
    }

    @Scheduled(initialDelay = 60, fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    fun refreshLocalCache() = ignoringTenant {
        val maxUpdateTime = accountCache.values.mapNotNull(MpAccountDO::updateTime).maxOrNull()
        if (accountCache.isEmpty() || maxUpdateTime == null || MpAccountDao.selectCountByUpdateTimeGt(maxUpdateTime) > 0) {
            initLocalCache()
        }
    }

    override fun createAccount(createReqVO: MpAccountCreateReqVO): Long {
        validateAppIdUnique(null, requireNotNull(createReqVO.appId))
        val id = MpAccountDao.insert(MpAccountConvert.convert(createReqVO))
        initLocalCache()
        return id
    }

    override fun updateAccount(updateReqVO: MpAccountUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateAccountExists(id)
        validateAppIdUnique(id, requireNotNull(updateReqVO.appId))
        MpAccountDao.updateById(MpAccountConvert.convert(updateReqVO))
        initLocalCache()
    }

    override fun deleteAccount(id: Long) {
        validateAccountExists(id)
        MpAccountDao.deleteById(id)
        initLocalCache()
    }

    override fun getAccount(id: Long): MpAccountDO? = MpAccountDao.selectById(id)
    override fun getAccountFromCache(appId: String): MpAccountDO? = accountCache[appId]
    override fun getAccountPage(pageReqVO: MpAccountPageReqVO): PageResult<MpAccountDO> = MpAccountDao.selectPage(pageReqVO)
    override fun getAccountList(): List<MpAccountDO> = MpAccountDao.selectList()

    override fun generateAccountQrCode(id: Long) {
        val account = validateAccountExists(id)
        val service = mpServiceFactory.getRequiredMpService(requireNotNull(account.appId))
        val qrCodeUrl = try {
            val ticket = service.qrcodeService.qrCodeCreateLastTicket("default")
            service.qrcodeService.qrCodePictureUrl(ticket.ticket)
        } catch (ex: Exception) {
            throw exception(ACCOUNT_GENERATE_QR_CODE_FAIL, wxErrorMessage(ex))
        }
        MpAccountDao.updateById(MpAccountDO().apply {
            this.id = id
            this.qrCodeUrl = qrCodeUrl
        })
        initLocalCache()
    }

    override fun clearAccountQuota(id: Long) {
        val account = validateAccountExists(id)
        try {
            mpServiceFactory.getRequiredMpService(requireNotNull(account.appId)).clearQuota(account.appId)
        } catch (ex: Exception) {
            throw exception(ACCOUNT_CLEAR_QUOTA_FAIL, wxErrorMessage(ex))
        }
    }

    private fun validateAccountExists(id: Long): MpAccountDO =
        MpAccountDao.selectById(id) ?: throw exception(ACCOUNT_NOT_EXISTS)

    internal fun validateAppIdUnique(id: Long?, appId: String) = ignoringTenant {
        MpAccountDao.selectByAppId(appId)?.let { account ->
            if (id == null || id != account.id) throw exception(USER_USERNAME_EXISTS)
        }
    }

    private fun <T> ignoringTenant(block: () -> T): T {
        val oldIgnore = TenantContextHolder.isIgnore()
        return try {
            TenantContextHolder.setIgnore(true)
            block()
        } finally {
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()

    private fun isMissingAccountTable(ex: Throwable): Boolean =
        generateSequence(ex) { it.cause }
            .mapNotNull(Throwable::message)
            .any { message ->
                message.contains("mp_account", ignoreCase = true) &&
                    (message.contains("does not exist", ignoreCase = true) ||
                        message.contains("not found", ignoreCase = true))
            }
}
