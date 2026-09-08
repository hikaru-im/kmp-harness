package im.hikaru.harness.agent

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.service.ServiceKey

object AgentKey : ServiceKey<AgentRegistry>("agent")

val Context.agents: AgentRegistry
    get() = require(AgentKey)
