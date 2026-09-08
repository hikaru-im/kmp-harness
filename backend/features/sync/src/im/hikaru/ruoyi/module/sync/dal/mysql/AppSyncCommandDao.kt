package im.hikaru.ruoyi.module.sync.dal.mysql

import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandOutcomeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandResultVO
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import im.hikaru.ruoyi.module.sync.service.SyncCommandEnvelope
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class AppSyncCommandRecord(
    val requestHash: String,
    val outcome: AppSyncCommandOutcomeVO?,
    val aggregateId: String?,
    val serverVersion: Long?,
    val errorCode: String?,
    val resultJson: String?,
)

object AppSyncCommandDao {
    fun select(context: SyncCommandContext, commandId: String): AppSyncCommandRecord? = transaction {
        AppSyncCommandTable.selectAll()
            .where {
                listOf(
                    AppSyncCommandTable.tenantId eq context.tenantId,
                    AppSyncCommandTable.userId eq context.userId,
                    AppSyncCommandTable.commandId eq commandId,
                ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toRecord)
    }

    fun insertIfAbsent(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
        requestHash: String,
        createdAt: LocalDateTime,
    ): Boolean = transaction {
        AppSyncCommandTable.insertIgnore {
            it[tenantId] = context.tenantId
            it[userId] = context.userId
            it[commandId] = command.commandId
            it[aggregateType] = command.aggregateType
            it[aggregateId] = command.aggregateId
            it[operation] = command.operation
            it[baseVersion] = command.baseVersion
            it[AppSyncCommandTable.requestHash] = requestHash
            it[AppSyncCommandTable.createdAt] = createdAt
        }.insertedCount > 0
    }

    fun complete(
        context: SyncCommandContext,
        result: AppSyncCommandResultVO,
        resultJson: String,
        completedAt: LocalDateTime,
    ): Int = transaction {
        AppSyncCommandTable.update(where = {
            listOf(
                AppSyncCommandTable.tenantId eq context.tenantId,
                AppSyncCommandTable.userId eq context.userId,
                AppSyncCommandTable.commandId eq result.commandId,
            ).compoundAnd()
        }) {
            it[outcome] = result.outcome.name
            it[aggregateId] = result.aggregateId
            it[serverVersion] = result.serverVersion
            it[errorCode] = result.errorCode
            it[AppSyncCommandTable.resultJson] = resultJson
            it[AppSyncCommandTable.completedAt] = completedAt
        }
    }

    private fun toRecord(row: ResultRow): AppSyncCommandRecord = AppSyncCommandRecord(
        requestHash = row[AppSyncCommandTable.requestHash],
        outcome = row[AppSyncCommandTable.outcome]?.let(AppSyncCommandOutcomeVO::valueOf),
        aggregateId = row[AppSyncCommandTable.aggregateId],
        serverVersion = row[AppSyncCommandTable.serverVersion],
        errorCode = row[AppSyncCommandTable.errorCode],
        resultJson = row[AppSyncCommandTable.resultJson],
    )
}
