package im.hikaru.ruoyi.framework.quartz.core.scheduler

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_IMPLEMENTED
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception0
import im.hikaru.ruoyi.framework.quartz.core.enums.JobDataKeyEnum
import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandlerInvoker
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey

/**
 * [Scheduler] 的管理器，负责创建任务 (迁移自 Java)
 *
 * @author 芋道源码
 */
class SchedulerManager(private val scheduler: Scheduler?) {

    /**
     * 添加 Job 到 Quartz 中
     */
    @Throws(SchedulerException::class)
    fun addJob(
        jobId: Long, jobHandlerName: String, jobHandlerParam: String?, cronExpression: String,
        retryCount: Int?, retryInterval: Int?,
    ) {
        validateScheduler()
        // 创建 JobDetail 对象
        val jobDetail: JobDetail = JobBuilder.newJob(JobHandlerInvoker::class.java)
            .usingJobData(JobDataKeyEnum.JOB_ID.name, jobId)
            .usingJobData(JobDataKeyEnum.JOB_HANDLER_NAME.name, jobHandlerName)
            .withIdentity(jobHandlerName).build()
        // 创建 Trigger 对象
        val trigger = buildTrigger(jobHandlerName, jobHandlerParam, cronExpression, retryCount, retryInterval)
        // 新增 Job 调度
        scheduler!!.scheduleJob(jobDetail, trigger)
    }

    /**
     * 更新 Job 到 Quartz
     */
    @Throws(SchedulerException::class)
    fun updateJob(
        jobHandlerName: String, jobHandlerParam: String?, cronExpression: String,
        retryCount: Int?, retryInterval: Int?,
    ) {
        validateScheduler()
        val newTrigger = buildTrigger(jobHandlerName, jobHandlerParam, cronExpression, retryCount, retryInterval)
        scheduler!!.rescheduleJob(TriggerKey(jobHandlerName), newTrigger)
    }

    /**
     * 删除 Quartz 中的 Job
     */
    @Throws(SchedulerException::class)
    fun deleteJob(jobHandlerName: String) {
        validateScheduler()
        scheduler!!.pauseTrigger(TriggerKey(jobHandlerName))
        scheduler.unscheduleJob(TriggerKey(jobHandlerName))
        scheduler.deleteJob(JobKey(jobHandlerName))
    }

    @Throws(SchedulerException::class)
    fun pauseJob(jobHandlerName: String) {
        validateScheduler()
        scheduler!!.pauseJob(JobKey(jobHandlerName))
    }

    @Throws(SchedulerException::class)
    fun resumeJob(jobHandlerName: String) {
        validateScheduler()
        scheduler!!.resumeJob(JobKey(jobHandlerName))
        scheduler.resumeTrigger(TriggerKey(jobHandlerName))
    }

    /**
     * 立即触发一次 Quartz 中的 Job
     */
    @Throws(SchedulerException::class)
    fun triggerJob(jobId: Long, jobHandlerName: String, jobHandlerParam: String?) {
        validateScheduler()
        val data = JobDataMap() // 无需重试，所以不设置 retryCount 和 retryInterval
        data[JobDataKeyEnum.JOB_ID.name] = jobId
        data[JobDataKeyEnum.JOB_HANDLER_NAME.name] = jobHandlerName
        data[JobDataKeyEnum.JOB_HANDLER_PARAM.name] = jobHandlerParam
        scheduler!!.triggerJob(JobKey(jobHandlerName), data)
    }

    private fun buildTrigger(
        jobHandlerName: String, jobHandlerParam: String?, cronExpression: String,
        retryCount: Int?, retryInterval: Int?,
    ): Trigger = TriggerBuilder.newTrigger()
        .withIdentity(jobHandlerName)
        .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
        .usingJobData(JobDataKeyEnum.JOB_HANDLER_PARAM.name, jobHandlerParam)
        .usingJobData(JobDataKeyEnum.JOB_RETRY_COUNT.name, retryCount)
        .usingJobData(JobDataKeyEnum.JOB_RETRY_INTERVAL.name, retryInterval)
        .build()

    private fun validateScheduler() {
        if (scheduler == null) {
            throw exception0(
                NOT_IMPLEMENTED.code,
                "[定时任务 - 已禁用][参考 https://doc.iocoder.cn/job/ 开启]",
            )
        }
    }
}
