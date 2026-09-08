package im.hikaru.ruoyi.module.sync.service

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchRespVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesQuery
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesRespVO
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeDao
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.TreeMap
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Service

@Service
class SyncCommandServiceImpl(
    private val processor: SyncCommandProcessor,
) : SyncCommandService {
    override fun submit(
        userId: Long,
        request: AppSyncCommandBatchReqVO,
    ): AppSyncCommandBatchRespVO {
        val context = SyncCommandContext(TenantContextHolder.getRequiredTenantId(), userId)
        val results = request.commands.map { item ->
            val command = SyncCommandEnvelope(
                commandId = item.commandId,
                aggregateType = item.aggregateType,
                aggregateId = item.aggregateId,
                operation = item.operation,
                payload = item.payload ?: JsonUtils.parseTree("{}"),
                baseVersion = item.baseVersion,
            )
            processor.process(context, command, requestHash(command))
        }
        return AppSyncCommandBatchRespVO(results)
    }

    override fun getChanges(userId: Long, query: AppSyncChangesQuery): AppSyncChangesRespVO {
        val context = SyncCommandContext(TenantContextHolder.getRequiredTenantId(), userId)
        return transaction {
            val bounds = AppSyncChangeDao.selectBounds(context, query.resource)
            if (query.cursor < bounds.retentionCursor || query.cursor > bounds.highWatermark) {
                return@transaction AppSyncChangesRespVO(
                    items = emptyList(),
                    nextCursor = bounds.highWatermark,
                    hasMore = false,
                    resetRequired = true,
                    resetCursor = bounds.highWatermark,
                )
            }

            val records = AppSyncChangeDao.selectAfter(context, query.resource, query.cursor, query.limit + 1)
            val hasMore = records.size > query.limit
            val page = records.take(query.limit)
            AppSyncChangesRespVO(
                items = page.map { record ->
                    AppSyncChangeVO(
                        cursor = record.cursor,
                        resource = record.resource,
                        aggregateId = record.aggregateId,
                        operation = record.operation,
                        aggregateVersion = record.aggregateVersion,
                        payload = JsonUtils.parseTree(record.payload),
                    )
                },
                nextCursor = page.lastOrNull()?.cursor ?: query.cursor,
                hasMore = hasMore,
            )
        }
    }

    private fun requestHash(command: SyncCommandEnvelope): String {
        val canonical = sortedMapOf<String, Any?>(
            "aggregateId" to command.aggregateId,
            "aggregateType" to command.aggregateType,
            "baseVersion" to command.baseVersion,
            "operation" to command.operation,
            "payload" to canonicalize(JsonUtils.objectMapper.convertValue(command.payload, Any::class.java)),
        )
        val bytes = JsonUtils.toJsonString(canonical).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }

    private fun canonicalize(value: Any?): Any? = when (value) {
        is Map<*, *> -> TreeMap<String, Any?>().apply {
            value.forEach { (key, nested) -> put(key.toString(), canonicalize(nested)) }
        }
        is Iterable<*> -> value.map(::canonicalize)
        else -> value
    }
}
