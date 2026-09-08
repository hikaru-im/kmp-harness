package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SyncChangeNotification
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeDao
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.time.Clock

@Service
class DatabaseSyncChangeWriter(
    private val notifiers: List<SyncChangeNotifier>,
) : SyncChangeWriter {
    override fun append(
        context: SyncCommandContext,
        resource: String,
        aggregateId: String,
        operation: SyncChangeOperation,
        aggregateVersion: Long,
        payload: Any,
    ): Long {
        val cursor = AppSyncChangeDao.insert(
            context = context,
            resource = resource,
            aggregateId = aggregateId,
            operation = operation,
            aggregateVersion = aggregateVersion,
            payload = JsonUtils.toJsonString(payload),
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        )
        notifyAfterCommit(context, SyncChangeNotification(resource, cursor))
        return cursor
    }

    private fun notifyAfterCommit(
        context: SyncCommandContext,
        notification: SyncChangeNotification,
    ) {
        val notify = { notifySafely(context, notification) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notify()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = notify()
        })
    }

    private fun notifySafely(
        context: SyncCommandContext,
        notification: SyncChangeNotification,
    ) {
        notifiers.forEach { notifier ->
            runCatching { notifier.notifyChange(context, notification) }
                .onFailure { failure ->
                    log.warn(
                        "Failed to publish sync change notification for tenant={}, user={}, resource={}, cursor={}",
                        context.tenantId,
                        context.userId,
                        notification.resource,
                        notification.cursor,
                        failure,
                    )
                }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(DatabaseSyncChangeWriter::class.java)
    }
}
