package im.hikaru.harness.system.prompt

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.service.ServiceKey

/** One assembly request. The Agent is absent for deployment-level inspection. */
public data class SystemPromptContext(
    public val agent: Agent? = null,
)

/**
 * One ordered system-prompt contribution.
 *
 * A non-empty [text] is interpolated unless [interpolate] is false. A [complete]
 * section replaces every other section after assembly.
 */
public class PromptSection(
    public val name: String,
    public val order: Int,
    public val text: suspend (SystemPromptContext) -> String,
    public val interpolate: Boolean = true,
    public val complete: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Prompt section name must not be blank" }
    }
}

/** One dynamic context contribution, materialized as a durable user-role snapshot. */
public class PromptContext(
    public val name: String,
    public val order: Int,
    public val text: suspend (SystemPromptContext) -> String,
    public val interpolate: Boolean = true,
    /** Required context survives `includeRuntimeContext = false`. */
    public val required: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Prompt context name must not be blank" }
    }
}

/** Tool schemas contributed to one assembly, plus the names valid for `toolOrder`. */
public data class ToolProviderResult(
    public val schemas: List<ToolSchema>,
    public val knownNames: List<String> = schemas.map(ToolSchema::name),
)

public data class AssembledSection(
    public val name: String,
    public val text: String,
    public val interpolate: Boolean = true,
)

public data class AssembledContext(
    public val name: String,
    public val text: String,
    public val interpolate: Boolean = true,
)

/** One resolved assembly; sections and contexts are not yet interpolated. */
public data class PromptAssembly(
    public val sections: List<AssembledSection>,
    public val contexts: List<AssembledContext>,
    public val tools: List<ToolSchema>,
    public val variables: Map<String, String?>,
)

/** Registry for the prompt inputs assembled before each model step. */
public interface SystemPromptService {
    public fun section(section: PromptSection): Disposable

    public fun context(context: PromptContext): Disposable

    public fun tools(provider: suspend (SystemPromptContext) -> ToolProviderResult): Disposable

    public fun variable(name: String, provider: suspend (SystemPromptContext) -> String?): Disposable

    public suspend fun assemble(context: SystemPromptContext = SystemPromptContext()): PromptAssembly
}

public object SystemPromptKey : ServiceKey<SystemPromptService>("system-prompt")

public val Context.systemPrompt: SystemPromptService?
    get() = get(SystemPromptKey)

/** Centrally allocated section positions, matching DSH's ordering contract. */
public object PromptSectionOrders {
    public const val HarnessIdentity: Int = -1000
    public const val DeploymentPersonaPrefix: Int = 0
    public const val AgentInstructions: Int = 100
    public const val DeploymentPersonaSuffix: Int = 10200
}

/** Centrally allocated runtime-context positions. */
public object PromptContextOrders {
    public const val WorkingDirectory: Int = 100
}
