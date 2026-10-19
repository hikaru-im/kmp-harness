package im.hikaru.harness.system.prompt

import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.effect.Disposable
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal const val HARNESS_IDENTITY_SECTION: String = "harness:identity"
internal const val PERSONA_PREFIX_SECTION: String = "deployment:persona-prefix"
internal const val PERSONA_SUFFIX_SECTION: String = "deployment:persona-suffix"

private val VARIABLE_NAME = Regex("^[a-z][a-z0-9_]*$")
private val GROUP_AT = Regex("^\\{\\{([^{}]*)\\}\\}")

/**
 * Registry implementation. Registrations are immutable snapshots: an assembly
 * sees the set registered before it started, and disposal removes only the
 * exact contribution that created it.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class DefaultSystemPromptService(
    private val config: SystemPromptConfig,
) : SystemPromptService {
    private data class Variables(
        val name: String,
        val provider: suspend (SystemPromptContext) -> String?,
    )

    private data class Tools(
        val provider: suspend (SystemPromptContext) -> ToolProviderResult,
    )

    private data class State(
        val sections: List<PromptSection> = emptyList(),
        val contexts: List<PromptContext> = emptyList(),
        val toolProviders: List<Tools> = emptyList(),
        val variables: List<Variables> = emptyList(),
    )

    private val state = AtomicReference(State())

    init {
        if (config.includeHarnessIdentity) {
            addSection(
                PromptSection(
                    name = HARNESS_IDENTITY_SECTION,
                    order = PromptSectionOrders.HarnessIdentity,
                    text = { "You are an AI agent powered by KMP Harness." },
                ),
            )
        }
        addSection(
            PromptSection(
                name = PERSONA_PREFIX_SECTION,
                order = PromptSectionOrders.DeploymentPersonaPrefix,
                text = { config.personaPrefix },
            ),
        )
        addSection(
            PromptSection(
                name = PERSONA_SUFFIX_SECTION,
                order = PromptSectionOrders.DeploymentPersonaSuffix,
                text = { config.personaSuffix },
            ),
        )
    }

    private fun addSection(section: PromptSection) {
        while (true) {
            val current = state.load()
            check(current.sections.none { it.name == section.name }) {
                "Prompt section '${section.name}' is already registered"
            }
            if (state.compareAndSet(current, current.copy(sections = current.sections + section))) return
        }
    }

    override fun section(section: PromptSection): Disposable {
        while (true) {
            val current = state.load()
            check(current.sections.none { it.name == section.name }) {
                "Prompt section '${section.name}' is already registered"
            }
            if (state.compareAndSet(current, current.copy(sections = current.sections + section))) break
        }
        return Disposable {
            while (true) {
                val current = state.load()
                if (current.sections.none { it === section }) return@Disposable
                if (
                    state.compareAndSet(
                        current,
                        current.copy(sections = current.sections.filterNot { it === section }),
                    )
                ) {
                    return@Disposable
                }
            }
        }
    }

    override fun context(context: PromptContext): Disposable {
        while (true) {
            val current = state.load()
            check(current.contexts.none { it.name == context.name }) {
                "Prompt context '${context.name}' is already registered"
            }
            if (state.compareAndSet(current, current.copy(contexts = current.contexts + context))) break
        }
        return Disposable {
            while (true) {
                val current = state.load()
                if (current.contexts.none { it === context }) return@Disposable
                if (
                    state.compareAndSet(
                        current,
                        current.copy(contexts = current.contexts.filterNot { it === context }),
                    )
                ) {
                    return@Disposable
                }
            }
        }
    }

    override fun tools(provider: suspend (SystemPromptContext) -> ToolProviderResult): Disposable {
        val registration = Tools(provider)
        while (true) {
            val current = state.load()
            if (
                state.compareAndSet(
                    current,
                    current.copy(toolProviders = current.toolProviders + registration),
                )
            ) {
                break
            }
        }
        return Disposable {
            while (true) {
                val current = state.load()
                if (current.toolProviders.none { it === registration }) return@Disposable
                if (
                    state.compareAndSet(
                        current,
                        current.copy(toolProviders = current.toolProviders.filterNot { it === registration }),
                    )
                ) {
                    return@Disposable
                }
            }
        }
    }

    override fun variable(
        name: String,
        provider: suspend (SystemPromptContext) -> String?,
    ): Disposable {
        require(VARIABLE_NAME.matches(name)) {
            "Invalid prompt variable name '$name' (must match ${VARIABLE_NAME.pattern})"
        }
        val registration = Variables(name, provider)
        while (true) {
            val current = state.load()
            check(current.variables.none { it.name == name }) {
                "Prompt variable '$name' is already registered"
            }
            if (state.compareAndSet(current, current.copy(variables = current.variables + registration))) break
        }
        return Disposable {
            while (true) {
                val current = state.load()
                if (current.variables.none { it === registration }) return@Disposable
                if (
                    state.compareAndSet(
                        current,
                        current.copy(variables = current.variables.filterNot { it === registration }),
                    )
                ) {
                    return@Disposable
                }
            }
        }
    }

    override suspend fun assemble(context: SystemPromptContext): PromptAssembly {
        val current = state.load()
        val variables = linkedMapOf<String, String?>()
        current.variables.forEach { variables[it.name] = it.provider(context) }

        val orderedSections = current.sections.sortedWith(
            compareBy({ it.order }, { it.name }),
        )
        val completeSections = orderedSections.filter { it.complete }
        check(completeSections.size <= 1) {
            "Multiple complete prompt sections are active: ${completeSections.map { it.name }}"
        }
        val activeSections = completeSections.ifEmpty { orderedSections }
        val sections = activeSections.map {
            AssembledSection(
                name = it.name,
                text = it.text(context),
                interpolate = it.interpolate,
            )
        }
        val contexts = current.contexts
            .filter { config.includeRuntimeContext || it.required }
            .sortedBy { it.order }
            .map {
                AssembledContext(
                    name = it.name,
                    text = it.text(context),
                    interpolate = it.interpolate,
                )
            }
        val contributions = current.toolProviders.map { it.provider(context) }
        val collected = contributions.flatMap { it.schemas }
        val knownNames = contributions.flatMap { it.knownNames }.toSet()
        return PromptAssembly(
            sections = sections,
            contexts = contexts,
            tools = orderTools(collected, config.toolOrder, knownNames),
            variables = variables,
        )
    }
}

private fun orderTools(
    schemas: List<ToolSchema>,
    toolOrder: List<String>?,
    knownNames: Set<String>,
): List<ToolSchema> {
    require(schemas.map { it.name }.distinct().size == schemas.size) {
        "Prompt tool providers returned duplicate tool names"
    }
    if (toolOrder == null) return schemas.sortedBy { it.name }
    val byName = schemas.associateBy { it.name }
    val configuredNames = toolOrder.filter { it != TOOL_ORDER_REST }
    val unknown = configuredNames.filter { it !in knownNames }
    require(unknown.isEmpty()) {
        "toolOrder names unknown tools: ${unknown.joinToString()}"
    }
    val configured = configuredNames.toSet()
    val rest = schemas.filter { it.name !in configured }.sortedBy { it.name }
    val ordered = mutableListOf<ToolSchema>()
    for (name in toolOrder) {
        if (name == TOOL_ORDER_REST) {
            ordered += rest
        } else {
            byName[name]?.let(ordered::add)
        }
    }
    return ordered
}

/** Interpolate strict `{{variable}}` references, drop empty sections, join with blank lines. */
public fun renderPrompt(assembly: PromptAssembly): String =
    assembly.sections
        .map { section ->
            if (section.interpolate) {
                interpolate(section.name, section.text, assembly.variables, "section")
            } else {
                section.text
            }
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

/** One rendered dynamic context contribution, attributed to its provider. */
public data class RenderedContextSection(
    public val name: String,
    public val text: String,
)

public fun renderContextSections(assembly: PromptAssembly): List<RenderedContextSection> =
    assembly.contexts
        .map { context ->
            RenderedContextSection(
                name = context.name,
                text = if (context.interpolate) {
                    interpolate(context.name, context.text, assembly.variables, "context")
                } else {
                    context.text
                },
            )
        }
        .filter { it.text.isNotEmpty() }

/** Render the complete runtime-context snapshot, superseding earlier snapshots. */
public fun renderContextSnapshot(assembly: PromptAssembly): String =
    joinContextSections(renderContextSections(assembly))

public fun joinContextSections(sections: List<RenderedContextSection>): String {
    val body = sections.joinToString("\n\n") { it.text }
    if (body.isEmpty()) return ""
    return "Current runtime context. This snapshot supersedes earlier runtime-context snapshots.\n\n$body"
}

private fun interpolate(
    name: String,
    text: String,
    variables: Map<String, String?>,
    kind: String,
): String {
    val result = StringBuilder()
    var last = 0
    var open = text.indexOf("{{")
    while (open >= 0) {
        val group = GROUP_AT.find(text.substring(open))
        if (group == null) {
            if (text.indexOf("}}", open + 2) >= 0) {
                throw IllegalStateException(
                    "Malformed prompt variable reference in $kind \"$name\"",
                )
            }
            result.append(text, last, open + 2)
            last = open + 2
            open = text.indexOf("{{", last)
            continue
        }
        val raw = group.value
        val variable = raw.substring(2, raw.length - 2)
        if (!VARIABLE_NAME.matches(variable)) {
            throw IllegalStateException(
                "Malformed prompt variable reference \"$raw\" in $kind \"$name\"",
            )
        }
        if (!variables.containsKey(variable)) {
            throw IllegalStateException(
                "Unknown prompt variable \"$raw\" in $kind \"$name\"; registered variables: " +
                    variables.keys.joinToString().ifEmpty { "(none)" },
            )
        }
        val value = variables[variable]
            ?: throw IllegalStateException(
                "Prompt variable \"$raw\" has no value for this assembly ($kind \"$name\")",
            )
        result.append(text, last, open).append(value)
        last = open + raw.length
        open = text.indexOf("{{", last)
    }
    result.append(text, last, text.length)
    return result.toString()
}
