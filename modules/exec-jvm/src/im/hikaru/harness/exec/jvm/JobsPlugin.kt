package im.hikaru.harness.exec.jvm

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.SessionKey

public const val JOBS_PLUGIN_NAME: String = "jobs"

public val JobsPluginDefinition: PluginDefinition =
    pluginDefinition(JOBS_PLUGIN_NAME, JobsPlugin())

/**
 * Process-local background-job registry. Producers such as `tool-bash` and
 * `tool-terminal` start work here; `tool-jobs` exposes collection and control.
 */
public class JobsPlugin(private val config: JobsConfig = JobsConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(InjectSpec.required(SessionKey))

    override suspend fun apply(context: Context, scope: EffectScope) {
        val jobs = LocalJobs(context, config)
        scope.add(jobs)
        scope.add(context.on(SessionEvents.Disposed) { notice -> jobs.ownerDisposed(notice.session.id) })
        scope.add(context.provide(JobsKey, jobs))
    }
}
