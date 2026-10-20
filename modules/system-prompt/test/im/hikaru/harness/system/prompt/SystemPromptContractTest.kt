package im.hikaru.harness.system.prompt

import im.hikaru.harness.llm.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SystemPromptContractTest {
    @Test
    fun sectionsRenderInOrderWithNameTieBreakAndDropEmptyText() = blocking {
        val service = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        service.section(section("second", 20) { "B" })
        service.section(section("first", 10) { "A" })
        service.section(section("blank", 30) { "" })
        service.section(section("tie-b", 10) { "B-tie" })

        val rendered = renderPrompt(kotlinx.coroutines.runBlocking { service.assemble() })
        assertEquals("A\n\nB-tie\n\nB", rendered)
    }

    @Test
    fun duplicateNamesAreRejectedAndDisposalRemovesOnlyTheExactContribution() = runBlockingTest {
        val service = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        val registration = service.section(section("one", 0) { "old" })
        assertFailsWith<IllegalStateException> {
            service.section(section("one", 0) { "new" })
        }
        registration.dispose()
        service.section(section("one", 0) { "new" })
        assertEquals("new", renderPrompt(service.assemble()))

        val variable = service.variable("name") { "value" }
        assertFailsWith<IllegalStateException> { service.variable("name") { "other" } }
        variable.dispose()
        service.variable("name") { "other" }
    }

    @Test
    fun interpolationIsStrictAndMissingValuesFailAssembly() = runBlockingTest {
        val service = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        service.variable("model") { "gpt" }
        service.section(section("known", 0) { "model={{model}}" })
        service.section(PromptSection("raw", 1, { "raw={{model}}" }, interpolate = false))
        val rendered = renderPrompt(service.assemble())
        assertTrue(rendered.contains("model=gpt"))
        assertTrue(rendered.contains("raw={{model}}"))

        val unknown = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        unknown.section(section("unknown", 0) { "{{missing}}" })
        assertFailsWith<IllegalStateException> { renderPrompt(unknown.assemble()) }

        val absent = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        absent.variable("present") { null }
        absent.section(section("absent", 0) { "{{present}}" })
        assertFailsWith<IllegalStateException> { renderPrompt(absent.assemble()) }
    }

    @Test
    fun completeSectionReplacesEveryOtherSection() = runBlockingTest {
        val service = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        service.section(section("base", 0) { "base" })
        service.section(
            PromptSection("override", 1000, { "override" }, complete = true),
        )
        assertEquals("override", renderPrompt(service.assemble()))

        service.section(PromptSection("second-override", 1001, { "x" }, complete = true))
        assertFailsWith<IllegalStateException> { service.assemble() }
    }

    @Test
    fun runtimeContextSuppressionKeepsOnlyRequiredContexts() = runBlockingTest {
        val service =
            DefaultSystemPromptService(
                SystemPromptConfig(includeHarnessIdentity = false, includeRuntimeContext = false),
            )
        service.context(PromptContext("cwd", 10, { "cwd=/tmp" }))
        service.context(PromptContext("required", 20, { "required" }, required = true))

        val assembly = service.assemble()
        assertEquals(listOf("required"), assembly.contexts.map { it.name })
        assertTrue(renderContextSnapshot(assembly).contains("required"))
        assertTrue(!renderContextSnapshot(assembly).contains("cwd"))
    }

    @Test
    fun toolOrderPlacesUnlistedToolsAtTheRestMarker() = runBlockingTest {
        val service =
            DefaultSystemPromptService(
                SystemPromptConfig(
                    includeHarnessIdentity = false,
                    toolOrder = listOf("z", TOOL_ORDER_REST, "a"),
                ),
            )
        service.tools {
            ToolProviderResult(listOf(schema("z"), schema("b"), schema("a")))
        }
        // Explicit names after the rest marker follow the unlisted tools.
        assertEquals(listOf("z", "b", "a"), service.assemble().tools.map { it.name })

        val unknown =
            DefaultSystemPromptService(
                SystemPromptConfig(includeHarnessIdentity = false, toolOrder = listOf("missing", TOOL_ORDER_REST)),
            )
        unknown.tools { ToolProviderResult(listOf(schema("a"))) }
        assertFailsWith<IllegalArgumentException> { unknown.assemble() }
    }

    @Test
    fun toolProvidersReturningDuplicatesAreRejected() = runBlockingTest {
        val service = DefaultSystemPromptService(SystemPromptConfig(includeHarnessIdentity = false))
        service.tools { ToolProviderResult(listOf(schema("same"))) }
        service.tools { ToolProviderResult(listOf(schema("same"))) }
        assertFailsWith<IllegalArgumentException> { service.assemble() }
    }

    private fun section(
        name: String,
        order: Int,
        text: suspend (SystemPromptContext) -> String,
    ): PromptSection = PromptSection(name, order, text)

    private fun schema(name: String): ToolSchema = ToolSchema(name, name, JsonObject(emptyMap()))

    private fun runBlockingTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }

    private fun blocking(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
