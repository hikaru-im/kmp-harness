package im.hikaru.harness.mcp.client

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.createSession
import im.hikaru.harness.tools.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Controlled external Java fixture, exercised through the real SDK and real OS pipes. */
class McpStdioIntegrationTest {
    @Test
    fun realProcessUsesExplicitEnvironmentRawToolNamesAndIsReapedOnUninstall() = runTest(timeout = 30.seconds) {
        val directory = Files.createTempDirectory("harness-mcp-stdio-")
        val source = directory.resolve("McpFixture.java")
        val pidFile = directory.resolve("pid")
        Files.writeString(source, fixtureSource)
        val compile = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "javac").toString(), source.toString(),
        ).directory(directory.toFile()).redirectErrorStream(true).start()
        assertTrue(compile.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(0, compile.exitValue(), compile.inputStream.bufferedReader().readText())
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin()); runtime.install(ToolsPlugin()); runtime.install(McpRegistryPlugin())
            val config = McpServerConfig(
                "external", McpTransportConfig.Stdio(
                    command = Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    args = listOf("-cp", directory.toString(), "McpFixture"), cwd = directory.toString(),
                    envFrom = mapOf("MCP_VISIBLE" to "HOST_ALLOWED", "MCP_PID_FILE" to "PID_TARGET"),
                ), startupTimeoutMs = 15_000, toolCallTimeoutMs = 3_000,
                reconnect = McpReconnectConfig(enabled = false), failOnStartupError = true,
            )
            val connector = SdkMcpConnector {
                System.getenv() + mapOf("HOST_ALLOWED" to "explicit-value", "PID_TARGET" to pidFile.toString(), "HOST_SECRET_TOKEN" to "must-not-inherit")
            }
            val fiber = runtime.install(McpClientPlugin(connector), config)
            val tools = runtime.context.require(ToolsKey)
            val schema = tools.schemas().single()
            assertTrue(schema.name.startsWith("mcp__external__echo_"))
            val execution = ToolExecutionContext(runtime.context, runtime.context.createSession())
            val result = withContext(Dispatchers.Default) {
                tools.execute(ToolCallRequest(CallId("stdio"), schema.name, "{}", execution.session.id.value), execution)
            }
            assertEquals(false, result.isError, result.content.toString())
            assertEquals("explicit-value|secret-absent|raw-name-ok", assertIs<TextBlock>(result.content.single()).text)
            val pid = Files.readString(pidFile).toLong()
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
            runtime.uninstall(fiber)
            assertTrue(tools.schemas().isEmpty())
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        } finally {
            runtime.context.dispose()
            Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private val fixtureSource = """
        import java.io.*;
        import java.nio.file.*;
        import java.util.regex.*;
        class McpFixture {
          public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(System.getenv("MCP_PID_FILE")), Long.toString(ProcessHandle.current().pid()));
            System.err.println("a diagnostic must not pollute stdout");
            var reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
            var writer = new PrintWriter(new OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8), true);
            String line;
            while ((line = reader.readLine()) != null) {
              var id = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|[0-9]+)").matcher(line);
              if (!id.find()) continue;
              String result;
              if (line.contains("initialize")) {
                result = "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}";
              } else if (line.contains("tools/list")) {
                result = "{\"tools\":[{\"name\":\"echo.raw\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}";
              } else if (line.contains("tools/call")) {
                var text = System.getenv("MCP_VISIBLE") + "|" + (System.getenv("HOST_SECRET_TOKEN") == null ? "secret-absent" : "LEAK") + "|" + (line.contains("echo.raw") ? "raw-name-ok" : "BAD-NAME");
                result = "{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"isError\":false}";
              } else {
                result = "{}";
              }
              writer.println("{\"jsonrpc\":\"2.0\",\"id\":" + id.group(1) + ",\"result\":" + result + "}");
            }
          }
        }
    """.trimIndent()
}
