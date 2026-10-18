package im.hikaru.harness.client.conversation

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationControllerTest {
    @Test
    fun switchingConversationKeepsCommittedReadRevisionForTheNextManagementCommand() = runTest {
        var stored = SUMMARY.copy(lastEventSeq = 8, unread = true, managementAvailable = true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var updates = 0
        val operations = FakeOperations().apply {
            listResult = { listOf(stored, SUMMARY.copy(id = OTHER)) }
            updateResult = { update ->
                if (++updates == 1) withContext(NonCancellable) { entered.complete(Unit); release.await() }
                check(stored.revision == update.expectedRevision) { "Stale metadata revision" }
                stored.copy(lastReadSeq = update.lastReadSeq, pinned = update.pinned,
                    revision = stored.revision + 1).also { stored = it }
            }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST); runCurrent()
        controller.select(SESSION); runCurrent()
        controller.markRead(SESSION, 8); runCurrent(); entered.await()
        controller.select(OTHER)
        release.complete(Unit); runCurrent()
        assertEquals(OTHER, controller.state.value.selectedSessionId)
        assertTrue(assertNotNull(controller.manage(SESSION, ConversationCommand.TogglePin)).await())
        assertEquals(2, stored.revision)
        assertTrue(stored.pinned)
        controller.close()
    }

    @Test
    fun catalogAdvancingDuringHistoryReadRequiresAFreshReadBeforeAcknowledging() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val firstRelease = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val secondRelease = CompletableDeferred<Unit>()
        var reads = 0
        val operations = FakeOperations().apply {
            listResult = { listOf(SUMMARY.copy(lastEventSeq = 4, managementAvailable = true)) }
            historyResult = { _, _ ->
                if (++reads == 1) { firstEntered.complete(Unit); firstRelease.await() }
                else { secondEntered.complete(Unit); secondRelease.await() }
                listOf(message("visible-$reads"))
            }
            updateResult = { SUMMARY.copy(lastEventSeq = 9, revision = 1, managementAvailable = true) }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST); runCurrent()
        controller.select(SESSION); runCurrent(); firstEntered.await()
        assertTrue(assertNotNull(controller.manage(SESSION, ConversationCommand.TogglePin)).await())
        firstRelease.complete(Unit); runCurrent(); secondEntered.await()
        assertEquals(4, controller.state.value.historyReadSeq)
        assertEquals(ConversationDataPhase.Loading, controller.state.value.historyPhase)
        secondRelease.complete(Unit); runCurrent()
        assertEquals(9, controller.state.value.historyReadSeq)
        assertEquals(ConversationDataPhase.Ready, controller.state.value.historyPhase)
        assertEquals(2, reads)
        controller.close()
    }

    @Test
    fun explicitUnreadFollowsInFlightReadWithoutBusyStateOrRevisionConflict() = runTest {
        var stored = SUMMARY.copy(lastEventSeq = 8, unread = true, managementAvailable = true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<ConversationUpdate>()
        var lists = 0
        val operations = FakeOperations().apply {
            listResult = { lists++; listOf(stored) }
            updateResult = { update ->
                requests += update
                if (requests.size == 1) { entered.complete(Unit); release.await() }
                assertEquals(stored.revision, update.expectedRevision)
                stored.copy(lastReadSeq = update.lastReadSeq, unread = update.lastReadSeq < stored.lastEventSeq,
                    revision = stored.revision + 1).also { stored = it }
            }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent(); controller.select(SESSION); runCurrent()
        controller.markRead(SESSION, 8)
        runCurrent(); entered.await()
        assertNull(controller.state.value.managingSessionId)
        val unread = assertNotNull(controller.manage(SESSION, ConversationCommand.MarkUnread))
        release.complete(Unit)
        assertTrue(unread.await())
        assertEquals(listOf(8L, -1L), requests.map { it.lastReadSeq })
        assertTrue(controller.state.value.sessions.single().unread)
        assertEquals(1, lists, "Metadata commands must update the returned row without reloading the catalog")
        controller.close()
    }

    @Test
    fun archiveUpdatesTheSelectedRowAndRenameReportsFailureToItsEditor() = runTest {
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent(); controller.select(SESSION); runCurrent()
        assertTrue(assertNotNull(controller.manage(SESSION, ConversationCommand.ToggleArchive)).await())
        assertTrue(controller.state.value.sessions.single().archived)
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        operations.updateResult = { error("Rejected") }
        assertFalse(assertNotNull(controller.manage(SESSION, ConversationCommand.Rename("keep draft"))).await())
        assertNull(controller.state.value.managementError, "The rename editor owns its error feedback")
        controller.close()
    }

    @Test
    fun deleteClearsSelectedConversationAndItsDraftAfterAcknowledgement() = runTest {
        var deleted = false
        val operations = FakeOperations().apply {
            listResult = { if (deleted) emptyList() else listOf(SUMMARY) }
            deleteResult = { _, _ -> deleted = true }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent()
        controller.select(SESSION)
        controller.updateDraft("old draft")
        runCurrent()
        assertTrue(assertNotNull(controller.manage(SESSION, ConversationCommand.Delete)).await())
        runCurrent()
        assertNull(controller.state.value.selectedSessionId)
        assertTrue(controller.state.value.sessions.isEmpty())
        controller.select(SESSION)
        assertEquals("", controller.state.value.draft)
        controller.close()
    }

    @Test
    fun managementKeepsCustomTitleAndReadPositionAndFencesHostChanges() = runTest {
        val captured = CompletableDeferred<ConversationUpdate>()
        val release = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply {
            listResult = { listOf(SUMMARY.copy(title = "automatic", customTitle = null,
                lastReadSeq = 3, lastEventSeq = 8, unread = true, revision = 2, managementAvailable = true)) }
            updateResult = { update -> captured.complete(update); release.await(); SUMMARY }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent()
        val command = assertNotNull(controller.manage(SESSION, ConversationCommand.TogglePin))
        runCurrent()
        assertEquals(ConversationUpdate(SESSION, 2, null, true, 3), captured.await())
        assertNull(controller.manage(SESSION, ConversationCommand.Rename("other")), "Only one management command can be in flight")
        controller.bind(null, null)
        release.complete(Unit)
        command.join()
        assertEquals(ConversationState(), controller.state.value)
        controller.close()
    }

    @Test
    fun durableHistoryAllowsSendButDoesNotOfferStopAndArchiveBlocksSend() {
        val state = ConversationState(host = HOST, connected = true, selectedSessionId = SESSION,
            draft = "continue", sessions = listOf(SUMMARY.copy(live = false)))
        assertTrue(state.canSubmit)
        assertFalse(state.canCancel)
        assertFalse(state.copy(sessions = listOf(SUMMARY.copy(archived = true))).canSubmit)
    }

    @Test
    fun recordedModelFailureIsShownAfterLocalIdleWithoutResending() = runTest {
        val operations = FakeOperations().apply {
            promptResult = { id, _ -> object : SessionPrompt {
                override val receipt = SessionPromptReceipt(id, "failed-prompt")
                override suspend fun awaitIdle() = Unit
                override suspend fun failure() = SessionPromptFailure("KOOG_UNSUPPORTED_TOOL_SCHEMA", 400)
            } }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.updateDraft("hello")
        assertNotNull(controller.submit()).join()
        runCurrent()
        assertEquals("模型执行失败（KOOG_UNSUPPORTED_TOOL_SCHEMA）。",
            controller.state.value.errors[ConversationErrorKind.Completion]?.message)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertEquals(1, operations.promptIds.size)
        assertNotNull(controller.state.value.lastAcceptedReceipt)
        controller.close()
    }

    @Test
    fun rejectedPreparationNeverCreatesOrPromptsAndRetainsDraft() = runTest {
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("keep pending")
        val revision = controller.state.value.draftRevision
        var preparations = 0
        var associations = 0
        assertNotNull(controller.submit(
            prepareCreation = { preparations++; null },
            onSessionCreated = { associations++; true },
        )).join()
        controller.refresh()
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(0, associations)
        assertEquals(0, operations.createCalls)
        assertTrue(operations.promptIds.isEmpty())
        assertNull(controller.state.value.selectedSessionId)
        assertNull(controller.state.value.lastAcceptedReceipt)
        assertEquals("keep pending", controller.state.value.draft)
        assertEquals(revision, controller.state.value.draftRevision)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertTrue(controller.state.value.canSubmit)
        assertTrue(controller.state.value.errors.isEmpty())
        controller.close()
    }

    @Test
    fun preparedOptionsAndCreatedAssociationPrecedeSinglePrompt() = runTest {
        val entered = CompletableDeferred<SessionSummary>()
        val release = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply { createResult = { SessionSummary(OTHER, createdAt = 2) } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.updateDraft("original")
        val prepared = CreateSessionOptions(cwd = "/prepared-project")
        val agent = AgentOptions(provider = "catalog-provider", model = "catalog-model")
        var preparations = 0
        var associations = 0
        val send = assertNotNull(controller.submit(
            options = CreateSessionOptions(cwd = "/fallback-project"),
            agentOptions = agent,
            prepareCreation = { preparations++; prepared },
            onSessionCreated = { created ->
                associations++
                entered.complete(created)
                release.await()
                true
            },
        ))
        runCurrent()
        assertTrue(entered.isCompleted)
        assertEquals(SessionSummary(OTHER, createdAt = 2), entered.await())
        assertEquals(OTHER, controller.state.value.selectedSessionId)
        assertEquals(prepared, operations.createdOptions)
        assertEquals(agent, operations.createdAgentOptions)
        assertTrue(operations.promptIds.isEmpty(), "association must finish before delivery")
        assertEquals(ConversationAction.Associating, controller.state.value.action)
        assertNull(controller.submit())
        controller.refresh()
        controller.updateDraft("next draft")
        val revision = controller.state.value.draftRevision
        runCurrent()
        assertTrue(send.isActive)
        assertEquals(1, preparations)
        assertEquals(1, associations)
        assertEquals(1, operations.createCalls)
        assertTrue(operations.promptIds.isEmpty())
        release.complete(Unit)
        send.join()
        runCurrent()
        assertEquals(listOf(OTHER), operations.promptIds)
        assertEquals("original", (operations.promptMessages.single().content.single() as TextContent).text)
        assertEquals(SessionPromptReceipt(OTHER, "accepted-1"), controller.state.value.lastAcceptedReceipt)
        assertEquals("next draft", controller.state.value.draft)
        assertEquals(revision, controller.state.value.draftRevision)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertTrue(controller.state.value.errors.isEmpty())
        controller.close()
    }

    @Test
    fun nonCooperativePreparationAfterThreadSelectionNeverCreates() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("Home draft")
        var returned = false
        var associations = 0
        val send = assertNotNull(controller.submit(
            prepareCreation = {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                returned = true
                CreateSessionOptions(cwd = "/obsolete-project")
            },
            onSessionCreated = { associations++; true },
        ))
        runCurrent()
        assertTrue(entered.isCompleted)
        controller.select(OTHER)
        controller.updateDraft("Thread draft")
        runCurrent()
        val selectedState = controller.state.value
        release.complete(Unit)
        send.join()
        runCurrent()
        assertTrue(returned, "exercise a late value despite cancellation")
        assertTrue(send.isCancelled)
        assertEquals(0, operations.createCalls)
        assertEquals(0, associations)
        assertTrue(operations.promptIds.isEmpty())
        assertEquals(selectedState, controller.state.value)
        assertEquals(OTHER, controller.state.value.selectedSessionId)
        assertEquals("Thread draft", controller.state.value.draft)
        controller.close()
    }

    @Test
    fun nonCooperativePreparationAfterOwnerChangesNeverCreates() = runTest {
        val tenant = BackendTenant("https://member.example", 1)
        val original = HOST.copy(identity = MemberIdentity(tenant, 1))
        val replacements = listOf(
            "connection reference" to original,
            "generation" to original.copy(generation = 2),
            "member identity" to original.copy(identity = MemberIdentity(tenant, 2)),
            "host" to original.copy(hostId = HostId("host-b")),
            "disconnect" to original,
        )
        for ((reason, replacement) in replacements) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val operations = FakeOperations()
            val controller = ConversationController(backgroundScope, operations)
            controller.bind(operations.connection, original)
            controller.updateDraft("old owner draft")
            var returned = false
            var associations = 0
            val send = assertNotNull(controller.submit(
                prepareCreation = {
                    entered.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    returned = true
                    CreateSessionOptions(cwd = "/old-owner-project")
                },
                onSessionCreated = { associations++; true },
            ))
            runCurrent()
            assertTrue(entered.isCompleted, reason)
            val nextConnection = when (reason) {
                "connection reference" -> FakeConnection()
                "disconnect" -> null
                else -> operations.connection
            }
            controller.bind(nextConnection, replacement)
            controller.updateDraft("current owner draft")
            runCurrent()
            val reboundState = controller.state.value
            release.complete(Unit)
            send.join()
            runCurrent()
            assertTrue(returned, reason)
            assertTrue(send.isCancelled, reason)
            assertEquals(0, operations.createCalls, reason)
            assertEquals(0, associations, reason)
            assertTrue(operations.promptIds.isEmpty(), reason)
            assertEquals(reboundState, controller.state.value, reason)
            assertEquals(replacement, controller.state.value.host, reason)
            assertEquals("current owner draft", controller.state.value.draft, reason)
            controller.close()
        }
    }

    @Test
    fun rejectedOrFailedAssociationRetainsCreatedIdAndDraftWithoutRefreshRetry() = runTest {
        for (throws in listOf(false, true)) {
            val created = SessionSummary(OTHER, createdAt = 2)
            val operations = FakeOperations().apply { createResult = { created } }
            val controller = ConversationController(backgroundScope, operations)
            controller.bind(operations.connection, HOST)
            controller.updateDraft("unsent draft")
            val revision = controller.state.value.draftRevision
            var preparations = 0
            val associations = mutableListOf<SessionSummary>()
            assertNotNull(controller.submit(
                prepareCreation = { preparations++; CreateSessionOptions(cwd = "/project") },
                onSessionCreated = {
                    associations += it
                    if (throws) error("private association credentials")
                    false
                },
            )).join()
            runCurrent()
            assertEquals(OTHER, controller.state.value.selectedSessionId)
            assertTrue(controller.state.value.sessions.contains(created))
            assertEquals("unsent draft", controller.state.value.draft)
            assertEquals(revision, controller.state.value.draftRevision)
            assertEquals(ConversationAction.Idle, controller.state.value.action)
            assertEquals(ConversationRunState.Unknown, controller.state.value.runState)
            assertNull(controller.state.value.lastAcceptedReceipt)
            val failure = assertNotNull(controller.state.value.errors[ConversationErrorKind.Association])
            assertEquals(ConversationErrorKind.Association, failure.kind)
            assertNull(failure.retry, "refresh must not repeat association or delivery")
            assertFalse(failure.message.contains("private association credentials"))
            assertEquals(setOf(ConversationErrorKind.Association), controller.state.value.errors.keys)
            repeat(2) { controller.refresh(); runCurrent() }
            assertEquals(1, operations.createCalls)
            assertEquals(1, preparations)
            assertEquals(listOf(created), associations)
            assertTrue(operations.promptIds.isEmpty())
            assertTrue(operations.promptMessages.isEmpty())
            assertEquals(0, operations.awaitIdleCalls)
            assertEquals(OTHER, controller.state.value.selectedSessionId, "list omission must not discard the created route")
            assertEquals("unsent draft", controller.state.value.draft)
            assertEquals(revision, controller.state.value.draftRevision)
            assertEquals(failure, controller.state.value.errors[ConversationErrorKind.Association])
            controller.close()
        }
    }

    @Test
    fun stopDuringAssociationCancelsLocalWorkAndPreservesUnsentDraftAndSession() = runTest {
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("unsent project message")
        val revision = controller.state.value.draftRevision
        val send = assertNotNull(controller.submit(onSessionCreated = {
            entered.complete(Unit)
            try { released.await(); true } finally { cleaned.complete(Unit) }
        }))
        runCurrent()
        assertTrue(entered.isCompleted)
        assertEquals(ConversationAction.Associating, controller.state.value.action)
        assertTrue(controller.state.value.canCancel)
        assertNotNull(controller.cancel()).join()
        runCurrent()
        assertTrue(send.isCancelled)
        assertTrue(cleaned.isCompleted)
        assertEquals(1, operations.createCalls)
        assertTrue(operations.promptIds.isEmpty())
        assertTrue(operations.cancelIds.isEmpty(), "no backend work was started")
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals("unsent project message", controller.state.value.draft)
        assertEquals(revision, controller.state.value.draftRevision)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertFalse(controller.state.value.cancelling)
        assertNull(controller.state.value.lastAcceptedReceipt)
        controller.close()
    }

    @Test
    fun stopWaitsForNonCooperativeAssociationAndNeverDeliversItsLateReply() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("keep draft")
        val send = assertNotNull(controller.submit(onSessionCreated = {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            true
        }))
        runCurrent()
        assertTrue(entered.isCompleted)
        val stop = assertNotNull(controller.cancel())
        runCurrent()
        assertTrue(stop.isActive)
        assertTrue(controller.state.value.cancelling)
        assertFalse(controller.state.value.canSubmit)
        assertNull(controller.submit())
        assertTrue(operations.promptIds.isEmpty())
        release.complete(Unit)
        stop.join()
        send.join()
        runCurrent()
        assertTrue(send.isCancelled)
        assertTrue(operations.promptIds.isEmpty())
        assertTrue(operations.cancelIds.isEmpty())
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals("keep draft", controller.state.value.draft)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertFalse(controller.state.value.cancelling)
        controller.close()
    }

    @Test
    fun existingSessionBypassesBothCreationHooks() = runTest {
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.select(OTHER)
        controller.updateDraft("existing session message")
        var preparations = 0
        var associations = 0
        assertNotNull(controller.submit(
            prepareCreation = { preparations++; error("must not prepare an existing session") },
            onSessionCreated = { associations++; error("must not associate an existing session") },
        )).join()
        runCurrent()
        assertEquals(0, preparations)
        assertEquals(0, associations)
        assertEquals(0, operations.createCalls)
        assertEquals(listOf(OTHER), operations.promptIds)
        assertEquals("existing session message", (operations.promptMessages.single().content.single() as TextContent).text)
        assertEquals(SessionPromptReceipt(OTHER, "accepted-1"), controller.state.value.lastAcceptedReceipt)
        assertEquals("", controller.state.value.draft)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertTrue(controller.state.value.errors.isEmpty())
        controller.close()
    }

    @Test
    fun nonCooperativeAssociationCannotSendOrPolluteNewSelection() = runTest {
        for (outcome in listOf("accepted", "rejected", "failed")) {
            val entered = CompletableDeferred<SessionSummary>()
            val release = CompletableDeferred<Unit>()
            val operations = FakeOperations()
            val controller = ConversationController(backgroundScope, operations)
            controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
            controller.updateDraft("old Home message")
            var associations = 0
            val send = assertNotNull(controller.submit(onSessionCreated = { created ->
                associations++
                entered.complete(created)
                withContext(NonCancellable) {
                    release.await()
                    if (outcome == "failed") error("obsolete association failed")
                    outcome == "accepted"
                }
            }))
            runCurrent()
            assertTrue(entered.isCompleted, outcome)
            assertEquals(SUMMARY, entered.await())
            assertEquals(SESSION, controller.state.value.selectedSessionId)
            assertTrue(operations.promptIds.isEmpty(), outcome)
            controller.select(OTHER)
            controller.updateDraft("new Thread message")
            runCurrent()
            val selectedState = controller.state.value
            release.complete(Unit)
            send.join()
            runCurrent()
            assertTrue(send.isCancelled, outcome)
            assertEquals(1, operations.createCalls, outcome)
            assertEquals(1, associations, outcome)
            assertTrue(operations.promptIds.isEmpty(), outcome)
            assertEquals(selectedState, controller.state.value, outcome)
            assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Association), outcome)
            assertTrue(operations.cancelIds.isEmpty(), "navigation must not cancel backend work")
            assertNotNull(controller.submit()).join()
            runCurrent()
            assertEquals(1, operations.createCalls, outcome)
            assertEquals(listOf(OTHER), operations.promptIds, outcome)
            assertEquals("new Thread message", (operations.promptMessages.single().content.single() as TextContent).text)
            assertEquals(SessionPromptReceipt(OTHER, "accepted-1"), controller.state.value.lastAcceptedReceipt)
            controller.close()
        }
    }

    @Test
    fun homeCreateRebasesSendFenceAndPassesExplicitModelOptions() = runTest {
        val operations = FakeOperations()
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.updateDraft("hello")
        assertTrue(controller.state.value.canSubmit, "Home does not need an existing session id")
        val options = CreateSessionOptions(cwd = "/project")
        val agent = AgentOptions(provider = "catalog-provider", model = "catalog-model", reasoningEffort = ReasoningEffortId("high"))
        assertNotNull(controller.submit(options, agent)).join()
        runCurrent()
        assertEquals(options, operations.createdOptions)
        assertEquals(agent, operations.createdAgentOptions)
        assertEquals(1, operations.createCalls)
        assertEquals(listOf(SESSION), operations.promptIds)
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals(SessionPromptReceipt(SESSION, "accepted-1"), controller.state.value.lastAcceptedReceipt)
        assertEquals("", controller.state.value.draft)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertEquals(ConversationRunState.Idle, controller.state.value.runState)
        assertTrue(controller.state.value.errors.isEmpty())
        controller.close()
    }

    @Test
    fun delayedCreateAcquiresBusyAndPreventsDuplicateBeforeSuspension() = runTest {
        val gate = CompletableDeferred<SessionSummary>()
        val operations = FakeOperations().apply { createResult = { gate.await() } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("hello")
        val send = assertNotNull(controller.submit())
        assertNull(controller.submit())
        assertEquals(ConversationAction.Creating, controller.state.value.action)
        assertFalse(controller.state.value.canCancel)
        runCurrent()
        controller.refresh()
        runCurrent()
        assertTrue(send.isActive)
        assertEquals(1, operations.createCalls)
        gate.complete(SUMMARY)
        send.join()
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals(1, operations.promptIds.size)
        controller.close()
    }

    @Test
    fun createFailurePreservesDraftAndReportsCreate() = runTest {
        val operations = FakeOperations().apply { createResult = { error("credential must not escape") } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("keep me")
        assertNotNull(controller.submit()).join()
        val state = controller.state.value
        assertNull(state.selectedSessionId)
        assertEquals("keep me", state.draft)
        assertEquals(ConversationRetry.SubmitExplicitly, state.errors[ConversationErrorKind.Create]?.retry)
        assertFalse(state.errors.values.any { it.message.contains("credential") })
        assertFalse(state.errors.containsKey(ConversationErrorKind.Prompt))
        assertTrue(state.canSubmit)
        controller.close()
    }

    @Test
    fun failedPromptRetainsCreatedIdAndExplicitRetryDoesNotRecreate() = runTest {
        val operations = FakeOperations().apply { promptResult = { _, _ -> error("prompt rejected") } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("hello")
        assertNotNull(controller.submit()).join()
        runCurrent()
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals("hello", controller.state.value.draft)
        assertTrue(controller.state.value.errors.containsKey(ConversationErrorKind.Prompt))
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Create))
        controller.refresh()
        runCurrent()
        assertEquals(1, operations.promptIds.size, "refresh must never resend")
        operations.promptResult = { id, _ -> operations.accepted(id) }
        assertNotNull(controller.submit()).join()
        assertEquals(1, operations.createCalls)
        assertEquals(listOf(SESSION, SESSION), operations.promptIds)
        assertEquals("", controller.state.value.draft)
        assertNotNull(controller.state.value.lastAcceptedReceipt)
        controller.close()
    }

    @Test
    fun acceptedPromptAndHistoryFailureRemainAccepted() = runTest {
        val operations = FakeOperations().apply { historyResult = { _, _ -> error("missing live agent") } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("sent")
        assertNotNull(controller.submit()).join()
        runCurrent()
        assertEquals("", controller.state.value.draft)
        assertEquals("accepted-1", controller.state.value.lastAcceptedMessageId)
        assertEquals(ConversationAction.RunningUnknown, controller.state.value.action)
        assertEquals(ConversationDataPhase.Failed, controller.state.value.historyPhase)
        assertEquals(ConversationRetry.History, controller.state.value.errors[ConversationErrorKind.History]?.retry)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Prompt))
        controller.close()
    }

    @Test
    fun draftRevisionProtectsEditAwayAndBackDuringPrompt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply { promptResult = { id, _ -> gate.await(); accepted(id) } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("original")
        val send = assertNotNull(controller.submit())
        assertNull(controller.submit())
        runCurrent()
        controller.updateDraft("changed")
        controller.updateDraft("original")
        val revision = controller.state.value.draftRevision
        gate.complete(Unit)
        send.join()
        assertEquals("original", controller.state.value.draft)
        assertEquals(revision, controller.state.value.draftRevision)
        assertEquals("original", (operations.promptMessages.single().content.single() as TextContent).text)
        assertNotNull(controller.state.value.lastAcceptedReceipt)
        controller.close()
    }

    @Test
    fun editsDuringCreateAndLocalCompletionArePreserved() = runTest {
        val create = CompletableDeferred<SessionSummary>()
        val idle = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply {
            createResult = { create.await() }
            idleResult = { idle.await() }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.updateDraft("snapshot")
        val send = assertNotNull(controller.submit())
        runCurrent()
        controller.updateDraft("during create")
        create.complete(SUMMARY)
        runCurrent()
        assertEquals(ConversationAction.WaitingForLocalIdle, controller.state.value.action)
        assertEquals(ConversationRunState.Running, controller.state.value.runState)
        assertEquals("during create", controller.state.value.draft)
        controller.updateDraft("during completion")
        controller.refresh()
        runCurrent()
        assertTrue(send.isActive)
        idle.complete(Unit)
        send.join()
        assertEquals("during completion", controller.state.value.draft)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertEquals(ConversationRunState.Idle, controller.state.value.runState)
        assertFalse(controller.state.value.canCancel)
        assertEquals(1, operations.awaitIdleCalls)
        controller.close()
    }

    @Test
    fun localCompletionFailurePreservesReceiptAndClearedDraft() = runTest {
        val operations = FakeOperations().apply { idleResult = { error("completion unavailable") } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.select(SESSION)
        controller.updateDraft("accepted")
        assertNotNull(controller.submit()).join()
        runCurrent()
        assertEquals("", controller.state.value.draft)
        assertNotNull(controller.state.value.lastAcceptedReceipt)
        assertEquals(ConversationRunState.Unknown, controller.state.value.runState)
        assertTrue(controller.state.value.errors.containsKey(ConversationErrorKind.Completion))
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Prompt))
        controller.close()
    }

    @Test
    fun deliveryAcknowledgementNeverCallsAwaitIdleOrBecomesIdleOnRefresh() = runTest {
        val operations = FakeOperations().apply { idleResult = { error("must not be called for relay semantics") } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("remote")
        assertNotNull(controller.submit()).join()
        controller.refresh()
        runCurrent()
        assertEquals(0, operations.awaitIdleCalls)
        assertEquals(CompletionCapability.DeliveryAcknowledgedOnly, controller.state.value.completionCapability)
        assertEquals(ConversationAction.RunningUnknown, controller.state.value.action)
        assertEquals(ConversationRunState.Unknown, controller.state.value.runState)
        assertTrue(controller.state.value.canCancel)
        controller.updateDraft("next")
        assertFalse(controller.state.value.canSubmit, "receipt/history cannot prove agent completion")
        controller.close()
    }

    @Test
    fun delayedCancelSurvivesRefreshAndFailureRestoresRunningAction() = runTest {
        val gate = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply { cancelResult = { gate.await(); error("stop unavailable") } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("remote")
        assertNotNull(controller.submit()).join()
        val stop = assertNotNull(controller.cancel())
        assertNull(controller.cancel())
        assertFalse(controller.state.value.canCancel)
        runCurrent()
        controller.refresh()
        runCurrent()
        assertTrue(stop.isActive)
        gate.complete(Unit)
        stop.join()
        assertFalse(controller.state.value.cancelling)
        assertEquals(ConversationAction.RunningUnknown, controller.state.value.action)
        assertEquals(ConversationRetry.Cancel, controller.state.value.errors[ConversationErrorKind.Cancel]?.retry)
        assertTrue(controller.state.value.canCancel)
        assertEquals(listOf(SESSION), operations.cancelIds)
        controller.close()
    }

    @Test
    fun stopAcknowledgementReleasesLocalWaitWithoutClaimingIdle() = runTest {
        val idle = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply { idleResult = { idle.await() } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.select(SESSION)
        controller.updateDraft("run")
        val send = assertNotNull(controller.submit())
        runCurrent()
        assertNotNull(controller.cancel()).join()
        send.join()
        assertTrue(send.isCancelled)
        assertFalse(controller.state.value.cancelling)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertEquals(ConversationRunState.Unknown, controller.state.value.runState)
        assertNotNull(controller.state.value.lastAcceptedReceipt)
        controller.close()
    }

    @Test
    fun routeIdIsAuthoritativeEvenWhenAbsentFromList() = runTest {
        val operations = FakeOperations().apply {
            historyResult = { _, id -> if (id == OTHER) error("not live") else listOf(message("real")) }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(OTHER)
        runCurrent()
        assertEquals(OTHER, controller.state.value.selectedSessionId)
        assertEquals(listOf(OTHER), operations.historyIds)
        assertEquals(ConversationDataPhase.Failed, controller.state.value.historyPhase)
        assertEquals(listOf(SUMMARY), controller.state.value.sessions)
        controller.select(SESSION)
        runCurrent()
        assertEquals(listOf(message("real")), controller.state.value.history)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.History))
        controller.close()
    }

    @Test
    fun latestListAndHistoryRequestsWinOverNonCooperativeOldResponses() = runTest {
        val listGate = CompletableDeferred<Unit>()
        val historyGate = CompletableDeferred<Unit>()
        var lists = 0
        var histories = 0
        val operations = FakeOperations().apply {
            listResult = {
                if (++lists == 1) withContext(NonCancellable) { listGate.await(); error("old list failure") }
                else listOf(SessionSummary(OTHER, 2))
            }
            historyResult = { _, _ ->
                if (++histories == 1) withContext(NonCancellable) { historyGate.await(); listOf(message("stale")) }
                else listOf(message("latest"))
            }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent()
        controller.select(SESSION)
        runCurrent()
        controller.refresh()
        runCurrent()
        assertEquals(listOf(SessionSummary(OTHER, 2)), controller.state.value.sessions)
        assertEquals(listOf(message("latest")), controller.state.value.history)
        listGate.complete(Unit)
        historyGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(SessionSummary(OTHER, 2)), controller.state.value.sessions)
        assertEquals(listOf(message("latest")), controller.state.value.history)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.List))
        assertEquals(ConversationDataPhase.Ready, controller.state.value.listPhase)
        controller.close()
    }

    @Test
    fun stalePromptFailureCannotChangeNewSelection() = runTest {
        val gate = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply {
            promptResult = { _, _ -> withContext(NonCancellable) { gate.await(); error("old selection failed") } }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("keep")
        val send = assertNotNull(controller.submit())
        runCurrent()
        controller.select(OTHER)
        runCurrent()
        assertEquals("", controller.state.value.draft, "another session must not inherit the pending draft")
        controller.updateDraft("other session draft")
        val otherRevision = controller.state.value.draftRevision
        gate.complete(Unit)
        send.join()
        assertEquals(OTHER, controller.state.value.selectedSessionId)
        assertEquals("other session draft", controller.state.value.draft)
        assertEquals(otherRevision, controller.state.value.draftRevision)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertNull(controller.state.value.lastAcceptedReceipt)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Prompt))
        assertTrue(operations.cancelIds.isEmpty(), "navigation only disposes client work")
        controller.select(SESSION)
        runCurrent()
        assertEquals("keep", controller.state.value.draft, "the failed send must retain its own session draft")
        controller.close()
    }

    @Test
    fun sameHostDisconnectReconnectRetainsRouteAndDraft() = runTest {
        val gate = CompletableDeferred<Unit>()
        var histories = 0
        val operations = FakeOperations().apply {
            historyResult = { _, _ ->
                if (++histories == 1) withContext(NonCancellable) { gate.await(); error("old connection failed") }
                else listOf(message("reconnected"))
            }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("preserve")
        runCurrent()
        controller.bind(null, HOST)
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals("preserve", controller.state.value.draft)
        assertFalse(controller.state.value.canSubmit)
        assertTrue(controller.state.value.history.isEmpty())
        controller.bind(FakeConnection(), HOST.copy(generation = 2))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals(HOST.copy(generation = 2), controller.state.value.host)
        assertEquals(listOf(message("reconnected")), controller.state.value.history)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.History))
        controller.close()
    }

    @Test
    fun referenceChangeAloneAndGenerationChangeAloneInvalidateOldRequests() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var histories = 0
        val operations = FakeOperations().apply {
            historyResult = { _, _ ->
                when (++histories) {
                    1 -> withContext(NonCancellable) { firstGate.await(); error("first obsolete") }
                    2 -> withContext(NonCancellable) { secondGate.await(); error("second obsolete") }
                    else -> listOf(message("current"))
                }
            }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        runCurrent()
        val replacement = FakeConnection()
        controller.bind(replacement, HOST)
        runCurrent()
        controller.bind(replacement, HOST.copy(generation = 2))
        runCurrent()
        firstGate.complete(Unit)
        secondGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(message("current")), controller.state.value.history)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.History))
        controller.close()
    }

    @Test
    fun identityChangeOnSameHostClearsSelectionAndLogoutFencesLateData() = runTest {
        val gate = CompletableDeferred<Unit>()
        var lists = 0
        val operations = FakeOperations().apply {
            listResult = {
                if (++lists == 2) withContext(NonCancellable) { gate.await(); listOf(SUMMARY) }
                else listOf(SUMMARY)
            }
            historyResult = { _, _ -> listOf(message("private")) }
        }
        val target = BackendTenant("https://member.example", 1)
        val first = HOST.copy(identity = MemberIdentity(target, 1))
        val second = first.copy(identity = MemberIdentity(target, 2))
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, first)
        controller.select(SESSION)
        controller.updateDraft("private draft")
        runCurrent()
        controller.bind(operations.connection, second)
        assertNull(controller.state.value.selectedSessionId)
        assertEquals("", controller.state.value.draft)
        assertTrue(controller.state.value.history.isEmpty())
        runCurrent()
        controller.bind(null, null)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ConversationState(), controller.state.value)
        controller.close()
    }

    @Test
    fun hostSwitchFencesDelayedCreateWithoutBackendCancel() = runTest {
        val gate = CompletableDeferred<SessionSummary>()
        val operations = FakeOperations().apply { createResult = { withContext(NonCancellable) { gate.await() } } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.updateDraft("old host")
        val send = assertNotNull(controller.submit())
        runCurrent()
        val next = HOST.copy(hostId = HostId("host-b"))
        controller.bind(operations.connection, next)
        gate.complete(SUMMARY)
        send.join()
        runCurrent()
        assertEquals(next, controller.state.value.host)
        assertNull(controller.state.value.selectedSessionId)
        assertEquals("", controller.state.value.draft)
        assertTrue(operations.promptIds.isEmpty())
        assertTrue(operations.cancelIds.isEmpty())
        controller.close()
    }

    @Test
    fun forwardedEventsAndRecoveryRefreshNeverCancelDelayedSend() = runTest {
        val gate = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply { promptResult = { id, _ -> gate.await(); accepted(id) } }
        val events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 8)
        val recoveries = MutableSharedFlow<HistoryRecovery>(extraBufferCapacity = 8)
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, streams = ConversationStreams(events, recoveries, {}, {}))
        controller.select(SESSION)
        runCurrent()
        controller.updateDraft("delayed")
        val send = assertNotNull(controller.submit())
        runCurrent()
        val count = operations.historyIds.size
        events.emit(event(OTHER, HOST.hostId, 1))
        events.emit(event(SESSION, HostId("wrong-host"), 2))
        recoveries.emit(HistoryRecovery(StreamId(OTHER.value), listOf(message("untrusted"))))
        runCurrent()
        assertEquals(count, operations.historyIds.size)
        events.emit(event(SESSION, HOST.hostId, 3))
        runCurrent()
        assertEquals(count + 1, operations.historyIds.size)
        recoveries.emit(HistoryRecovery(StreamId(SESSION.value), listOf(message("untrusted"))))
        runCurrent()
        assertEquals(count + 2, operations.historyIds.size)
        controller.refresh()
        runCurrent()
        assertTrue(send.isActive)
        assertEquals(ConversationAction.Sending, controller.state.value.action)
        assertNull(controller.submit())
        assertTrue(controller.state.value.history.isEmpty(), "recovery body is not authoritative API history")
        gate.complete(Unit)
        send.join()
        runCurrent()
        assertEquals(listOf(SESSION), operations.promptIds)
        assertEquals("", controller.state.value.draft)
        assertEquals(ConversationAction.RunningUnknown, controller.state.value.action)
        assertNotNull(controller.state.value.lastAcceptedReceipt)
        controller.close()
    }

    @Test
    fun subscriptionsReleaseBeforeSelectionReconnectAndClose() = runTest {
        val operations = FakeOperations()
        val lifecycle = mutableListOf<String>()
        fun streams(label: String) = ConversationStreams(
            MutableSharedFlow<RelayEvent>(), MutableSharedFlow<HistoryRecovery>(),
            { lifecycle += "subscribe:" + label + ":" + it.value },
            { lifecycle += "unsubscribe:" + label + ":" + it.value },
        )
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST, streams = streams("old"))
        controller.select(SESSION)
        runCurrent()
        controller.select(OTHER)
        runCurrent()
        controller.bind(FakeConnection(), HOST.copy(generation = 2), streams = streams("new"))
        runCurrent()
        controller.close()
        runCurrent()
        assertEquals(listOf(
            "subscribe:old:session-a", "unsubscribe:old:session-a",
            "subscribe:old:session-b", "unsubscribe:old:session-b",
            "subscribe:new:session-b", "unsubscribe:new:session-b",
        ), lifecycle)
        assertEquals(ConversationState(), controller.state.value)
    }

    @Test
    fun cancelledSubmitPropagatesCancellationAndClearsBusyState() = runTest {
        val gate = CompletableDeferred<Unit>()
        val operations = FakeOperations().apply { promptResult = { id, _ -> gate.await(); accepted(id) } }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        controller.select(SESSION)
        controller.updateDraft("keep")
        val send = assertNotNull(controller.submit())
        runCurrent()
        send.cancelAndJoin()
        assertTrue(send.isCancelled)
        assertEquals("keep", controller.state.value.draft)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Prompt))
        controller.close()
    }

    @Test
    fun immediateUnconfinedOperationsRegisterBeforeExecution() = runTest {
        val operations = FakeOperations()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val controller = ConversationController(scope, operations)
        controller.bind(operations.connection, HOST, CompletionCapability.LocalIdle)
        controller.updateDraft("first")
        val first = assertNotNull(controller.submit())
        assertTrue(first.isCompleted)
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        controller.updateDraft("second")
        assertTrue(assertNotNull(controller.submit()).isCompleted)
        assertEquals(1, operations.createCalls)
        assertEquals(2, operations.promptIds.size)
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        controller.close()
    }

    @Test
    fun externalArchiveInvalidationRelistsWithoutTouchingAnUnrelatedSelection() = runTest {
        var lists = 0
        val operations = FakeOperations().apply {
            listResult = { if (++lists == 1) listOf(SUMMARY, SessionSummary(OTHER, 2)) else listOf(SessionSummary(OTHER, 2)) }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent()
        assertEquals(listOf(SUMMARY, SessionSummary(OTHER, 2)), controller.state.value.sessions)
        controller.select(SESSION)
        runCurrent()
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        controller.invalidateSessions()
        runCurrent()
        assertEquals(listOf(SessionSummary(OTHER, 2)), controller.state.value.sessions)
        assertEquals(SESSION, controller.state.value.selectedSessionId, "an unrelated selection keeps its route")
        assertEquals(2, lists)
        assertTrue(operations.cancelIds.isEmpty())
        assertEquals(ConversationAction.Idle, controller.state.value.action)
        controller.close()
    }

    @Test
    fun retiredArchiveDeselectsItsSelectionAndFencesStaleHistory() = runTest {
        var lists = 0
        val operations = FakeOperations().apply {
            listResult = { if (++lists == 1) listOf(SUMMARY, SessionSummary(OTHER, 2)) else listOf(SessionSummary(OTHER, 2)) }
            historyResult = { _, _ -> listOf(message("kept")) }
        }
        val controller = ConversationController(backgroundScope, operations)
        controller.bind(operations.connection, HOST)
        runCurrent()
        controller.select(SESSION)
        runCurrent()
        assertEquals(SESSION, controller.state.value.selectedSessionId)
        assertEquals(listOf(message("kept")), controller.state.value.history)
        // The archived chat stops being live: its list entry goes and its stale history is dropped.
        controller.invalidateSessions(retired = SESSION)
        runCurrent()
        assertNull(controller.state.value.selectedSessionId)
        assertTrue(controller.state.value.history.isEmpty())
        assertEquals(ConversationDataPhase.Unbound, controller.state.value.historyPhase)
        assertEquals(listOf(SessionSummary(OTHER, 2)), controller.state.value.sessions)
        assertEquals(2, lists)
        // A retired id that is not the current selection leaves the current route alone.
        controller.select(OTHER)
        runCurrent()
        controller.invalidateSessions(retired = SESSION)
        runCurrent()
        assertEquals(OTHER, controller.state.value.selectedSessionId)
        assertEquals(listOf(message("kept")), controller.state.value.history)
        assertTrue(operations.cancelIds.isEmpty())
        controller.close()
    }
}

