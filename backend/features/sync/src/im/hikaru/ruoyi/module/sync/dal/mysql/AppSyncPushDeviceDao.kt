package im.hikaru.ruoyi.module.sync.dal.mysql

import im.hikaru.contracts.sync.SyncPushPlatform
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object AppSyncPushDeviceDao {
    fun register(
        context: SyncCommandContext,
        token: String,
        platform: SyncPushPlatform,
        updatedAt: LocalDateTime,
    ) = transaction {
        AppSyncPushDeviceTable.insertIgnore {
            it[AppSyncPushDeviceTable.token] = token
            it[tenantId] = context.tenantId
            it[userId] = context.userId
            it[AppSyncPushDeviceTable.platform] = platform.name
            it[AppSyncPushDeviceTable.updatedAt] = updatedAt
        }
        AppSyncPushDeviceTable.update(where = { AppSyncPushDeviceTable.token eq token }) {
            it[tenantId] = context.tenantId
            it[userId] = context.userId
            it[AppSyncPushDeviceTable.platform] = platform.name
            it[AppSyncPushDeviceTable.updatedAt] = updatedAt
        }
    }

    fun unregister(context: SyncCommandContext, token: String): Int = transaction {
        AppSyncPushDeviceTable.deleteWhere {
            listOf(
                AppSyncPushDeviceTable.token eq token,
                AppSyncPushDeviceTable.tenantId eq context.tenantId,
                AppSyncPushDeviceTable.userId eq context.userId,
            ).compoundAnd()
        }
    }

    fun selectTokens(context: SyncCommandContext): List<String> = transaction {
        AppSyncPushDeviceTable.selectAll()
            .where {
                listOf(
                    AppSyncPushDeviceTable.tenantId eq context.tenantId,
                    AppSyncPushDeviceTable.userId eq context.userId,
                ).compoundAnd()
            }
            .map { it[AppSyncPushDeviceTable.token] }
    }

    fun deleteTokens(context: SyncCommandContext, tokens: Collection<String>): Int = transaction {
        if (tokens.isEmpty()) return@transaction 0
        AppSyncPushDeviceTable.deleteWhere {
            listOf(
                AppSyncPushDeviceTable.tenantId eq context.tenantId,
                AppSyncPushDeviceTable.userId eq context.userId,
                AppSyncPushDeviceTable.token inList tokens,
            ).compoundAnd()
        }
    }
}
