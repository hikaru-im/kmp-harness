package im.hikaru.ruoyi.module.system.job.token

import im.hikaru.ruoyi.framework.quartz.core.handler.JobHandler
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TokenCleanJob(
    private val oauth2TokenService: OAuth2TokenService,
) : JobHandler {
    @TenantIgnore
    override fun execute(param: String): String {
        val refreshCount = oauth2TokenService.cleanRefreshToken(RETAIN_DAYS, DELETE_LIMIT)
        val accessCount = oauth2TokenService.cleanAccessToken(RETAIN_DAYS, DELETE_LIMIT)
        log.info("[execute] removed {} refresh tokens and {} access tokens", refreshCount, accessCount)
        return "Removed $refreshCount refresh tokens and $accessCount access tokens"
    }

    companion object {
        private const val RETAIN_DAYS = 14
        private const val DELETE_LIMIT = 100
        private val log = LoggerFactory.getLogger(TokenCleanJob::class.java)
    }
}
