package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SyncPushPlatform
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncPushDeviceDao
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import kotlin.time.Clock

@Service
class SyncPushDeviceServiceImpl : SyncPushDeviceService {
    override fun register(userId: Long, token: String, platform: SyncPushPlatform) {
        AppSyncPushDeviceDao.register(
            context = context(userId),
            token = token.trim(),
            platform = platform,
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        )
    }

    override fun unregister(userId: Long, token: String) {
        AppSyncPushDeviceDao.unregister(context(userId), token.trim())
    }

    private fun context(userId: Long) = SyncCommandContext(
        tenantId = TenantContextHolder.getRequiredTenantId(),
        userId = userId,
    )
}