private val HOST = ConversationHostScope(HostId("host-a"), generation = 1, identity = null)
private val SESSION = SessionId("session-a")
private val OTHER = SessionId("session-b")
private val SUMMARY = SessionSummary(SESSION, createdAt = 1)

private fun message(text: String) = Message(
    "m-$text", MessageRole.ASSISTANT, listOf(TextContent(text)), ModelMessageSource("test-provider", "test-model"),
)

private fun event(id: SessionId, host: HostId, sequence: Long) = RelayEvent(
    eventId = EventId("event-$sequence"), hostId = host, streamId = StreamId(id.value), sequence = sequence,
    event = "assistant/chunk", payload = JsonPrimitive("not authoritative history"),
)

private class FakeConnection : Connection {
    override val host: HostApi get() = error("Host API is unused by session state")
    override val session: SessionApi get() = error("operations are injected")
}

private class FakeOperations : ConversationOperations {
    override suspend fun archive(connection: Connection, id: SessionId, archived: Boolean) = Unit
    var deleteResult: suspend (SessionId, Long) -> Unit = { _, _ -> }
    override suspend fun delete(connection: Connection, id: SessionId, revision: Long) = deleteResult(id, revision)
    var updateResult: suspend (ConversationUpdate) -> SessionSummary = { SUMMARY }
    override suspend fun update(connection: Connection, update: ConversationUpdate): SessionSummary = updateResult(update)
    val connection: Connection = FakeConnection()
    var createCalls = 0
    var awaitIdleCalls = 0
    var createdOptions: CreateSessionOptions? = null
    var createdAgentOptions: AgentOptions? = null
    val promptIds = mutableListOf<SessionId>()
    val promptMessages = mutableListOf<Message>()
    val historyIds = mutableListOf<SessionId>()
    val cancelIds = mutableListOf<SessionId>()
    var listResult: suspend (Connection) -> List<SessionSummary> = { listOf(SUMMARY) }
    var createResult: suspend () -> SessionSummary = { SUMMARY }
    var historyResult: suspend (Connection, SessionId) -> List<Message> = { _, _ -> emptyList() }
    var promptResult: suspend (SessionId, Message) -> SessionPrompt = { id, _ -> accepted(id) }
    var cancelResult: suspend () -> Unit = {}
    var idleResult: suspend () -> Unit = {}

    override suspend fun list(connection: Connection): List<SessionSummary> = listResult(connection)
    override suspend fun create(connection: Connection, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
        createCalls++
        createdOptions = options
        createdAgentOptions = agentOptions
        return createResult()
    }
    override suspend fun history(connection: Connection, id: SessionId): List<Message> {
        historyIds += id
        return historyResult(connection, id)
    }
    override suspend fun prompt(connection: Connection, id: SessionId, message: Message): SessionPrompt {
        promptIds += id
        promptMessages += message
        return promptResult(id, message)
    }
    override suspend fun cancel(connection: Connection, id: SessionId) {
        cancelIds += id
        cancelResult()
    }
    fun accepted(id: SessionId): SessionPrompt = object : SessionPrompt {
        override val receipt = SessionPromptReceipt(id, "accepted-" + promptIds.size)
        override suspend fun awaitIdle() {
            awaitIdleCalls++
            idleResult()
        }
    }
}
