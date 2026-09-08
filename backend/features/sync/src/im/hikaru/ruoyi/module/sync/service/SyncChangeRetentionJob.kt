package im.hikaru.ruoyi.module.sync.service

import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SyncChangeRetentionJob(
    private val retentionService: SyncChangeRetentionService,
    @param:Value("\${yudao.sync.change-retention-days:30}")
    private val retentionDays: Long,
    @param:Value("\${yudao.sync.change-retention-batch-size:1000}")
    private val batchSize: Int,
    @param:Value("\${yudao.sync.change-retention-max-batches:20}")
    private val maxBatches: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(retentionDays > 0) { "change-retention-days must be positive" }
    }

    @Scheduled(cron = "\${yudao.sync.change-retention-cron:0 30 3 * * ?}")
    fun pruneExpiredChanges() {
        val cutoff = LocalDateTime.now().minusDays(retentionDays).toKotlinLocalDateTime()
        val deleted = retentionService.pruneBefore(cutoff, batchSize, maxBatches)
        if (deleted > 0) logger.info("Pruned {} expired App sync changes", deleted)
    }
}
