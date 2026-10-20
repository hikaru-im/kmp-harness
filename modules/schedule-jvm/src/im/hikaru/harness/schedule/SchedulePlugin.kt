package im.hikaru.harness.schedule

import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.api.SessionApiKey
import java.io.File

public const val SCHEDULE_PLUGIN_NAME: String = "schedule"

public object ScheduleKey : ServiceKey<ScheduleService>("schedule")

public val Context.schedule: ScheduleService
    get() = require(ScheduleKey)

/**
 * Host reminder service backed by the desktop `harness-schedule.db`.
 *
 * The store is one independent Room database; delivery uses the Session facade
 * so a quiescent conversation is restored exactly like a user prompt.
 */
public class SchedulePlugin(
    private val databaseFile: File,
    private val config: ScheduleConfig = ScheduleConfig(),
    private val clock: ScheduleClock = SystemScheduleClock,
    private val delivererFactory: (Context) -> ScheduleDeliverer = { SessionScheduleDeliverer(it) },
) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey),
        InjectSpec.required(SessionKey),
        InjectSpec.required(SessionApiKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val store = RoomScheduleStore.open(databaseFile)
        val service = try {
            DefaultScheduleService(context, store, config = config, clock = clock, deliverer = delivererFactory(context))
                .also { it.initialize() }
        } catch (error: Throwable) {
            store.dispose()
            throw error
        }
        scope.add(service)
        scope.add(context.provide(ScheduleKey, service))
        service.start(context.managedScope())
    }
}

public fun schedulePluginDefinition(
    databaseFile: File,
    config: ScheduleConfig = ScheduleConfig(),
): PluginDefinition =
    pluginDefinition(
        name = SCHEDULE_PLUGIN_NAME,
        plugin = SchedulePlugin(databaseFile, config),
    )
