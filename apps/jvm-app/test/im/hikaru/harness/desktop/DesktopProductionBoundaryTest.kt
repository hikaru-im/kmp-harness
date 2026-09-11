package im.hikaru.harness.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProductionBoundaryTest {
    @Test
    fun productionSourceShouldDependOnProfileBundleInsteadOfProviderInternals() {
        val sourceRoot = locateProjectRoot().resolve("apps/jvm-app/src")
        val forbidden =
            Files.walk(sourceRoot).use { paths ->
                paths
                    .filter { path -> path.isRegularFile() }
                    .filter { it.toString().endsWith(".kt") }
                    .flatMap { path ->
                        Files.readAllLines(path).stream().map { line -> path to line.trim() }
                    }
                    .filter { (_, line) ->
                        line.startsWith("import ai.koog.") ||
                            (line.startsWith(LLM_IMPORT_PREFIX) && line !in allowedDomainImports) ||
                            line.contains("OpenAiKoogPlugin") ||
                            line.contains("DesktopLlmProviderPlugin")
                    }
                    .toList()
            }

        assertTrue(forbidden.isEmpty(), "Provider imports leaked into Desktop production source: $forbidden")
        assertFalse(Files.exists(sourceRoot.resolve("im/hikaru/harness/desktop/DesktopHostProfile.kt")))
    }

    @Test
    fun sharedUiShouldPropagateCancellationBeforeMappingFailuresToUiState() {
        val source =
            Files.readAllLines(
                locateProjectRoot().resolve("apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt")
            )

        assertFalse(source.any { it.contains("runCatching") }, "HarnessApp must not map cancellation via runCatching")
        source.forEachIndexed { index, line ->
            if (line.contains("catch (") && line.contains(": Throwable)")) {
                val preceding = source.subList(maxOf(0, index - 4), index)
                assertTrue(
                    preceding.any { it.contains("catch (cancelled: CancellationException)") },
                    "Throwable handler at HarnessApp.kt:${index + 1} is missing cancellation propagation",
                )
            }
        }
    }

    private fun locateProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        repeat(8) {
            if (Files.exists(current.resolve("project.yaml"))) return current
            current = current.parent ?: return@repeat
        }
        error("Cannot locate project root")
    }

    private companion object {
        private const val LLM_IMPORT_PREFIX = "import im.hikaru.harness.llm."
        private val allowedDomainImports =
            setOf(
                "import im.hikaru.harness.llm.CallId",
                "import im.hikaru.harness.llm.Message",
                "import im.hikaru.harness.llm.MessageId",
                "import im.hikaru.harness.llm.MessageRole",
                "import im.hikaru.harness.llm.MessageSource as CoreMessageSource",
                "import im.hikaru.harness.llm.ModelMessageSource",
                "import im.hikaru.harness.llm.PluginMessageSource",
                "import im.hikaru.harness.llm.ReasoningEffortId",
                "import im.hikaru.harness.llm.ToolMessageSource",
                "import im.hikaru.harness.llm.UserMessageSource",
                "import im.hikaru.harness.llm.TextBlock",
                "import im.hikaru.harness.llm.createUserMessage",
            )
    }
}
