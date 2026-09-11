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
                            (line.startsWith("import im.hikaru.harness.llm.") &&
                                line !in allowedDomainImports) ||
                            line.contains("OpenAiKoogPlugin") ||
                            line.contains("DesktopLlmProviderPlugin")
                    }
                    .toList()
            }

        assertTrue(forbidden.isEmpty(), "Provider imports leaked into Desktop production source: $forbidden")
        assertFalse(Files.exists(sourceRoot.resolve("im/hikaru/harness/desktop/DesktopHostProfile.kt")))
    }

    private companion object {
        private val allowedDomainImports =
            setOf(
                "import im.hikaru.harness.llm.Message",
                "import im.hikaru.harness.llm.TextBlock",
                "import im.hikaru.harness.llm.createUserMessage",
            )
    }

    private fun locateProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        repeat(8) {
            if (Files.exists(current.resolve("project.yaml"))) return current
            current = current.parent ?: return@repeat
        }
        error("Cannot locate project root")
    }
}
