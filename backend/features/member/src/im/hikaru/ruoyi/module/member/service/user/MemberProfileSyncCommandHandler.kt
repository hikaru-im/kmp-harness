package im.hikaru.ruoyi.module.member.service.user

import im.hikaru.contracts.app.member.MemberProfileSyncContract
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import im.hikaru.ruoyi.module.sync.service.SyncCommandDecision
import im.hikaru.ruoyi.module.sync.service.SyncCommandEnvelope
import im.hikaru.ruoyi.module.sync.service.SyncCommandHandler
import im.hikaru.ruoyi.module.sync.service.SyncCommandHandlerKey
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class MemberProfileSyncCommandHandler(
    private val profileCommandService: ProfileCommandService,
    private val validator: Validator,
) : SyncCommandHandler {
    override val keys: Set<SyncCommandHandlerKey> = setOf(
        SyncCommandHandlerKey(MemberProfileSyncContract.RESOURCE, MemberProfileSyncContract.UPDATE),
    )

    override fun handle(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
    ): SyncCommandDecision {
        val aggregateId = command.aggregateId?.toLongOrNull()
            ?.takeIf { it == context.userId }
            ?: return SyncCommandDecision.Rejected("INVALID_AGGREGATE_ID")
        val baseVersion = command.baseVersion
            ?: return SyncCommandDecision.Rejected("BASE_VERSION_REQUIRED")
        val request = runCatching {
            JsonUtils.objectMapper.convertValue(command.payload, AppMemberUserUpdateReqVO::class.java)
        }.getOrNull() ?: return SyncCommandDecision.Rejected("INVALID_PAYLOAD")
        if (validator.validate(request).isNotEmpty()) {
            return SyncCommandDecision.Rejected("INVALID_PAYLOAD")
        }

        return when (val result = profileCommandService.update(context.userId, request, baseVersion)) {
            is ProfileMutationResult.Applied -> SyncCommandDecision.Applied(
                aggregateId = aggregateId.toString(),
                serverVersion = result.profile.profileVersion,
            )
            ProfileMutationResult.NotFound -> SyncCommandDecision.Rejected("AGGREGATE_NOT_FOUND")
            is ProfileMutationResult.Rejected -> SyncCommandDecision.Rejected(result.errorCode)
            is ProfileMutationResult.Conflict -> SyncCommandDecision.Conflict(
                serverVersion = result.serverVersion,
                serverPayload = toServerPayload(result.serverProfile),
            )
        }
    }

    private fun toServerPayload(profile: MemberUserDO): JsonNode = JsonUtils.objectMapper.valueToTree(
        mapOf(
            "id" to profile.id,
            "nickname" to profile.nickname,
            "avatar" to profile.avatar,
            "email" to profile.email,
            "sex" to profile.sex,
            "profileVersion" to profile.profileVersion,
        ),
    )
}
