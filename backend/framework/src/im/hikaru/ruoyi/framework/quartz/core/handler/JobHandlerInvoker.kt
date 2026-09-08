package im.hikaru.ruoyi.framework.quartz.core.handler

import im.hikaru.ruoyi.framework.quartz.core.enums.JobDataKeyEnum
import im.hikaru.ruoyi.framework.quartz.core.service.JobLogFrameworkService
import jakarta.annotation.Resource
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.PersistJobDataAfterExecution
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.quartz.QuartzJobBean
import java.time.Duration
import java.time.LocalDateTime

/**
 * 基础 Job 调用者，负责调用 [JobHandler.execute] 执行任务 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：
 *  - Hutool LocalDateTimeUtil.between(a, b).toMillis() → java.time.Duration.between(a, b).toMillis()
 *  - Hutool ThreadUtil.sleep → Thread.sleep
 *  - Hutool ExceptionUtil.getRootCauseMessage → 手写递归
 *
 * @author 芋道源码
 */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
class JobHandlerInvoker : QuartzJobBean() {

    @Resource
    private lateinit var applicationContext: ApplicationContext

    @Resource
    private lateinit var jobLogFrameworkService: JobLogFrameworkService

    @Throws(JobExecutionException::class)
    override fun executeInternal(executionContext: JobExecutionContext) {
        // 第一步，获得 Job 数据
        val jobDataMap = executionContext.mergedJobDataMap
        val jobId = jobDataMap.getLong(JobDataKeyEnum.JOB_ID.name)
        val jobHandlerName = jobDataMap.getString(JobDataKeyEnum.JOB_HANDLER_NAME.name)
        val jobHandlerParam = jobDataMap.getString(JobDataKeyEnum.JOB_HANDLER_PARAM.name)
        val refireCount = executionContext.refireCount
        val retryCount = jobDataMap.getOrDefault(JobDataKeyEnum.JOB_RETRY_COUNT.name, 0) as Int
        val retryInterval = jobDataMap.getOrDefault(JobDataKeyEnum.JOB_RETRY_INTERVAL.name, 0) as Int

        // 第二步，执行任务
        var jobLogId: Long? = null
        val startTime = LocalDateTime.now()
        var data: String? = null
        var exception: Throwable? = null
        try {
            // 记录 Job 日志（初始）
            jobLogId = jobLogFrameworkService.createJobLog(jobId, startTime, jobHandlerName, jobHandlerParam, refireCount + 1)
            // 执行任务
            data = executeInternal(jobHandlerName, jobHandlerParam)
        } catch (ex: Throwable) {
            exception = ex
        }

        // 第三步，记录执行日志
        updateJobLogResultAsync(jobLogId, startTime, data, exception, executionContext)

        // 第四步，处理有异常的情况
        exception?.let { handleException(it, refireCount, retryCount, retryInterval) }
    }

    @Throws(Exception::class)
    private fun executeInternal(jobHandlerName: String, jobHandlerParam: String?): String? {
        // 获得 JobHandler 对象
        val jobHandler = applicationContext.getBean(jobHandlerName, JobHandler::class.java)
        requireNotNull(jobHandler) { "JobHandler 不会为空" }
        // 执行任务
        return jobHandler.execute(jobHandlerParam ?: "")
    }

    private fun updateJobLogResultAsync(
        jobLogId: Long?, startTime: LocalDateTime, data: String?, exception: Throwable?,
        executionContext: JobExecutionContext,
    ) {
        val endTime = LocalDateTime.now()
        // 处理是否成功
        val success = exception == null
        val resultData = if (!success) getRootCauseMessage(exception) else data
        // 更新日志
        try {
            jobLogFrameworkService.updateJobLogResultAsync(
                jobLogId!!, endTime,
                Duration.between(startTime, endTime).toMillis().toInt(),
                success, resultData,
            )
        } catch (ex: Exception) {
            log.error(
                "[executeInternal][Job({}) logId({}) 记录执行日志失败({}/{})]",
                executionContext.jobDetail.key, jobLogId, success, resultData,
            )
        }
    }

    @Throws(JobExecutionException::class)
    private fun handleException(
        exception: Throwable, refireCount: Int, retryCount: Int, retryInterval: Int,
    ) {
        // 情况一：如果到达重试上限，则直接抛出异常即可
        if (refireCount >= retryCount) {
            throw JobExecutionException(exception)
        }
        // 情况二：如果未到达重试上限，则 sleep 一定间隔时间，然后重试
        if (retryInterval > 0) {
            Thread.sleep(retryInterval.toLong())
        }
        // 第二个参数，refireImmediately = true，表示立即重试
        throw JobExecutionException(exception, true)
    }

    companion object {
        private val log = LoggerFactory.getLogger(JobHandlerInvoker::class.java)

        private fun getRootCauseMessage(throwable: Throwable?): String {
            var current = throwable
            while (current?.cause != null && current.cause !== current) {
                current = current.cause
            }
            return current?.message ?: ""
        }
    }
}
