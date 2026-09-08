package im.hikaru.ruoyi.module.infra.service.job

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.quartz.core.scheduler.SchedulerManager
import im.hikaru.ruoyi.framework.quartz.core.util.CronUtils
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobDO
import im.hikaru.ruoyi.module.infra.dal.mysql.job.JobDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_CHANGE_STATUS_EQUALS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_CHANGE_STATUS_INVALID
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_CRON_EXPRESSION_VALID
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_HANDLER_BEAN_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_HANDLER_BEAN_TYPE_ERROR
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_HANDLER_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.JOB_UPDATE_ONLY_NORMAL_STATUS
import im.hikaru.ruoyi.module.infra.enums.job.JobStatusEnum
import org.quartz.SchedulerException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class JobServiceImpl(
    private val schedulerManager: SchedulerManager,
    private val applicationContext: ApplicationContext,
) : JobService {

    @Transactional(rollbackFor = [Exception::class])
    @Throws(SchedulerException::class)
    override fun createJob(createReqVO: JobSaveReqVO): Long {
        val cronExpression = requireNotNull(createReqVO.cronExpression)
        val handlerName = requireNotNull(createReqVO.handlerName)
        validateCronExpression(cronExpression)
        if (JobDao.selectByHandlerName(handlerName) != null) {
            throw exception(JOB_HANDLER_EXISTS)
        }
        validateJobHandlerExists(handlerName)

        val job = requireNotNull(BeanUtils.toBean(createReqVO, JobDO::class.java)).apply {
            status = JobStatusEnum.INIT.status
            if (monitorTimeout == null) monitorTimeout = 0
        }
        val id = JobDao.insert(job)
        schedulerManager.addJob(
            id,
            handlerName,
            job.handlerParam,
            cronExpression,
            job.retryCount,
            job.retryInterval,
        )
        JobDao.updateById(JobDO().apply {
            this.id = id
            status = JobStatusEnum.NORMAL.status
        })
        return id
    }

    @Transactional(rollbackFor = [Exception::class])
    @Throws(SchedulerException::class)
    override fun updateJob(updateReqVO: JobSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        val cronExpression = requireNotNull(updateReqVO.cronExpression)
        val handlerName = requireNotNull(updateReqVO.handlerName)
        validateCronExpression(cronExpression)
        val existing = validateJobExists(id)
        if (existing.status != JobStatusEnum.NORMAL.status) {
            throw exception(JOB_UPDATE_ONLY_NORMAL_STATUS)
        }
        validateJobHandlerExists(handlerName)

        val updated = requireNotNull(BeanUtils.toBean(updateReqVO, JobDO::class.java)).apply {
            if (monitorTimeout == null) monitorTimeout = 0
        }
        JobDao.updateById(updated)
        schedulerManager.updateJob(
            requireNotNull(existing.handlerName),
            updated.handlerParam,
            cronExpression,
            updated.retryCount,
            updated.retryInterval,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    @Throws(SchedulerException::class)
    override fun updateJobStatus(id: Long, status: Int) {
        if (status != JobStatusEnum.NORMAL.status && status != JobStatusEnum.STOP.status) {
            throw exception(JOB_CHANGE_STATUS_INVALID)
        }
        val job = validateJobExists(id)
        if (job.status == status) {
            throw exception(JOB_CHANGE_STATUS_EQUALS)
        }
        JobDao.updateById(JobDO().apply {
            this.id = id
            this.status = status
        })
        val handlerName = requireNotNull(job.handlerName)
        if (status == JobStatusEnum.NORMAL.status) {
            schedulerManager.resumeJob(handlerName)
        } else {
            schedulerManager.pauseJob(handlerName)
        }
    }

    @Throws(SchedulerException::class)
    override fun triggerJob(id: Long) {
        val job = validateJobExists(id)
        schedulerManager.triggerJob(
            requireNotNull(job.id),
            requireNotNull(job.handlerName),
            job.handlerParam,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    @Throws(SchedulerException::class)
    override fun syncJob() {
        JobDao.selectList().forEach { job ->
            val handlerName = requireNotNull(job.handlerName)
            schedulerManager.deleteJob(handlerName)
            schedulerManager.addJob(
                requireNotNull(job.id),
                handlerName,
                job.handlerParam,
                requireNotNull(job.cronExpression),
                job.retryCount,
                job.retryInterval,
            )
            if (job.status == JobStatusEnum.STOP.status) {
                schedulerManager.pauseJob(handlerName)
            }
            log.info("[syncJob] Synchronized job id={} handler={}", job.id, handlerName)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    @Throws(SchedulerException::class)
    override fun deleteJob(id: Long) {
        val job = validateJobExists(id)
        JobDao.deleteById(id)
        schedulerManager.deleteJob(requireNotNull(job.handlerName))
    }

    @Transactional(rollbackFor = [Exception::class])
    @Throws(SchedulerException::class)
    override fun deleteJobList(ids: List<Long>) {
        val jobs = JobDao.selectByIds(ids)
        JobDao.deleteByIds(ids)
        jobs.forEach { schedulerManager.deleteJob(requireNotNull(it.handlerName)) }
    }

    override fun getJob(id: Long): JobDO? = JobDao.selectById(id)

    override fun getJobPage(pageReqVO: JobPageReqVO): PageResult<JobDO> = JobDao.selectPage(pageReqVO)

    private fun validateJobExists(id: Long): JobDO =
        JobDao.selectById(id) ?: throw exception(JOB_NOT_EXISTS)

    private fun validateCronExpression(cronExpression: String) {
        if (!CronUtils.isValid(cronExpression)) {
            throw exception(JOB_CRON_EXPRESSION_VALID)
        }
    }

    private fun validateJobHandlerExists(handlerName: String) {
        val handler = try {
            applicationContext.getBean(handlerName)
        } catch (_: NoSuchBeanDefinitionException) {
            throw exception(JOB_HANDLER_BEAN_NOT_EXISTS)
        }
        if (handler !is JobHandler) {
            throw exception(JOB_HANDLER_BEAN_TYPE_ERROR)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(JobServiceImpl::class.java)
    }
}
