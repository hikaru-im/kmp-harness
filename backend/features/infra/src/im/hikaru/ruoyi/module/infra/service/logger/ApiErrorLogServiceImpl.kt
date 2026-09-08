package im.hikaru.ruoyi.module.infra.service.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiErrorLogDO
import im.hikaru.ruoyi.module.infra.dal.mysql.logger.ApiErrorLogDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.API_ERROR_LOG_NOT_FOUND
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.API_ERROR_LOG_PROCESSED
import im.hikaru.ruoyi.module.infra.enums.logger.ApiErrorLogProcessStatusEnum
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class ApiErrorLogServiceImpl : ApiErrorLogService {

    override fun createApiErrorLog(createDTO: ApiErrorLogCreateReqDTO) {
        val errorLog = requireNotNull(BeanUtils.toBean(createDTO, ApiErrorLogDO::class.java)).apply {
            processStatus = ApiErrorLogProcessStatusEnum.INIT.status
            requestParams = requestParams?.take(REQUEST_PARAMS_MAX_LENGTH)
        }
        try {
            if (TenantContextHolder.getTenantId() != null) {
                ApiErrorLogDao.insert(errorLog)
            } else {
                TenantUtils.executeIgnore(Runnable { ApiErrorLogDao.insert(errorLog) })
            }
        } catch (ex: Exception) {
            log.error("[createApiErrorLog] Failed to persist API error log", ex)
        }
    }

    override fun updateApiErrorLogProcess(id: Long, processStatus: Int, processUserId: Long?) {
        val errorLog = ApiErrorLogDao.selectById(id) ?: throw exception(API_ERROR_LOG_NOT_FOUND)
        if (errorLog.processStatus != ApiErrorLogProcessStatusEnum.INIT.status) {
            throw exception(API_ERROR_LOG_PROCESSED)
        }
        ApiErrorLogDao.updateById(ApiErrorLogDO().apply {
            this.id = id
            this.processStatus = processStatus
            this.processUserId = processUserId
            this.processTime = LocalDateTime.now().toKotlinLocalDateTime()
        })
    }

    override fun getApiErrorLog(id: Long): ApiErrorLogDO? = ApiErrorLogDao.selectById(id)

    override fun getApiErrorLogPage(pageReqVO: ApiErrorLogPageReqVO): PageResult<ApiErrorLogDO> =
        ApiErrorLogDao.selectPage(pageReqVO)

    override fun cleanErrorLog(exceedDay: Int, deleteLimit: Int): Int {
        val expireDate = LocalDateTime.now().minusDays(exceedDay.toLong()).toKotlinLocalDateTime()
        var count = 0
        repeat(Short.MAX_VALUE.toInt()) {
            val deleted = ApiErrorLogDao.deleteByCreateTimeLt(expireDate, deleteLimit)
            count += deleted
            if (deleted < deleteLimit) return count
        }
        return count
    }

    private companion object {
        const val REQUEST_PARAMS_MAX_LENGTH = 8000
        val log = LoggerFactory.getLogger(ApiErrorLogServiceImpl::class.java)
    }
}
