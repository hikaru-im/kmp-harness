package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.*
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.ReasoningModeId
import im.hikaru.harness.llm.ReasoningOptions
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.*
import im.hikaru.harness.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.*

class SubagentToolsContractTest {
    @Test
    fun unifiedReasoningIsInheritedAndExplicitProviderDefaultStopsInheritance() = runTest {
        val fixture = fixture()
        try {
            val reasoning = ReasoningOptions(
                mode = ReasoningModeId("enabled"), effort = ReasoningEffortId("high"), budgetTokens = 4096,
            )
            val parent = fixture.create("reasoning-parent", reasoning = reasoning)
            val inherited = decode(fixture.execute(parent, "inherit", """{"description":"inherit reasoning","prompt":"task"}"""))
            val inheritedChild = assertNotNull(fixture.registry.get(AgentId(assertNotNull(inherited.childSessionId))))
            assertEquals(reasoning, inheritedChild.options.reasoning)
            assertNull(inheritedChild.options.reasoningEffort)

            val reset = decode(fixture.execute(parent, "reset", """{"description":"provider defaults","prompt":"task","reasoning":{}}"""))
            val resetChild = assertNotNull(fixture.registry.get(AgentId(assertNotNull(reset.childSessionId))))
            assertEquals(ReasoningOptions(), resetChild.options.reasoning)
            assertNull(resetChild.options.reasoningEffort)

            val conflict = decode(fixture.execute(parent, "conflict", """{"description":"invalid override","prompt":"task","reasoning":{},"reasoning_effort":"low"}"""))
            assertEquals(SubagentOutcome.REJECTED, conflict.outcome)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun realDispatchCreatesVerifiedLineageExplicitModelAndRealInboxReceipt() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent", cwd = "/actual/parent")
            val result = decode(fixture.execute(parent))
            assertEquals(SubagentOutcome.ACCEPTED, result.outcome, result.error)
            assertEquals(SubagentDelivery.ACCEPTED, result.delivery)
            assertFalse(result.completionVerified)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertEquals(SessionOrigin.SUBAGENT, child.session.header.origin)
            assertEquals(parent.agent.session.id, child.session.header.parentSession)
            assertEquals("/actual/parent", child.session.header.cwd)
            assertEquals("/actual/parent", child.options.cwd)
            assertEquals("selected-provider", child.options.provider)
            assertEquals("selected-model", child.options.model)
            val inbox = child.inbox.snapshot().nextTurn.single()
            assertEquals(result.receiptId, inbox.id)
            assertEquals(result.inputMessageId, inbox.message.id.value)
            assertNotEquals(result.receiptId, result.inputMessageId)
            assertEquals(2, inbox.message.content.size)
            assertEquals("actual task", (inbox.message.content[0] as TextBlock).text)
            assertEquals(
                "Your parent agent id is \"parent\". Before you finish, send your result to that agent with " +
                    "send_message({ agent_id: \"parent\", message: \"<self-contained result>\" }). The parent " +
                    "shares your workspace but does not automatically receive your transcript, tool output, or " +
                    "reasoning. Send earlier messages as well when a finding changes what the parent should do " +
                    "next; sending a message does not end your turn.",
                (inbox.message.content[1] as TextBlock).text,
            )
            assertEquals(AgentSendTarget.NEXT_TURN, inbox.target)
            assertEquals(AgentWakeup.IF_IDLE, inbox.wakeup)
            assertEquals(0, (child as ControlledAgent).awaitIdleCalls)

            val descendants = assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(parent.agent.session.id))
            assertEquals(listOf(child.session.id), descendants.children.map { it.child })
            assertEquals(DescendantStatus.UNKNOWN, descendants.children.single().status)
            assertFalse(descendants.complete)
            // The consumer commits the actual queued input into the actual Session log.
            child.session.append(SessionEventKeys.UserMessage, UserMessageEvent(turn = 1, message = inbox.message))
            val history = assertIs<ChildHistoryResult.Available>(
                fixture.api.readChildHistory(parent.agent.session.id, child.session.id),
            )
            // Session projection stamps the envelope time onto derived messages.
            assertEquals(listOf(inbox.message), history.messages.map { it.copy(timestampMillis = null) })
            assertEquals(TOOL_SUBAGENT_PLUGIN_NAME, ToolSubagentPluginDefinition.name)
            assertEquals(
                listOf(SUBAGENT_TOOL_NAME, SEND_MESSAGE_TOOL_NAME, INTERRUPT_AGENT_TOOL_NAME, LIST_AGENTS_TOOL_NAME),
                fixture.tools.schemas().map { it.name },
            )
            fixture.scope.dispose()
            assertNull(fixture.registry.get(child.id))
            assertNull(fixture.runtime.context.sessions.get(child.session.id))
            assertSame(parent.agent, fixture.registry.get(parent.agent.id))
            assertTrue(fixture.tools.schemas().isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun subagentInheritsParentRouteAndSupportsExplicitCwdAndRouteOverride() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create(
                "parent",
                cwd = "/parent/dir",
                provider = "parent-provider",
                model = "parent-model",
                reasoningEffort = ReasoningEffortId("high"),
                maxTokens = 4_096,
            )
            val inherited = decode(
                fixture.execute(
                    parent,
                    call = "inherited",
                    arguments = """{"description":"inherit route","prompt":"inherited task"}""",
                ),
            )
            assertEquals(SubagentOutcome.ACCEPTED, inherited.outcome)
            val inheritedChild = assertNotNull(fixture.registry.get(AgentId(assertNotNull(inherited.childSessionId))))
            assertEquals("/parent/dir", inheritedChild.options.cwd)
            assertEquals("parent-provider", inheritedChild.options.provider)
            assertEquals("parent-model", inheritedChild.options.model)
            assertEquals(ReasoningEffortId("high"), inheritedChild.options.reasoningEffort)
            assertEquals(4_096L, inheritedChild.options.maxTokens)

            val overridden = decode(
                fixture.execute(
                    parent,
                    call = "overridden",
                    arguments = """{"description":"override route","prompt":"override task","cwd":"/child/dir","provider":"child-provider","model":"child-model","reasoning_effort":"low"}""",
                ),
            )
            assertEquals(SubagentOutcome.ACCEPTED, overridden.outcome)
            val overriddenChild = assertNotNull(fixture.registry.get(AgentId(assertNotNull(overridden.childSessionId))))
            assertEquals("/child/dir", overriddenChild.options.cwd)
            assertEquals("/child/dir", overriddenChild.session.header.cwd)
            assertEquals("child-provider", overriddenChild.options.provider)
            assertEquals("child-model", overriddenChild.options.model)
            assertEquals(ReasoningEffortId("low"), overriddenChild.options.reasoningEffort)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun nestedSpawnIsReadableButOrdinaryBranchIsNotAnAgentDescendant() = runTest {
        // DSH's default maxDepth is 1; this test intentionally allows one more level.
        val fixture = fixture(maxDepth = 2)
        try {
            val root = fixture.create("root")
            val branch = fixture.create("branch", parent = "root", origin = null)
            val first = decode(fixture.execute(root, "first"))
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(first.childSessionId))))
            val second = decode(fixture.tools.execute(request("nested", child.session.id.value), execution(child)))
            val nested = assertNotNull(second.childSessionId)
            val nestedAgent = assertNotNull(fixture.registry.get(AgentId(nested)))
            assertEquals(2L, nestedAgent.session.header.delegationDepth)
            assertEquals(1L, child.session.header.delegationDepth)
            val listed = assertIs<SessionDescendantsResult.Available>(fixture.api.descendants(root.agent.session.id))
            assertEquals(setOf(first.childSessionId, nested), listed.children.map { it.child.value }.toSet())
            assertFalse(listed.children.any { it.child == branch.agent.session.id })
            val branchChild = decode(fixture.execute(branch, "branch-child"))
            assertEquals(SubagentOutcome.ACCEPTED, branchChild.outcome, "An ordinary branch can delegate")
            assertEquals(2, assertIs<SessionDescendantsResult.Available>(
                fixture.api.descendants(root.agent.session.id),
            ).children.size, "The ordinary branch edge must not be reclassified")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun untrustedSingleArgumentScopeForgedParentAndInvalidRouteAreRejectedBeforeCreate() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            assertFailsWith<IllegalStateException> { fixture.tools.execute(request("no-authority", "parent")) }
            val arguments = listOf(
                """{"prompt":"task","provider":"p","model":"m","parent":"parent"}""",
                """{"description":"d","prompt":"task","provider":"p"}""",
                """{"description":"d","prompt":"task","model":""}""",
                """{"description":"d","prompt":"task","provider":12,"model":"m"}""",
                """{"description":"","prompt":"task"}""",
                """{"description":"d"}""",
            )
            for ((index, value) in arguments.withIndex()) {
                val response = fixture.tools.execute(request("invalid-$index", "parent", arguments = value), execution(parent.agent))
                assertTrue(response.isError)
                assertEquals(SubagentOutcome.REJECTED, decode(response).outcome)
            }
            assertEquals(SubagentOutcome.REJECTED, decode(fixture.tools.execute(
                request("wrong-scope", "someone-else"), execution(parent.agent),
            )).outcome)
            assertEquals(listOf(parent.agent), fixture.registry.list())
            assertEquals(1, fixture.created.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun foreignRegistryForeignStoreAndWrongContextCannotAuthorizeParent() = runTest {
        val fixture = fixture()
        val foreign = fixture()
        try {
            val parent = fixture.create("same-id")
            val other = foreign.create("same-id")
            assertEquals(SubagentOutcome.REJECTED, decode(fixture.tools.execute(
                request("foreign", "same-id"), execution(other.agent),
            )).outcome)
            assertEquals(SubagentOutcome.REJECTED, decode(fixture.tools.execute(
                request("wrong-session", "same-id"), ToolExecutionContext(parent.agent.context, other.agent.session),
            )).outcome)
            assertEquals(SubagentOutcome.REJECTED, decode(fixture.tools.execute(
                request("wrong-context", "same-id"), ToolExecutionContext(fixture.runtime.context, parent.agent.session),
            )).outcome)
            val overlay = parent.agent.context.isolate(SessionKey)
            overlay.provide(SessionKey, foreign.runtime.context.sessions)
            assertEquals(SubagentOutcome.REJECTED, decode(fixture.tools.execute(
                request("foreign-store", "same-id"), ToolExecutionContext(overlay, parent.agent.session),
            )).outcome)
            assertEquals(1, fixture.created.size)
            overlay.dispose()
        } finally {
            foreign.close()
            fixture.close()
        }
    }

    @Test
    fun invalidSelfCycleMissingAncestorAndSubagentWithoutParentCannotSpawn() = runTest {
        val fixture = fixture()
        try {
            val self = fixture.create("self", "self")
            val a = fixture.create("a", "b")
            fixture.create("b", "a")
            val missing = fixture.create("missing", "absent")
            val orphan = fixture.create("orphan", origin = SessionOrigin.SUBAGENT)
            for (handle in listOf(self, a, missing, orphan)) {
                assertEquals(SubagentOutcome.REJECTED, decode(fixture.execute(handle, handle.agent.id.value)).outcome)
            }
            assertEquals(5, fixture.created.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun delayedCreateRejectsParentOrAncestorReplacementAndRollsBackOnlyOwnedChild() = runTest {
        for (replacedId in listOf("parent", "ancestor")) {
            val fixture = fixture()
            try {
                val ancestor = fixture.create("ancestor")
                val parent = fixture.create("parent", "ancestor")
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                fixture.beforeCreate = { created ->
                    if (created.session.header.origin == SessionOrigin.SUBAGENT && created.id.value !in setOf("parent", "ancestor")) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                val pending = async { fixture.execute(parent) }
                entered.await()
                (if (replacedId == "parent") parent else ancestor).dispose()
                val replacement = fixture.create(replacedId, if (replacedId == "parent") "ancestor" else null,
                    origin = if (replacedId == "parent") SessionOrigin.SUBAGENT else null)
                release.complete(Unit)
                val response = decode(pending.await())
                assertEquals(SubagentOutcome.ROLLED_BACK, response.outcome)
                assertEquals(SubagentDelivery.NOT_ATTEMPTED, response.delivery)
                val childId = assertNotNull(response.childSessionId)
                assertNull(fixture.registry.get(AgentId(childId)))
                assertNull(fixture.runtime.context.sessions.get(SessionId(childId)))
                assertSame(replacement.agent, fixture.registry.get(AgentId(replacedId)))
                assertEquals(2, fixture.registry.list().size)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun pluginDisposedDuringCreateRejectsLateChildAndDoesNotPrompt() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.beforeCreate = { created ->
                if (created.session.header.origin == SessionOrigin.SUBAGENT) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val pending = async { fixture.execute(parent) }
            entered.await()
            fixture.scope.dispose()
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SubagentOutcome.ROLLED_BACK, result.outcome)
            assertNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertEquals(0, fixture.created.last().sendCalls)
            assertSame(parent.agent, fixture.registry.list().single())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun promptFailurePreservesChildAndReturnsItsIdWithoutAnAutomaticSecondCreate() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            fixture.beforeSend = { _, _ -> error("controlled prompt failure") }
            val response = fixture.execute(parent, "prompt-failed")
            assertTrue(response.isError)
            val result = decode(response)
            assertEquals(SubagentOutcome.CREATED_PROMPT_FAILED, result.outcome)
            assertEquals(SubagentDelivery.UNKNOWN, result.delivery)
            assertContains(assertNotNull(result.error), "controlled prompt failure")
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertTrue(child.inbox.snapshot().nextTurn.isEmpty())
            assertNull(result.receiptId)
            assertNotNull(result.inputMessageId)
            assertFailsWith<IllegalStateException> { fixture.execute(parent, "prompt-failed") }
            assertEquals(2, fixture.created.size)
            assertEquals(1, (child as ControlledAgent).sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failureAfterInboxCommitReportsUnknownDeliveryAndKeepsActualInput() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            fixture.afterSend = { _, _ -> error("failed after acceptance") }
            val result = decode(fixture.execute(parent))
            assertEquals(SubagentOutcome.CREATED_PROMPT_FAILED, result.outcome)
            assertEquals(SubagentDelivery.UNKNOWN, result.delivery)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertEquals(result.inputMessageId, child.inbox.snapshot().nextTurn.single().message.id.value)
            assertNull(result.receiptId)
            assertFalse(result.completionVerified)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancellationDuringDeliveryKeepsIdAndRepeatedCallCannotCreateAnotherChild() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Unit>()
            fixture.afterSend = { _, _ -> entered.complete(Unit); never.await() }
            val pending = async { fixture.execute(parent, "cancelled-delivery") }
            entered.await()
            pending.cancel()
            assertFailsWith<CancellationException> { pending.await() }
            val retry = decode(fixture.execute(parent, "cancelled-delivery"))
            assertEquals(SubagentOutcome.CREATED_DELIVERY_UNKNOWN, retry.outcome)
            assertEquals(SubagentDelivery.UNKNOWN, retry.delivery)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(retry.childSessionId))))
            assertEquals(retry.inputMessageId, child.inbox.snapshot().nextTurn.single().message.id.value)
            assertEquals(2, fixture.created.size)
            assertEquals(1, (child as ControlledAgent).sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun parentChangesDuringFollowupReturnsAcceptedReceiptAndExplicitRollback() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.afterSend = { _, _ -> entered.complete(Unit); release.await() }
            val pending = async { fixture.execute(parent) }
            entered.await()
            parent.dispose()
            val replacement = fixture.create("parent")
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SubagentOutcome.ROLLED_BACK, result.outcome)
            assertEquals(SubagentDelivery.ACCEPTED, result.delivery, "Do not relabel accepted input as unsent")
            assertNotNull(result.receiptId)
            assertNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId))))
            assertSame(replacement.agent, fixture.registry.list().single())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rollbackOfOldOwnedHandleCannotDeleteSameIdChildReplacement() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.afterSend = { _, _ -> entered.complete(Unit); release.await() }
            val pending = async { fixture.execute(parent) }
            entered.await()
            val original = fixture.created.last()
            fixture.scope.dispose()
            assertNull(fixture.registry.get(original.id))
            val replacement = fixture.create(original.id.value)
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SubagentOutcome.ROLLED_BACK, result.outcome)
            assertEquals(SubagentDelivery.ACCEPTED, result.delivery)
            assertSame(replacement.agent, fixture.registry.get(original.id))
            assertSame(replacement.agent.session, fixture.runtime.context.sessions.get(original.session.id))
            assertSame(parent.agent, fixture.registry.get(parent.agent.id))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun scopeIsOptionalButConcurrentRepeatedCallCannotCreateAnotherChild() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.beforeCreate = { created ->
                if (created.session.header.origin == SessionOrigin.SUBAGENT) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val call = request("concurrent", null)
            val pending = async { fixture.tools.execute(call, execution(parent.agent)) }
            entered.await()
            assertFailsWith<IllegalStateException> { fixture.tools.execute(call, execution(parent.agent)) }
            release.complete(Unit)
            assertEquals(SubagentOutcome.ACCEPTED, decode(pending.await()).outcome)
            assertEquals(2, fixture.created.size)
            assertEquals(1, fixture.created.last().sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun factoryFailureIsCreateFailureAndRollbackFailureIsExplicit() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            fixture.beforeCreate = { error("factory unavailable") }
            val failed = decode(fixture.execute(parent, "factory-failed"))
            assertEquals(SubagentOutcome.CREATE_FAILED, failed.outcome)
            assertNull(failed.childSessionId)
            assertEquals(1, fixture.runtime.context.sessions.list().size)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            fixture.beforeCreate = { created ->
                if (created.id.value != "parent") { entered.complete(Unit); release.await() }
            }
            fixture.cancelFailure = true
            val pending = async { fixture.execute(parent, "rollback-failed") }
            entered.await()
            parent.dispose() // This parent was constructed before cancelFailure was enabled.
            val replacement = fixture.create("parent")
            release.complete(Unit)
            val result = decode(pending.await())
            assertEquals(SubagentOutcome.ROLLBACK_FAILED, result.outcome)
            assertContains(assertNotNull(result.rollbackError), "controlled cancel failure")
            assertSame(replacement.agent, fixture.registry.get(AgentId("parent")))
            fixture.cancelFailure = false
            (replacement.agent as ControlledAgent).failCancel = false
        } finally {
            fixture.close()
        }
    }

    @Test
    fun hostDisposeReleasesAcceptedChildrenWithoutWaitingForIdle() = runTest {
        val fixture = fixture()
        val parent = fixture.create("parent")
        val result = decode(fixture.execute(parent))
        val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(result.childSessionId)))) as ControlledAgent
        fixture.close()
        assertTrue(child.context.isDisposed)
        assertFalse(child.session.isLive())
        assertEquals(0, child.awaitIdleCalls)
    }

    @Test
    fun sendMessageReachesDirectChildAndDirectParentWithStatusBasedDelivery() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val child = fixture.create("child", parent = "parent")
            val controlledParent = parent.agent as ControlledAgent
            val controlledChild = child.agent as ControlledAgent

            val toChild = fixture.control(
                "send-child", SEND_MESSAGE_TOOL_NAME,
                """{"agent_id":"child","message":"first instruction"}""",
                execution(parent.agent),
            )
            assertFalse(toChild.isError)
            val childReceipt = Json.decodeFromString(SendMessageResult.serializer(), text(toChild))
            assertEquals(controlledChild.receipts.last(), childReceipt.messageId)
            assertEquals(AgentSendTarget.NEXT_TURN, controlledChild.targets.last())
            assertEquals(AgentWakeup.IF_IDLE, controlledChild.wakeups.last())
            assertEquals("first instruction", (controlledChild.messages.last().content.single() as TextBlock).text)

            controlledChild.status = AgentStatus.RUNNING
            val steer = fixture.control(
                "steer-child", SEND_MESSAGE_TOOL_NAME,
                """{"agent_id":"child","message":"steer now"}""",
                execution(parent.agent),
            )
            assertFalse(steer.isError)
            assertEquals(AgentSendTarget.NEXT_STEP, controlledChild.targets.last())
            assertEquals(AgentWakeup.NONE, controlledChild.wakeups.last())

            val toParent = fixture.control(
                "send-parent", SEND_MESSAGE_TOOL_NAME,
                """{"agent_id":"parent","message":"report please"}""",
                execution(child.agent),
            )
            assertFalse(toParent.isError)
            assertEquals(controlledParent.receipts.last(),
                Json.decodeFromString(SendMessageResult.serializer(), text(toParent)).messageId)
            assertEquals(1, controlledParent.sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sendMessageRejectsSelfSiblingAndNonDirectDescendant() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val child = fixture.create("child", parent = "parent")
            val sibling = fixture.create("sibling", parent = "parent")
            val grandchild = fixture.create("grandchild", parent = "child")
            for ((call, sender, target) in listOf(
                Triple("self", parent.agent, "parent"),
                Triple("grandchild", parent.agent, "grandchild"),
                Triple("sibling", child.agent, "sibling"),
                Triple("unknown", parent.agent, "missing"),
            )) {
                val response = fixture.control(
                    call, SEND_MESSAGE_TOOL_NAME,
                    """{"agent_id":"$target","message":"should not arrive"}""",
                    execution(sender),
                )
                assertTrue(response.isError, call)
                assertContains(text(response), "error")
            }
            assertEquals(0, (parent.agent as ControlledAgent).sendCalls)
            assertEquals(0, (sibling.agent as ControlledAgent).sendCalls)
            assertEquals(0, (grandchild.agent as ControlledAgent).sendCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun interruptAgentStopsVerifiedDescendantWithKeepInboxAndRejectsOthers() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val child = fixture.create("child", parent = "parent")
            val grandchild = fixture.create("grandchild", parent = "child")
            val sibling = fixture.create("sibling", parent = "parent")

            val accepted = fixture.control(
                "interrupt-grandchild", INTERRUPT_AGENT_TOOL_NAME,
                """{"agent_id":"grandchild"}""",
                execution(parent.agent),
            )
            assertFalse(accepted.isError)
            assertTrue(Json.decodeFromString(InterruptAgentResult.serializer(), text(accepted)).accepted)
            val interrupted = grandchild.agent as ControlledAgent
            assertEquals(1, interrupted.cancelCalls)
            assertTrue(interrupted.lastKeepInbox)

            for ((call, sender, target) in listOf(
                Triple("self", parent.agent, "parent"),
                Triple("sibling", child.agent, "sibling"),
                Triple("ancestor", child.agent, "parent"),
                Triple("unknown", parent.agent, "missing"),
            )) {
                val response = fixture.control(
                    call, INTERRUPT_AGENT_TOOL_NAME,
                    """{"agent_id":"$target"}""",
                    execution(sender),
                )
                assertTrue(response.isError, call)
            }
            assertEquals(0, (sibling.agent as ControlledAgent).cancelCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun listAgentsListsDirectChildrenAndDescendantsWithLiveStatus() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val child = fixture.create("child", parent = "parent")
            val second = fixture.create("second", parent = "parent")
            val grandchild = fixture.create("grandchild", parent = "child")
            fixture.create("unrelated")
            (grandchild.agent as ControlledAgent).status = AgentStatus.RUNNING

            val children = fixture.listAgents("children", execution(parent.agent))
            assertEquals(setOf("child", "second"), children.map { it.id }.toSet())
            assertTrue(children.all { it.parent == null && it.depth == null })
            assertEquals("inactive", children.single { it.id == "child" }.status)

            val descendants = fixture.listAgents("descendants", execution(parent.agent))
            assertEquals(setOf("child", "grandchild", "second"), descendants.map { it.id }.toSet())
            val grandchildEntry = descendants.single { it.id == "grandchild" }
            assertEquals("child", grandchildEntry.parent)
            assertEquals(2L, grandchildEntry.depth)
            assertEquals("running", grandchildEntry.status)
            assertEquals(1L, descendants.single { it.id == "child" }.depth)
            assertFalse(descendants.any { it.id == "unrelated" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun spawnStampsDelegationDepthAndRejectsRecursionBeyondMaxDepth() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val first = decode(fixture.execute(parent))
            assertEquals(SubagentOutcome.ACCEPTED, first.outcome, first.error)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(first.childSessionId))))
            assertEquals(1L, child.session.header.delegationDepth)

            val nested = decode(
                fixture.tools.execute(request("nested", child.session.id.value), execution(child)),
            )
            assertEquals(SubagentOutcome.REJECTED, nested.outcome)
            assertContains(assertNotNull(nested.error), "exceeds maxDepth 1")
            assertNull(nested.childSessionId)
            assertEquals(2, fixture.created.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun maxActiveSubagentsLimitsFanOutUntilAChildSettles() = runTest {
        val fixture = fixture(maxActiveSubagents = 1)
        try {
            val parent = fixture.create("parent")
            val first = decode(fixture.execute(parent, "first"))
            assertEquals(SubagentOutcome.ACCEPTED, first.outcome, first.error)
            val child = assertNotNull(fixture.registry.get(AgentId(assertNotNull(first.childSessionId))))

            val limited = decode(fixture.execute(parent, "second"))
            assertEquals(SubagentOutcome.REJECTED, limited.outcome)
            assertContains(assertNotNull(limited.error), "active child limit: 1")
            assertEquals(2, fixture.created.size)

            // The queued prompt is what keeps the child active; claiming it frees the slot.
            child.inbox.claim(InboxQueue.NEXT_TURN)
            val retried = decode(fixture.execute(parent, "third"))
            assertEquals(SubagentOutcome.ACCEPTED, retried.outcome, retried.error)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun settlementDeliversClosingOutputAndDefersWhileTheChildHasQueuedInput() = runTest {
        val fixture = fixture()
        try {
            val parent = fixture.create("parent")
            val controlledParent = parent.agent as ControlledAgent

            val settled = decode(fixture.execute(parent, "settled"))
            val settledChild = assertNotNull(
                fixture.registry.get(AgentId(assertNotNull(settled.childSessionId))),
            )
            settledChild.inbox.claim(InboxQueue.NEXT_TURN)
            settledChild.finishTurn(1, "all done")

            val deferred = decode(fixture.execute(parent, "deferred"))
            val deferredChild = assertNotNull(
                fixture.registry.get(AgentId(assertNotNull(deferred.childSessionId))),
            )
            deferredChild.finishTurn(1, "must not be reported yet")

            val notice = realAwait(10_000) { controlledParent.deliveries.receive() }
            assertEquals(
                listOf(
                    TextBlock(
                        "Background subagent ${settledChild.id.value} finished and will do no further " +
                            "work unless you send it more.",
                    ),
                    TextBlock("Its closing message:"),
                    TextBlock("all done"),
                ),
                notice.content,
            )
            assertEquals(AgentSendTarget.NEXT_TURN, controlledParent.targets.single())
            withContext(Dispatchers.Default) {
                delay(300)
                assertTrue(controlledParent.deliveries.tryReceive().isFailure)
            }

            deferredChild.inbox.claim(InboxQueue.NEXT_TURN)
            deferredChild.finishTurn(2, "later")
            val second = realAwait(10_000) { controlledParent.deliveries.receive() }
            assertEquals(TextBlock("Background subagent ${deferredChild.id.value} finished and will do no " +
                "further work unless you send it more."), second.content.first())
        } finally {
            fixture.close()
        }
    }

    private suspend fun Agent.finishTurn(
        turn: Long,
        text: String?,
        reason: TurnEndReason = CompletedTurnEndReason,
    ) {
        if (text != null) {
            session.append(
                SessionEventKeys.AssistantMessage,
                AssistantMessageEvent(
                    turn = turn,
                    step = 1,
                    message = createAssistantMessage(listOf(TextBlock(text)), "test-provider", "test-model"),
                ),
            )
        }
        session.append(SessionEventKeys.TurnEnd, TurnEndEvent(turn, reason))
    }

    private suspend fun <T> realAwait(timeoutMillis: Long, block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(timeoutMillis) { block() } }

    private suspend fun fixture(
        maxDepth: Long = DEFAULT_SUBAGENT_MAX_DEPTH,
        maxActiveSubagents: Int = DEFAULT_MAX_ACTIVE_SUBAGENTS,
    ): Fixture {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.install(ToolsPlugin())
        runtime.install(SessionApiPlugin())
        val fixture = Fixture(runtime)
        fixture.registry.registerFactory(AgentFactory { request ->
            fixture.beforeCreate(request)
            ControlledAgent(request, fixture).also { fixture.created += it }
        })
        ToolSubagentPlugin(maxDepth = maxDepth, maxActiveSubagents = maxActiveSubagents)
            .apply(runtime.context, fixture.scope)
        ToolSubagentControlPlugin().apply(runtime.context, fixture.scope)
        ListAgentsPlugin().apply(runtime.context, fixture.scope)
        runtime.context.effect { fixture.scope.dispose() }
        return fixture
    }

    private class Fixture(val runtime: Runtime) {
        val registry get() = runtime.context.agents
        val tools get() = runtime.context.require(ToolsKey)
        val api get() = runtime.context.sessionApi
        val scope = EffectScope()
        val created = mutableListOf<ControlledAgent>()
        var beforeCreate: suspend (AgentFactoryRequest) -> Unit = {}
        var beforeSend: suspend (ControlledAgent, Message) -> Unit = { _, _ -> }
        var afterSend: suspend (ControlledAgent, String) -> Unit = { _, _ -> }
        var cancelFailure = false

        suspend fun create(
            id: String, parent: String? = null,
            origin: SessionOrigin? = if (parent == null) null else SessionOrigin.SUBAGENT,
            cwd: String? = null,
            provider: String? = null,
            model: String? = null,
            reasoningEffort: ReasoningEffortId? = null,
            maxTokens: Long? = null,
            reasoning: ReasoningOptions? = null,
        ): AgentHandle = registry.create(
            id = AgentId(id),
            options = AgentOptions(
                cwd = cwd, provider = provider, model = model,
                reasoningEffort = reasoningEffort, maxTokens = maxTokens,
                reasoning = reasoning,
            ),
            sessionOptions = CreateSessionOptions(cwd = cwd, parentSession = parent?.let(::SessionId), origin = origin),
        )

        suspend fun execute(parent: AgentHandle, call: String = "spawn", arguments: String = SPAWN_ARGUMENTS): ToolExecutionResult =
            tools.execute(
                request(call, parent.agent.session.id.value, arguments = arguments), execution(parent.agent),
            )

        suspend fun control(
            call: String,
            name: String,
            arguments: String,
            execution: ToolExecutionContext,
        ): ToolExecutionResult = tools.execute(
            ToolCallRequest(CallId(call), name, arguments, scope = execution.session.id.value),
            execution,
        )

        suspend fun listAgents(scope: String, execution: ToolExecutionContext): List<ListAgentsEntry> {
            val response = control("list-$scope", LIST_AGENTS_TOOL_NAME, """{"scope":"$scope"}""", execution)
            assertFalse(response.isError)
            return Json.decodeFromString(ListSerializer(ListAgentsEntry.serializer()), text(response))
        }

        suspend fun close() { runtime.context.dispose() }
    }

    private class ControlledAgent(request: AgentFactoryRequest, private val fixture: Fixture) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val context: Context = request.context
        override val inbox = request.inbox
        override var status = AgentStatus.IDLE
        var awaitIdleCalls = 0
        var sendCalls = 0
        var cancelCalls = 0
        var lastKeepInbox = false
        val messages = mutableListOf<Message>()
        val targets = mutableListOf<AgentSendTarget>()
        val wakeups = mutableListOf<AgentWakeup>()
        val receipts = mutableListOf<String>()
        val deliveries = Channel<Message>(Channel.UNLIMITED)
        var failCancel = fixture.cancelFailure
        override suspend fun awaitIdle() { awaitIdleCalls++ }
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) {
            cancelCalls++
            lastKeepInbox = keepInbox
            if (failCancel) error("controlled cancel failure")
        }
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String {
            sendCalls++
            messages += message
            targets += target
            wakeups += wakeup
            fixture.beforeSend(this, message)
            val receipt = inbox.insert(message, target, wakeup)
            receipts += receipt
            deliveries.trySend(message)
            fixture.afterSend(this, receipt)
            return receipt
        }
    }

    companion object {
        private const val SPAWN_ARGUMENTS =
            """{"description":"delegate work","prompt":"actual task","provider":"selected-provider","model":"selected-model"}"""

        private fun execution(agent: Agent) = ToolExecutionContext(agent.context, agent.session)
        private fun request(
            call: String,
            scope: String?,
            name: String = SUBAGENT_TOOL_NAME,
            arguments: String = SPAWN_ARGUMENTS,
        ) = ToolCallRequest(
            callId = CallId(call), name = name, scope = scope, arguments = arguments,
        )
        private fun decode(result: ToolExecutionResult): SubagentResult = Json.decodeFromString(
            SubagentResult.serializer(), text(result),
        )
        private fun text(result: ToolExecutionResult): String =
            (result.content.single() as TextBlock).text
    }
}
