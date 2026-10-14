package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.scheduling.LocalScheduledSessionAccess
import im.hikaru.harness.client.scheduling.ScheduleEngine
import im.hikaru.harness.client.scheduling.ScheduledClock
import im.hikaru.harness.client.scheduling.ScheduledExecutor
import im.hikaru.harness.client.scheduling.ScheduledExecutorConfig
import im.hikaru.harness.client.scheduling.ScheduledExecutorOwner
import im.hikaru.harness.client.scheduling.ScheduledScope
import im.hikaru.harness.client.scheduling.ScheduledStartResult
import im.hikaru.harness.client.scheduling.SystemScheduledClock
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.runtime.Context
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/** One local connection lifetime; the application retains its owner until shutdown proves Drained. */
public class DesktopScheduledExecution(
    context: Context,
    expectedHostId: HostId,
    public val connection: LocalConnection,
    public val store: DesktopScheduledStore,
    public val calendar: DesktopScheduleCalendar,
    leaderPath: Path,
    executionScope: CoroutineScope,
    config: ScheduledExecutorConfig = ScheduledExecutorConfig(),
    clock: ScheduledClock = SystemScheduledClock,
    generation: Long = 1,
) {
    public val recoveryEnabled: Boolean = config.verifiedCronExecution
    public val binding: ScheduledScope = ScheduledScope(expectedHostId, null, generation)
    public val owner: ScheduledExecutorOwner = ScheduledExecutorOwner {
        ScheduledExecutor(
            store = store,
            engine = ScheduleEngine(calendar),
            access = LocalScheduledSessionAccess(
                connection.session, DesktopScheduledSessionEvidence(context, expectedHostId),
            ),
            leader = DesktopScheduledLeaderLease(leaderPath),
            coroutineScope = executionScope,
            clock = clock,
            config = config,
        )
    }

    /** Verify through the exact gateway connection that supplies session delivery and the UI. */
    public suspend fun start(): ScheduledStartResult {
        val description = try {
            val response = connection.host.describe()
            response.data.takeIf { response.isSuccess }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return refuse("无法核验本机 Host；未启动定时执行。")
        }
        if (description == null || description.hostId != binding.hostId ||
            description.protocolVersion.major != ProtocolVersion.Current.major) {
            return refuse("本机 Host 身份或协议不匹配；未启动定时执行。")
        }
        return owner.start(binding)
    }

    private fun refuse(reason: String): ScheduledStartResult.Unavailable {
        owner.invalidate()
        return ScheduledStartResult.Unavailable(reason)
    }
}
