package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey

const val SESSION_API_PLUGIN_NAME: String = "session-api"

object SessionApiKey : ServiceKey<SessionApi>("session-api")

val Context.sessionApi: SessionApi
    get() = require(SessionApiKey)

class SessionApiPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(InjectSpec.required(AgentKey))

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        scope.add(context.provide(SessionApiKey, DefaultSessionApi(context)))
    }
}
