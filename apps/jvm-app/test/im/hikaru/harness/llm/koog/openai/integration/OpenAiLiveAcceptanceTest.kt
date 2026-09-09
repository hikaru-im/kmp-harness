package im.hikaru.harness.llm.koog.openai.integration

import im.hikaru.harness.bundle.desktop.startDesktopProfile
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.credentials.credentialRef
import im.hikaru.harness.llm.BlockAssembler
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmRuntime
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.UsageChunk
import im.hikaru.harness.llm.createToolResultMessage
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val LIVE_API_KEY_ENV = "HARNESS_OPENAI_LIVE_API_KEY"
private const val LIVE_BASE_URL_ENV = "HARNESS_OPENAI_LIVE_BASE_URL"
private const val LIVE_MODEL_ENV = "HARNESS_OPENAI_LIVE_MODEL"
private const val LIVE_TOOLS_ENV = "HARNESS_OPENAI_LIVE_TOOLS"
private const val LIVE_LIFECYCLE_ENV = "HARNESS_OPENAI_LIVE_LIFECYCLE"
private const val LIVE_FAILURE_ENV = "HARNESS_OPENAI_LIVE_FAILURE"
private const val LIVE_FAILURE_MODEL_ENV = "HARNESS_OPENAI_LIVE_FAILURE_MODEL"
private const val LIVE_RESPONSES_ENV = "HARNESS_OPENAI_LIVE_RESPONSES"
private const val LIVE_RESPONSES_MODEL_ENV = "HARNESS_OPENAI_LIVE_RESPONSES_MODEL"
private const val LIVE_FILE_CREDENTIAL_NAME = "HARNESS_OPENAI_FILE_ACCEPTANCE_KEY"
private const val LIVE_TIMEOUT_MILLIS = 90_000L
private const val LIVE_CANCELLATION_TIMEOUT_MILLIS = 15_000L
private const val LIVE_CLOSE_TIMEOUT_MILLIS = 30_000L
private const val ENABLED_ENV_PATTERN = "(?i:true|1|yes)"

/** Opt-in external-network acceptance through the production file-backed Desktop bundle. */
@EnabledIfEnvironmentVariable(named = LIVE_API_KEY_ENV, matches = ".+")
class OpenAiLiveAcceptanceTest {

    @Test
    fun chatCompletionsShouldStreamThroughFileBackedDesktopProfile() = runBlocking {
        assertLiveText(
            api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
            model = liveChatModel(),
        )
    }

