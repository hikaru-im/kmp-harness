package im.hikaru.ruoyi.module.sync.service

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandOutcomeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandResultVO
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncCommandDao
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncCommandRecord
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Clock

@Service
class SyncCommandProcessor(
    private val dispatcher: SyncCommandDispatcher,
) {
    @Transactional(rollbackFor = [Exception::class])
    fun process(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
        requestHash: String,
    ): AppSyncCommandResultVO {
        AppSyncCommandDao.select(context, command.commandId)?.let { existing ->
            return replay(command, requestHash, existing)
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        if (!AppSyncCommandDao.insertIfAbsent(context, command, requestHash, now)) {
            val existing = checkNotNull(AppSyncCommandDao.select(context, command.commandId)) {
                "The idempotency row disappeared after a duplicate insert"
            }
            return replay(command, requestHash, existing)
        }

        val result = dispatcher.dispatch(context, command).toResult(command)
        check(AppSyncCommandDao.complete(
            context = context,
            result = result,
            resultJson = JsonUtils.toJsonString(result),
            completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        ) == 1) { "Failed to persist sync command result" }
        return result
    }

    private fun replay(
        command: SyncCommandEnvelope,
        requestHash: String,
        existing: AppSyncCommandRecord,
    ): AppSyncCommandResultVO {
        if (existing.requestHash != requestHash) {
            return AppSyncCommandResultVO(
                commandId = command.commandId,
                outcome = AppSyncCommandOutcomeVO.REJECTED,
                replayed = true,
                aggregateId = command.aggregateId,
                serverVersion = existing.serverVersion,
                errorCode = "COMMAND_ID_REUSED",
            )
        }
        val outcome = checkNotNull(existing.outcome) {
            "A committed sync command is missing its deterministic result"
        }
        return AppSyncCommandResultVO(
            commandId = command.commandId,
            outcome = outcome,
            replayed = true,
            aggregateId = existing.aggregateId,
            serverVersion = existing.serverVersion,
            serverPayload = existing.resultJson
                ?.let { resultJson ->
                    runCatching { JsonUtils.parseTree(resultJson).get("serverPayload") }
                        .getOrNull()
                }
                ?.takeUnless { it.isNull },
            errorCode = existing.errorCode,
        )
    }

    private fun SyncCommandDecision.toResult(command: SyncCommandEnvelope): AppSyncCommandResultVO = when (this) {
        is SyncCommandDecision.Applied -> AppSyncCommandResultVO(
            commandId = command.commandId,
            outcome = AppSyncCommandOutcomeVO.APPLIED,
            replayed = false,
            aggregateId = aggregateId ?: command.aggregateId,
            serverVersion = serverVersion,
        )

        is SyncCommandDecision.Rejected -> AppSyncCommandResultVO(
            commandId = command.commandId,
            outcome = AppSyncCommandOutcomeVO.REJECTED,
            replayed = false,
            aggregateId = command.aggregateId,
            serverVersion = serverVersion,
            errorCode = errorCode,
        )

        is SyncCommandDecision.Conflict -> AppSyncCommandResultVO(
            commandId = command.commandId,
            outcome = AppSyncCommandOutcomeVO.CONFLICT,
            replayed = false,
            aggregateId = command.aggregateId,
            serverVersion = serverVersion,
            serverPayload = serverPayload,
            errorCode = errorCode,
        )
    }
}
