package im.hikaru.ruoyi.module.infra.job.logger

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.infra.service.logger.ApiErrorLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Periodically removes API error logs older than the retention window. */
@Component
class ErrorLogCleanJob(
    private val apiErrorLogService: ApiErrorLogService,
) : JobHandler {

    @TenantIgnore
    override fun execute(param: String): String {
        val count = apiErrorLogService.cleanErrorLog(RETAIN_DAYS, DELETE_LIMIT)
        log.info("[execute] removed {} API error logs", count)
        return "Removed $count API error logs"
    }

    companion object {
        private const val RETAIN_DAYS = 14
        private const val DELETE_LIMIT = 100
        private val log = LoggerFactory.getLogger(ErrorLogCleanJob::class.java)
    }
}