    @Test
    @EnabledIfEnvironmentVariable(named = LIVE_TOOLS_ENV, matches = ENABLED_ENV_PATTERN)
    fun chatCompletionsShouldCompleteAFileBackedToolRoundTrip() = runBlocking {
        val model = liveChatModel()
        withLiveProfile(
            api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
            model = model,
        ) { llm, apiKey ->
            val tool = lookupTool()
            val user =
                createUserMessage(
                    listOf(
                        TextBlock(
                            "Call the lookup tool exactly once with the query Kotlin. " +
                                "Do not answer before the tool result is provided."
                        )
                    )
                )
            val firstChunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(
                        GenerateOptions(
                            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
                            model = model,
                            messages = listOf(user),
                            system =
                                "This is a tool-use acceptance test. Use lookup exactly once, " +
                                    "then wait for its result.",
                            tools = listOf(tool),
                            temperature = 0.0,
                            maxTokens = 128,
                        )
                    ).toList()
                }
            assertSecretAbsent(apiKey, firstChunks.toString())

            val first = BlockAssembler()
            firstChunks.forEach(first::push)
            assertEquals(ToolCallsFinishReason, first.finish)
            val call = first.blocks().filterIsInstance<ToolCallBlock>().single()
            assertEquals("lookup", call.name)
            assertEquals(
                "Kotlin",
                Json.parseToJsonElement(call.arguments)
                    .jsonObject
                    .getValue("query")
                    .jsonPrimitive
                    .content,
            )

            val result =
                createToolResultMessage(
                    callId = call.id,
                    content = listOf(TextBlock("Kotlin lookup completed successfully.")),
                    isError = false,
                )
            val secondChunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(
                        GenerateOptions(
                            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
                            model = model,
                            messages = listOf(user, first.message(OpenAiKoogCatalog.OPENAI_PROVIDER_ID, model), result),
                            system =
                                "The lookup result is now available. Do not call another tool. " +
                                    "Reply with exactly: TOOL_OK",
                            tools = listOf(tool),
                            temperature = 0.0,
                            maxTokens = 32,
                        )
                    ).toList()
                }
            assertSecretAbsent(apiKey, secondChunks.toString())
            assertTrue(assertTextTerminal(secondChunks).contains("TOOL_OK"))
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = LIVE_LIFECYCLE_ENV, matches = ENABLED_ENV_PATTERN)
    fun chatCompletionsShouldCancelRecoverAndCloseFileBackedDesktopProfile() = runBlocking {
        val model = liveChatModel()
        withLiveProfile(
            api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
            model = model,
        ) { llm, apiKey ->
            val cancellationOptions =
                liveTextOptions(
                    model = model,
                    expectedText = "CANCEL_STREAM",
                )

            val earlyStopChunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(cancellationOptions).take(1).toList()
                }
            assertEquals(1, earlyStopChunks.size)
            assertFalse(earlyStopChunks.any { chunk -> chunk is FinishChunk })
            assertSecretAbsent(apiKey, earlyStopChunks.toString())

            val cancelledChunks = mutableListOf<StreamChunk>()
            coroutineScope {
                val firstChunk = CompletableDeferred<Unit>()
                val holdCollector = CompletableDeferred<Unit>()
                val collection =
                    launch {
                        llm.stream(cancellationOptions).collect { chunk ->
                            cancelledChunks += chunk
                            firstChunk.complete(Unit)
                            holdCollector.await()
                        }
                    }

                withTimeout(LIVE_TIMEOUT_MILLIS) { firstChunk.await() }
                withTimeout(LIVE_CANCELLATION_TIMEOUT_MILLIS) {
                    collection.cancelAndJoin()
                }
                assertTrue(collection.isCancelled)
            }
            assertEquals(1, cancelledChunks.size)
            assertFalse(cancelledChunks.any { chunk -> chunk is FinishChunk })
            assertSecretAbsent(apiKey, cancelledChunks.toString())

            val recoveryChunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(
                        liveTextOptions(
                            model = model,
                            expectedText = "RECOVERY_OK",
                        )
                    ).toList()
                }
            assertSecretAbsent(apiKey, recoveryChunks.toString())
            assertTrue(assertTextTerminal(recoveryChunks).contains("RECOVERY_OK"))
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = LIVE_FAILURE_ENV, matches = ENABLED_ENV_PATTERN)
    fun chatCompletionsShouldMapLiveServerFailureAndRecoverOnSameExecutor() = runBlocking {
        val healthyModel = liveChatModel()
        val failureModel = requiredEnvironment(LIVE_FAILURE_MODEL_ENV)
        require(failureModel != healthyModel) {
            "$LIVE_FAILURE_MODEL_ENV must differ from $LIVE_MODEL_ENV"
        }
        withLiveProfile(
            api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
            models = listOf(failureModel, healthyModel),
        ) { llm, apiKey ->
            val failureChunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(
                        liveTextOptions(
                            model = failureModel,
                            expectedText = "THIS_REQUEST_MUST_FAIL",
                        )
                    ).toList()
                }
            assertSecretAbsent(apiKey, failureChunks.toString())
            assertEquals(1, failureChunks.size)
            val finish = assertIs<FinishChunk>(failureChunks.single())
            val failure = assertIs<ErrorFinishReason>(finish.reason).failure
            assertEquals("SERVER", failure.code)
            assertEquals(503, failure.status)
            assertTrue(failure.message.isNotBlank())
            assertSecretAbsent(apiKey, failure.message)

            val recoveryChunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(
                        liveTextOptions(
                            model = healthyModel,
                            expectedText = "RECOVERY_OK",
                        )
                    ).toList()
                }
            assertSecretAbsent(apiKey, recoveryChunks.toString())
            assertTrue(assertTextTerminal(recoveryChunks).contains("RECOVERY_OK"))
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = LIVE_RESPONSES_ENV, matches = ENABLED_ENV_PATTERN)
    fun responsesShouldStreamThroughFileBackedDesktopProfile() = runBlocking {
        assertLiveText(
            api = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
            model = liveResponsesModel(),
        )
    }

    private suspend fun assertLiveText(
        api: String,
        model: String,
    ) {
        withLiveProfile(api = api, model = model) { llm, apiKey ->
            val chunks =
                withTimeout(LIVE_TIMEOUT_MILLIS) {
                    llm.stream(liveTextOptions(model = model, expectedText = "OK")).toList()
                }

            assertSecretAbsent(apiKey, chunks.toString())
            assertTrue(assertTextTerminal(chunks).isNotBlank())
        }
    }

    private suspend fun <T> withLiveProfile(
        api: String,
        model: String,
        block: suspend (LlmRuntime, String) -> T,
    ): T =
        withLiveProfile(
            api = api,
            models = listOf(model),
            block = block,
        )

    private suspend fun <T> withLiveProfile(
        api: String,
        models: List<String>,
        block: suspend (LlmRuntime, String) -> T,
    ): T {
        require(models.isNotEmpty() && models.all(String::isNotBlank)) {
            "Live acceptance requires at least one non-blank model"
        }
        require(models.distinct().size == models.size) {
            "Live acceptance models must not contain duplicates"
        }
        val apiKey = requiredEnvironment(LIVE_API_KEY_ENV)
        val directory = Files.createTempDirectory("harness-openai-live")
        val settingsPath = directory.resolve("settings.yaml")
        val credentialsPath = directory.resolve(".credentials.yaml")

        try {
            Files.writeString(
                settingsPath,
                liveSettingsYaml(
                    api = api,
                    models = models,
                    baseUrl = liveBaseUrl(),
                ),
            )
            Files.writeString(
                credentialsPath,
                "$LIVE_FILE_CREDENTIAL_NAME: ${yamlScalar(apiKey)}\n",
            )
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)

            val session = startDesktopProfile(harnessHome = directory, pollMillis = 25)
            try {
                val context = session.host.runtime.context
                val credential =
                    context.require(CredentialsKey)
                        .describe(credentialRef(LIVE_FILE_CREDENTIAL_NAME))
                assertEquals("file", credential.source)
                assertEquals(
                    listOf("openai" to true),
                    context.llm.listConfigurableProviders().map { provider ->
                        provider.provider to provider.declared
                    },
                )
                assertEquals(
                    listOf(OpenAiKoogCatalog.OPENAI_PROVIDER_ID),
                    context.llm.listProviders().map { provider -> provider.id },
                )
                assertEquals(
                    models,
                    context.llm
                        .listModels(OpenAiKoogCatalog.OPENAI_PROVIDER_ID)
                        .map { configured -> configured.id },
                )
                return block(context.llm, apiKey)
            } finally {
                withTimeout(LIVE_CLOSE_TIMEOUT_MILLIS) {
                    session.close()
                }
                assertTrue(session.host.isClosed)
            }
        } catch (error: Throwable) {
            assertSecretAbsent(apiKey, error.stackTraceToString())
            throw error
        } finally {
            try {
                assertSecretAbsentFromNonCredentialFiles(
                    directory = directory,
                    credentialsPath = credentialsPath,
                    apiKey = apiKey,
                )
            } finally {
                directory.toFile().deleteRecursively()
            }
            assertFalse(Files.exists(directory), "Temporary live Harness home was not removed")
        }
    }
}

