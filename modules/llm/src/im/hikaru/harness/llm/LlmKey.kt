package im.hikaru.harness.llm

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.service.ServiceKey

/** Runtime 中 provider-neutral LLM 能力的 ServiceKey。 */
object LlmKey : ServiceKey<LlmRuntime>("llm")

/** 取得当前 Context 可见的 LlmRuntime。 */
val Context.llm: LlmRuntime
    get() = require(LlmKey)
