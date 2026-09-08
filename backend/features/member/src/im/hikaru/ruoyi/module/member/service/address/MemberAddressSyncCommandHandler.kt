package im.hikaru.ruoyi.module.member.service.address

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import im.hikaru.ruoyi.module.sync.service.SyncCommandDecision
import im.hikaru.ruoyi.module.sync.service.SyncCommandEnvelope
import im.hikaru.ruoyi.module.sync.service.SyncCommandHandler
import im.hikaru.ruoyi.module.sync.service.SyncCommandHandlerKey
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class MemberAddressSyncCommandHandler(
    private val addressCommandService: AddressCommandService,
    private val validator: Validator,
) : SyncCommandHandler {
    override val keys: Set<SyncCommandHandlerKey> = setOf(
        SyncCommandHandlerKey(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.CREATE),
        SyncCommandHandlerKey(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.DELETE),
        SyncCommandHandlerKey(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.UPDATE),
    )

    override fun handle(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
    ): SyncCommandDecision = when (command.operation) {
        MemberAddressSyncContract.CREATE -> handleCreate(context, command)
        MemberAddressSyncContract.DELETE -> handleDelete(context, command)
        MemberAddressSyncContract.UPDATE -> handleUpdate(context, command)
        else -> SyncCommandDecision.Rejected("UNSUPPORTED_OPERATION")
    }

    private fun handleCreate(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
    ): SyncCommandDecision {
        command.aggregateId?.toLongOrNull()
            ?.takeIf { it < 0 }
            ?: return SyncCommandDecision.Rejected("INVALID_AGGREGATE_ID")
        if (command.baseVersion != null) {
            return SyncCommandDecision.Rejected("BASE_VERSION_NOT_ALLOWED")
        }
        val request = runCatching {
            JsonUtils.objectMapper.convertValue(command.payload, AppAddressCreateReqVO::class.java)
        }.getOrNull() ?: return SyncCommandDecision.Rejected("INVALID_PAYLOAD")
        if (validator.validate(request).isNotEmpty()) {
            return SyncCommandDecision.Rejected("INVALID_PAYLOAD")
        }

        val address = addressCommandService.create(context.userId, request)
        return SyncCommandDecision.Applied(
            aggregateId = requireNotNull(address.id).toString(),
            serverVersion = address.version,
        )
    }

    private fun handleDelete(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
    ): SyncCommandDecision {
        val aggregateId = command.aggregateId?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: return SyncCommandDecision.Rejected("INVALID_AGGREGATE_ID")
        val baseVersion = command.baseVersion
            ?: return SyncCommandDecision.Rejected("BASE_VERSION_REQUIRED")

        return when (val result = addressCommandService.delete(context.userId, aggregateId, baseVersion)) {
            is AddressMutationResult.Applied -> SyncCommandDecision.Applied(
                aggregateId = aggregateId.toString(),
                serverVersion = result.address.version,
            )
            AddressMutationResult.NotFound -> SyncCommandDecision.Applied(
                aggregateId = aggregateId.toString(),
                serverVersion = baseVersion + 1,
            )
            is AddressMutationResult.Conflict -> SyncCommandDecision.Conflict(
                serverVersion = result.serverVersion,
                serverPayload = result.serverAddress?.let(::toServerPayload),
            )
        }
    }

    private fun handleUpdate(
        context: SyncCommandContext,
        command: SyncCommandEnvelope,
    ): SyncCommandDecision {
        val aggregateId = command.aggregateId?.toLongOrNull()
            ?: return SyncCommandDecision.Rejected("INVALID_AGGREGATE_ID")
        val baseVersion = command.baseVersion
            ?: return SyncCommandDecision.Rejected("BASE_VERSION_REQUIRED")
        val request = runCatching {
            JsonUtils.objectMapper.convertValue(command.payload, AppAddressUpdateReqVO::class.java)
        }.getOrNull() ?: return SyncCommandDecision.Rejected("INVALID_PAYLOAD")
        if (request.id != aggregateId || validator.validate(request).isNotEmpty()) {
            return SyncCommandDecision.Rejected("INVALID_PAYLOAD")
        }

        return when (val result = addressCommandService.update(context.userId, request, baseVersion)) {
            is AddressMutationResult.Applied -> SyncCommandDecision.Applied(
                aggregateId = aggregateId.toString(),
                serverVersion = result.address.version,
            )
            AddressMutationResult.NotFound -> SyncCommandDecision.Rejected("AGGREGATE_NOT_FOUND")
            is AddressMutationResult.Conflict -> SyncCommandDecision.Conflict(
                serverVersion = result.serverVersion,
                serverPayload = result.serverAddress?.let(::toServerPayload),
            )
        }
    }

    private fun toServerPayload(address: im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO): JsonNode =
        JsonUtils.objectMapper.valueToTree(
            mapOf(
                "id" to address.id,
                "name" to address.name,
                "mobile" to address.mobile,
                "areaId" to address.areaId,
                "detailAddress" to address.detailAddress,
                "defaultStatus" to (address.defaultStatus ?: false),
                "version" to address.version,
            ),
        )
}
