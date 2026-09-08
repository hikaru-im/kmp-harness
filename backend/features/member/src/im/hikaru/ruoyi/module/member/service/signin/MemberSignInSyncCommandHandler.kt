package im.hikaru.ruoyi.module.member.service.signin

import im.hikaru.contracts.app.member.MemberSignInSyncContract
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import im.hikaru.ruoyi.module.sync.service.SyncCommandDecision
import im.hikaru.ruoyi.module.sync.service.SyncCommandEnvelope
import im.hikaru.ruoyi.module.sync.service.SyncCommandHandler
import im.hikaru.ruoyi.module.sync.service.SyncCommandHandlerKey
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component

@Component
class MemberSignInSyncCommandHandler(
    private val signInRecordService: MemberSignInRecordService,
) : SyncCommandHandler {
    override val keys: Set<SyncCommandHandlerKey> = setOf(
        SyncCommandHandlerKey(MemberSignInSyncContract.RESOURCE, MemberSignInSyncContract.CREATE),
    )

    override fun handle(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
    ): SyncCommandDecision {
        val aggregateId = command.aggregateId?.toLongOrNull()
            ?.takeIf { it == context.userId }
            ?: return SyncCommandDecision.Rejected("INVALID_AGGREGATE_ID")
        if (command.baseVersion != null) return SyncCommandDecision.Rejected("BASE_VERSION_NOT_ALLOWED")
        val requestedDate = command.payload.get("requestedDate")
            ?.let { node -> runCatching { LocalDate.parse(node.stringValue()) }.getOrNull() }
            ?: return SyncCommandDecision.Rejected("INVALID_PAYLOAD")

        return when (val result = signInRecordService.createSignRecord(context.userId, requestedDate)) {
            is SignInMutationResult.Applied -> SyncCommandDecision.Applied(
                aggregateId = aggregateId.toString(),
                serverVersion = requireNotNull(result.record.id),
            )
            SignInMutationResult.AlreadySigned ->
                SyncCommandDecision.Rejected(MemberSignInSyncContract.ERROR_ALREADY_COMPLETED)
            is SignInMutationResult.DateNotCurrent ->
                SyncCommandDecision.Rejected(MemberSignInSyncContract.ERROR_DATE_NOT_CURRENT)
            SignInMutationResult.UserNotFound -> SyncCommandDecision.Rejected("AGGREGATE_NOT_FOUND")
        }
    }
}