private fun liveTextOptions(
    model: String,
    expectedText: String,
): GenerateOptions =
    GenerateOptions(
        provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
        model = model,
        messages =
            listOf(
                createUserMessage(
                    listOf(TextBlock("Reply with exactly: $expectedText"))
                )
            ),
        temperature = 0.0,
        maxTokens = 16,
    )

private fun assertTextTerminal(chunks: List<StreamChunk>): String {
    val finishIndex = chunks.lastIndex
    val finish = assertIs<FinishChunk>(chunks[finishIndex])
    assertEquals(StopFinishReason, finish.reason)

    val text =
        chunks
            .filterIsInstance<BlockEndChunk>()
            .mapNotNull { chunk -> (chunk.block as? TextBlock)?.text }
            .joinToString("")
    assertTrue(text.isNotBlank(), "Live response must contain a non-blank text block")

    val usageIndices =
        chunks.mapIndexedNotNull { index, chunk -> index.takeIf { chunk is UsageChunk } }
    assertTrue(usageIndices.size <= 1, "Live response must emit usage at most once")
    usageIndices.singleOrNull()?.let { usageIndex ->
        assertEquals(finishIndex - 1, usageIndex)
        val usage = assertIs<UsageChunk>(chunks[usageIndex]).usage
        assertTrue(usage.inputTokens > 0L)
        assertTrue(usage.outputTokens > 0L)
    }
    return text
}

