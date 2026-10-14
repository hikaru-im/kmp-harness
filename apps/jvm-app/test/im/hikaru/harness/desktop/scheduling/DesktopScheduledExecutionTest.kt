package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.host.HostApiKey
import im.hikaru.harness.api.gateway.host.HostApiPlugin
import im.hikaru.harness.api.gateway.host.HostApiService
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.CronHarness
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Uses the same assembly entry as main and the real gateway, disk store and session runtime. */
class DesktopScheduledExecutionTest {
    @Test fun realGatewayHostAndProtocolMismatchRefuseAdmissionAndNeverAcquireLease() = runBlocking {
        val expected = HostId("cron-page-local")
        val descriptions = listOf(
            HostDescription(ProtocolVersion.Current, HostId("foreign-local-host"), "Foreign local host"),
            HostDescription(ProtocolVersion.Current.copy(major = ProtocolVersion.Current.major + 1), expected, "Incompatible host"),
        )
        for (description in descriptions) {
            val f = CronHarness.create(description = description, expectedHostId = expected)
            try {
                val task = f.savePausedTask()
                assertEquals(description, f.connection.host.describe().data)
                assertIs<ScheduledStartResult.Unavailable>(f.execution.start())
                assertTrue(f.execution.owner.state.value.invalidated)
                assertFalse(f.executor.state.value.accepting)
                assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "unverified"))
                assertIs<ScheduledActivationResult.Refused>(f.executor.activate(task.id, task.revision, true))
                assertNoExecution(f)
                assertLeaseAvailable(f)
                // Fixing the underlying description cannot revive this rejected lifetime.
                val hostFiber = f.runtime.fibers.single { it.plugin === HostApiPlugin }
                f.runtime.uninstall(hostFiber)
                assertFalse(f.runtime.context.has(HostApiKey))
                val corrected = HostDescription(ProtocolVersion.Current, expected, "Corrected host")
                val registration = f.runtime.context.provide(HostApiKey,
                    HostApiService { corrected })
                try {
                    assertEquals(corrected, f.connection.host.describe().data)
                    assertIs<ScheduledStartResult.Unavailable>(f.execution.start())
                }
                finally { registration.dispose() }
                assertNoExecution(f)
            } finally { f.close() }
        }
    }

    @Test fun cancellationFromRealGatewayHostServicePropagatesWithoutStartingOwner() = runBlocking {
        val f = CronHarness.create()
        try {
            f.savePausedTask()
            val cancellation = CancellationException("host describe cancelled")
            val hostFiber = f.runtime.fibers.single { it.plugin === HostApiPlugin }
            f.runtime.uninstall(hostFiber)
            assertFalse(f.runtime.context.has(HostApiKey))
            val registration = f.runtime.context.provide(HostApiKey, HostApiService { throw cancellation })
            try {
                val thrown = assertFailsWith<CancellationException> { f.execution.start() }
                assertSame(cancellation, thrown)
                assertNull(f.execution.owner.state.value.binding)
                assertFalse(f.executor.state.value.accepting)
                assertNoExecution(f)
                assertLeaseAvailable(f)
            } finally { registration.dispose() }
        } finally { f.close() }
    }

    @Test fun defaultDisabledConfigurationRefusesRunActivationAndRecoveryWithRealStore() = runBlocking {
        val f = CronHarness.create(config = ScheduledExecutorConfig())
        try {
            val task = f.savePausedTask()
            val before = f.freshSnapshot()
            assertFalse(f.execution.recoveryEnabled)
            assertIs<ScheduledStartResult.Unavailable>(f.execution.start())
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "default-denied"))
            assertIs<ScheduledActivationResult.Refused>(f.executor.activate(task.id, task.revision, true))
            assertIs<ScheduledStartResult.Unavailable>(f.execution.owner.recover(f.execution.binding, 10_000))
            f.clock.set(f.clock.nowEpochMillis() + 3_600_000)
            f.executor.tick()
            assertEquals(before, f.freshSnapshot())
            assertNoExecution(f)
            assertLeaseAvailable(f)
        } finally { f.close() }
    }

    @Test fun openProviderShutdownRetainsRuntimeAndLeaseUntilExactTerminalThenNewOwnerStarts() = runBlocking {
        val f = CronHarness.create()
        var replacement: DesktopScheduledExecution? = null
        try {
            val task = f.savePausedTask()
            assertTrue(f.execution.recoveryEnabled)
            assertEquals(ScheduledStartResult.Started, f.execution.start())
            val claimed = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, "shutdown-open"))
            withTimeout(10_000) { f.adapter.started.await() }
            val accepted = f.awaitRun { it.phase == ScheduledRunPhase.Accepted }
            assertEquals(claimed.run.inputMessageId, accepted.inputMessageId)
            assertNull(accepted.terminal)
            assertFalse(f.adapter.finish.isCompleted)
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.execution.owner.shutdown(100))
            assertTrue(f.execution.owner.state.value.stopped)
            val probe = DesktopScheduledLeaderLease(f.leaderPath)
            try { assertFalse(probe.acquire(f.execution.binding.copy(generation = 2))) }
            finally { probe.release() }
            // The retained Runtime still serves the actual session while its provider is open.
            assertEquals(accepted.sessionRef, f.connection.session.list().single().id)
            assertEquals(1, f.adapter.streams.get())
            assertEquals(ScheduledRunPhase.Accepted, f.freshSnapshot().runs.single().phase)
            f.adapter.finish.complete(Unit)
            val completed = f.awaitRun { it.phase == ScheduledRunPhase.Succeeded }
            f.assertExactPersistedTerminal(completed)
            assertEquals(ScheduledShutdownResult.Drained, f.execution.owner.shutdown(10_000))
            assertLeaseAvailable(f)
            val next = DesktopScheduledExecution(f.runtime.context, f.host, f.connection, f.execution.store,
                f.execution.calendar, f.leaderPath, f.executionScope, CronHarness.verifiedConfig(), f.clock, generation = 2)
            replacement = next
            assertEquals(ScheduledStartResult.Started, next.start())
            assertEquals(f.execution.binding.copy(generation = 2), next.owner.state.value.binding)
            assertTrue(next.owner.state.value.executor.state.value.accepting)
            assertEquals(completed, f.freshSnapshot().runs.single())
            assertEquals(1, f.adapter.streams.get())
            assertEquals(ScheduledShutdownResult.Drained, next.owner.shutdown(10_000))
        } finally {
            try { replacement?.owner?.shutdown(10_000) } finally { f.close() }
        }
    }

    private suspend fun assertNoExecution(f: CronHarness) {
        assertTrue(f.freshSnapshot().runs.isEmpty())
        assertTrue(f.freshSnapshot().cursors.isEmpty())
        assertTrue(f.database.dao.listSessions().isEmpty())
        assertTrue(f.connection.session.list().isEmpty())
        assertEquals(0, f.adapter.streams.get())
        assertFalse(f.adapter.started.isCompleted)
    }

    private suspend fun assertLeaseAvailable(f: CronHarness) {
        val probe = DesktopScheduledLeaderLease(f.leaderPath)
        try { assertTrue(probe.acquire(f.execution.binding.copy(generation = 9)), "Rejected/drained owner must not hold the OS lease") }
        finally { probe.release() }
    }
}
