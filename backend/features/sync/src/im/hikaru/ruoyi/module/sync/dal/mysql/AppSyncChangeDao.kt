package im.hikaru.ruoyi.module.sync.dal.mysql

import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.sync.service.SyncChangeOperation
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class AppSyncChangeRecord(
    val cursor: Long,
    val resource: String,
    val aggregateId: String,
    val operation: String,
    val aggregateVersion: Long,
    val payload: String,
)

data class AppSyncChangeBounds(
    val retentionCursor: Long,
    val highWatermark: Long,
)

private data class AppSyncChangeScope(
    val tenantId: Long,
    val userId: Long,
    val resource: String,
)

object AppSyncChangeDao {
    fun insert(
        context: SyncCommandContext,
        resource: String,
        aggregateId: String,
        operation: SyncChangeOperation,
        aggregateVersion: Long,
        payload: String,
        createdAt: LocalDateTime,
    ): Long = transaction {
        AppSyncChangeTable.insert {
            it[tenantId] = context.tenantId
            it[userId] = context.userId
            it[AppSyncChangeTable.resource] = resource
            it[AppSyncChangeTable.aggregateId] = aggregateId
            it[AppSyncChangeTable.operation] = operation.name
            it[AppSyncChangeTable.aggregateVersion] = aggregateVersion
            it[AppSyncChangeTable.payload] = payload
            it[AppSyncChangeTable.createdAt] = createdAt
        }.get(AppSyncChangeTable.cursor)
    }

    fun selectAfter(
        context: SyncCommandContext,
        resource: String,
        cursor: Long,
        limit: Int,
    ): List<AppSyncChangeRecord> = transaction {
        AppSyncChangeTable.selectAll()
            .where {
                listOf(
                    AppSyncChangeTable.tenantId eq context.tenantId,
                    AppSyncChangeTable.userId eq context.userId,
                    AppSyncChangeTable.resource eq resource,
                    AppSyncChangeTable.cursor greater cursor,
                ).compoundAnd()
            }
            .orderBy(AppSyncChangeTable.cursor, SortOrder.ASC)
            .limit(limit)
            .map(::toRecord)
    }

    fun selectBounds(
        context: SyncCommandContext,
        resource: String,
    ): AppSyncChangeBounds = transaction {
        val retentionCursor = AppSyncChangeRetentionTable.selectAll()
            .where {
                listOf(
                    AppSyncChangeRetentionTable.tenantId eq context.tenantId,
                    AppSyncChangeRetentionTable.userId eq context.userId,
                    AppSyncChangeRetentionTable.resource eq resource,
                ).compoundAnd()
            }
            .limit(1)
            .firstOrNull()
            ?.get(AppSyncChangeRetentionTable.retentionCursor)
            ?: 0L
        val latestCursor = AppSyncChangeTable.selectAll()
            .where {
                listOf(
                    AppSyncChangeTable.tenantId eq context.tenantId,
                    AppSyncChangeTable.userId eq context.userId,
                    AppSyncChangeTable.resource eq resource,
                ).compoundAnd()
            }
            .orderBy(AppSyncChangeTable.cursor, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(AppSyncChangeTable.cursor)
            ?: 0L
        AppSyncChangeBounds(
            retentionCursor = retentionCursor,
            highWatermark = maxOf(retentionCursor, latestCursor),
        )
    }

    fun pruneBefore(cutoff: LocalDateTime, limit: Int): Int {
        require(limit > 0) { "limit must be positive" }
        return transaction {
            val expired = AppSyncChangeTable.selectAll()
                .where { AppSyncChangeTable.createdAt less cutoff }
                .orderBy(AppSyncChangeTable.cursor, SortOrder.ASC)
                .limit(limit)
                .toList()
            if (expired.isEmpty()) return@transaction 0
            val updatedAt = java.time.LocalDateTime.now().toKotlinLocalDateTime()

            expired.groupBy { row ->
                AppSyncChangeScope(
                    tenantId = row[AppSyncChangeTable.tenantId],
                    userId = row[AppSyncChangeTable.userId],
                    resource = row[AppSyncChangeTable.resource],
                )
            }.forEach { (scope, rows) ->
                advanceRetentionCursor(
                    scope = scope,
                    cursor = rows.maxOf { it[AppSyncChangeTable.cursor] },
                    updatedAt = updatedAt,
                )
            }

            val cursors = expired.map { it[AppSyncChangeTable.cursor] }
            AppSyncChangeTable.deleteWhere { AppSyncChangeTable.cursor inList cursors }
        }
    }

    private fun advanceRetentionCursor(
        scope: AppSyncChangeScope,
        cursor: Long,
        updatedAt: LocalDateTime,
    ) {
        AppSyncChangeRetentionTable.insertIgnore {
            it[tenantId] = scope.tenantId
            it[userId] = scope.userId
            it[resource] = scope.resource
            it[retentionCursor] = cursor
            it[AppSyncChangeRetentionTable.updatedAt] = updatedAt
        }
        AppSyncChangeRetentionTable.update(
            where = {
                listOf(
                    AppSyncChangeRetentionTable.tenantId eq scope.tenantId,
                    AppSyncChangeRetentionTable.userId eq scope.userId,
                    AppSyncChangeRetentionTable.resource eq scope.resource,
                    AppSyncChangeRetentionTable.retentionCursor less cursor,
                ).compoundAnd()
            },
        ) {
            it[retentionCursor] = cursor
            it[AppSyncChangeRetentionTable.updatedAt] = updatedAt
        }
    }

    private fun toRecord(row: ResultRow): AppSyncChangeRecord = AppSyncChangeRecord(
        cursor = row[AppSyncChangeTable.cursor],
        resource = row[AppSyncChangeTable.resource],
        aggregateId = row[AppSyncChangeTable.aggregateId],
        operation = row[AppSyncChangeTable.operation],
        aggregateVersion = row[AppSyncChangeTable.aggregateVersion],
        payload = row[AppSyncChangeTable.payload],
    )
}
