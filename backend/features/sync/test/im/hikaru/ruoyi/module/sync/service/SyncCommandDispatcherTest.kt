package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.contracts.app.member.MemberProfileSyncContract
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncCommandDispatcherTest {

    private val whitelist = SyncCommandWhitelist()

    @Test
    fun `白名单操作可以正常分发`() {
        val handler = handler(
            keys = setOf(
                SyncCommandHandlerKey(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.CREATE)
            ),
            decision = SyncCommandDecision.Applied(aggregateId = "123", serverVersion = 1),
        )

        val dispatcher = SyncCommandDispatcher(listOf(handler), whitelist)
        val context = SyncCommandContext(tenantId = 1, userId = 1)
        val command = SyncCommandEnvelope(
            commandId = "cmd-1",
            aggregateType = MemberAddressSyncContract.RESOURCE,
            aggregateId = "-1",
            operation = MemberAddressSyncContract.CREATE,
            payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
            baseVersion = null,
        )

        val result = dispatcher.dispatch(context, command)

        assertTrue(result is SyncCommandDecision.Applied)
    }

    @Test
    fun `非白名单操作被拒绝`() {
        val handler = handler(
            keys = setOf(SyncCommandHandlerKey("member-sign-in", "create")),
        )

        val dispatcher = SyncCommandDispatcher(listOf(handler), whitelist)
        val context = SyncCommandContext(tenantId = 1, userId = 1)
        val command = SyncCommandEnvelope(
            commandId = "cmd-1",
            aggregateType = "member-sign-in",
            aggregateId = "1",
            operation = "create",
            payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
            baseVersion = null,
        )

        val result = dispatcher.dispatch(context, command)

        assertTrue(result is SyncCommandDecision.Rejected)
        assertEquals("SYNC_NOT_ALLOWED", (result as SyncCommandDecision.Rejected).errorCode)
    }

    @Test
    fun `白名单检查优先于 Handler 查找`() {
        // 即使 Handler 存在，不在白名单也会被拒绝
        val handler = handler(
            keys = setOf(SyncCommandHandlerKey("unknown-resource", "unknown-operation")),
        )

        val dispatcher = SyncCommandDispatcher(listOf(handler), whitelist)
        val context = SyncCommandContext(tenantId = 1, userId = 1)
        val command = SyncCommandEnvelope(
            commandId = "cmd-1",
            aggregateType = "unknown-resource",
            aggregateId = "1",
            operation = "unknown-operation",
            payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
            baseVersion = null,
        )

        val result = dispatcher.dispatch(context, command)

        // 白名单检查先于 Handler 查找
        assertTrue(result is SyncCommandDecision.Rejected)
        assertEquals("SYNC_NOT_ALLOWED", (result as SyncCommandDecision.Rejected).errorCode)
    }

    @Test
    fun `所有白名单操作都有对应的 Contract 定义`() {
        val allowed = whitelist.getAllowedOperations()

        // 验证每个白名单操作都来自 Contract
        val validContracts = setOf(
            MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.CREATE,
            MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.UPDATE,
            MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.DELETE,
            MemberProfileSyncContract.RESOURCE to MemberProfileSyncContract.UPDATE,
        )

        assertTrue(allowed.all { it in validContracts },
            "所有白名单操作必须在已知的 Contract 中定义")
    }

    private fun handler(
        keys: Set<SyncCommandHandlerKey>,
        decision: SyncCommandDecision = SyncCommandDecision.Rejected("UNEXPECTED_HANDLER_CALL"),
    ): SyncCommandHandler = object : SyncCommandHandler {
        override val keys = keys

        override fun handle(
            context: SyncCommandContext,
            command: SyncCommandEnvelope,
        ): SyncCommandDecision = decision
    }
}
