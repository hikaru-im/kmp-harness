package im.hikaru.ruoyi.module.infra.job.job

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.infra.service.job.JobLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Periodically removes scheduled-job logs older than the retention window. */
@Component
class JobLogCleanJob(
    private val jobLogService: JobLogService,
) : JobHandler {

    @TenantIgnore
    override fun execute(param: String): String {
        val count = jobLogService.cleanJobLog(RETAIN_DAYS, DELETE_LIMIT)
        log.info("[execute] removed {} scheduled-job logs", count)
        return "Removed $count scheduled-job logs"
    }

    companion object {
        private const val RETAIN_DAYS = 14
        private const val DELETE_LIMIT = 100
        private val log = LoggerFactory.getLogger(JobLogCleanJob::class.java)
    }
}
