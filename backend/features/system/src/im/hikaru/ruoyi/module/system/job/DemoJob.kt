package im.hikaru.ruoyi.module.system.job

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.job.TenantJob
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserDao
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DemoJob : JobHandler {
    @TenantJob
    override fun execute(param: String): String {
        val tenantId = TenantContextHolder.getTenantId()
        val count = AdminUserDao.selectCount()
        log.info("Demo job executed for tenant {} with {} users; param={}", tenantId, count, param)
        return "User count: $count"
    }

    companion object {
        private val log = LoggerFactory.getLogger(DemoJob::class.java)
    }
}