private fun lookupTool(): ToolSchema =
    ToolSchema(
        name = "lookup",
        description = "Look up a query for the acceptance test",
        parameters =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "The exact query to look up")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("query"))
                }
            },
    )

private fun liveSettingsYaml(
    api: String,
    models: List<String>,
    baseUrl: String,
): String {
    val header =
        """
    llm-koog:
      providers:
        openai:
          displayName: OpenAI Live Acceptance
          api: ${yamlScalar(api)}
          baseUrl: ${yamlScalar(baseUrl)}
          credential:
            name: $LIVE_FILE_CREDENTIAL_NAME
          requestTimeoutMillis: $LIVE_TIMEOUT_MILLIS
          connectTimeoutMillis: 30000
          socketTimeoutMillis: $LIVE_TIMEOUT_MILLIS
          models:
        """.trimIndent()
    val entries =
        models.joinToString("\n") { model ->
            """
            - id: ${yamlScalar(model)}
              name: ${yamlScalar(model)}
              input: [text]
            """.trimIndent().prependIndent("        ")
        }
    return "$header\n$entries\n"
}

private fun liveBaseUrl(): String =
    System.getenv(LIVE_BASE_URL_ENV)
        ?.trim()
        .orEmpty()
        .ifBlank { "https://api.openai.com" }

private fun liveChatModel(): String =
    System.getenv(LIVE_MODEL_ENV)
        ?.trim()
        .orEmpty()
        .ifBlank { "gpt-4o-mini" }

private fun liveResponsesModel(): String =
    System.getenv(LIVE_RESPONSES_MODEL_ENV)
        ?.trim()
        .orEmpty()
        .ifBlank { liveChatModel() }

private fun requiredEnvironment(name: String): String =
    requireNotNull(System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)) {
        "$name must be set for live acceptance"
    }

private fun yamlScalar(value: String): String = JsonPrimitive(value).toString()

private fun setOwnerOnly(path: Path) {
    if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
        val expected =
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
        Files.setPosixFilePermissions(path, expected)
        assertEquals(expected, Files.getPosixFilePermissions(path))
    }
}

private fun assertSecretAbsentFromNonCredentialFiles(
    directory: Path,
    credentialsPath: Path,
    apiKey: String,
) {
    if (!Files.exists(directory)) return

    Files.walk(directory).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { path -> path != credentialsPath }
            .forEach { path ->
                runCatching { Files.readString(path) }
                    .getOrNull()
                    ?.let { text -> assertSecretAbsent(apiKey, text) }
            }
    }
}

private fun assertSecretAbsent(
    apiKey: String,
    value: String,
) {
    assertFalse(
        value.contains(apiKey),
        "Live API key leaked outside the managed credentials file",
    )
}
