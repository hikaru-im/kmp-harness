package im.hikaru.ruoyi.module.infra.service.job

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log.JobLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobLogDO
import im.hikaru.ruoyi.module.infra.dal.mysql.job.JobLogDao
import im.hikaru.ruoyi.module.infra.enums.job.JobLogStatusEnum
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class JobLogServiceImpl : JobLogService {

    override fun createJobLog(
        jobId: Long,
        beginTime: LocalDateTime,
        jobHandlerName: String,
        jobHandlerParam: String?,
        executeIndex: Int,
    ): Long = JobLogDao.insert(JobLogDO().apply {
        this.jobId = jobId
        this.beginTime = beginTime.toKotlinLocalDateTime()
        handlerName = jobHandlerName
        handlerParam = jobHandlerParam
        this.executeIndex = executeIndex
        status = JobLogStatusEnum.RUNNING.status
    })

    @Async
    override fun updateJobLogResultAsync(
        logId: Long,
        endTime: LocalDateTime,
        duration: Int,
        success: Boolean,
        result: String?,
    ) {
        try {
            JobLogDao.updateById(JobLogDO().apply {
                id = logId
                this.endTime = endTime.toKotlinLocalDateTime()
                this.duration = duration
                status = if (success) JobLogStatusEnum.SUCCESS.status else JobLogStatusEnum.FAILURE.status
                this.result = result
            })
        } catch (ex: Exception) {
            log.error("[updateJobLogResultAsync] Failed to update job log {}", logId, ex)
        }
    }

    override fun getJobLog(id: Long): JobLogDO? = JobLogDao.selectById(id)

    override fun getJobLogPage(pageReqVO: JobLogPageReqVO): PageResult<JobLogDO> = JobLogDao.selectPage(pageReqVO)

    override fun cleanJobLog(exceedDay: Int, deleteLimit: Int): Int {
        val expireDate = LocalDateTime.now().minusDays(exceedDay.toLong()).toKotlinLocalDateTime()
        var count = 0
        repeat(Short.MAX_VALUE.toInt()) {
            val deleted = JobLogDao.deleteByCreateTimeLt(expireDate, deleteLimit)
            count += deleted
            if (deleted < deleteLimit) return count
        }
        return count
    }

    private companion object {
        val log = LoggerFactory.getLogger(JobLogServiceImpl::class.java)
    }
}
