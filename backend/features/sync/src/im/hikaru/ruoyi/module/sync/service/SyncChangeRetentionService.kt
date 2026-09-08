package im.hikaru.ruoyi.module.sync.service

import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeDao
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service

@Service
class SyncChangeRetentionService {
    fun pruneBefore(
        cutoff: LocalDateTime,
        batchSize: Int = 1_000,
        maxBatches: Int = 20,
    ): Int {
        require(batchSize > 0) { "batchSize must be positive" }
        require(maxBatches > 0) { "maxBatches must be positive" }
        var total = 0
        repeat(maxBatches) {
            val deleted = AppSyncChangeDao.pruneBefore(cutoff, batchSize)
            total += deleted
            if (deleted < batchSize) return total
        }
        return total
    }
}
