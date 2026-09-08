package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningEffortId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class KoogProviderSettingsTest {

    @Test
    fun persistedModelSettingsShouldRoundTripThroughTheManualJsonBoundary() {
        val document =
            buildJsonObject {
                put(
                    "providers",
                    buildJsonObject {
                        put(
                            "test",
                            buildJsonObject {
                                put(
                                    "models",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", "custom-model")
                                                put("contextWindow", 65_536)
                                                put("maxTokens", 2_048)
                                                put(
                                                    "reasoningEfforts",
                                                    buildJsonObject {
                                                        put("low", "low")
                                                        put("high", "high")
                                                    },
                                                )
                                                put("defaultReasoningEffort", "low")
                                            }
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }

        val settings = KoogLlmSettings.fromJson(document)
        val model = settings.providers.getValue("test").models!!.single()

        assertEquals(65_536, model.contextWindow)
        assertEquals(2_048, model.maxTokens)
        assertEquals(
            KoogReasoningEfforts.Supported(
                efforts = listOf(ReasoningEffortId("low"), ReasoningEffortId("high")),
                defaultEffort = ReasoningEffortId("low"),
            ),
            model.reasoningEfforts,
        )
        assertEquals(settings, KoogLlmSettings.fromJson(settings.toJson()))
    }

    @Test
    fun unsupportedReasoningWireAliasesShouldFailInsteadOfBeingIgnored() {
        val document =
            buildJsonObject {
                put(
                    "test",
                    buildJsonObject {
                        put(
                            "models",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", "custom-model")
                                        put(
                                            "reasoningEfforts",
                                            buildJsonObject { put("high", "ultra") },
                                        )
                                    }
                                )
                            },
                        )
                    },
                )
            }

        assertFailsWith<IllegalArgumentException> {
            KoogLlmSettings.fromJson(document)
        }
    }

    @Test
    fun settingsShouldSerializeCredentialReferenceWithoutSecret() {
        val settings =
            KoogProviderSettings(
                provider = "deepseek",
                displayName = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                credential = KoogCredentialRef("deepseek-api-key"),
            )

        val encoded = Json.encodeToString(settings)

        assertEquals(settings, Json.decodeFromString(encoded))
        assertFalse("actual-secret" in encoded)
    }

    @Test
    fun settingsShouldRejectBlankIdentifiers() {
        assertFailsWith<IllegalArgumentException> {
            KoogCredentialRef(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            KoogProviderSettings(provider = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            KoogProviderSettings(provider = "test", baseUrl = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            KoogProviderSettings(provider = "test", socketTimeoutMillis = 0)
        }
    }

    @Test
    fun settingsShouldRoundTripTransportTimeoutsWithoutSecrets() {
        val settings =
            KoogProviderSettings(
                provider = "openai",
                api = "openai-chat-completions",
                requestTimeoutMillis = 10_000,
                connectTimeoutMillis = 2_000,
                socketTimeoutMillis = 750,
                credential = KoogCredentialRef("OPENAI_API_KEY"),
            )

        val encoded = Json.encodeToString(settings)

        assertEquals(settings, Json.decodeFromString(encoded))
        assertFalse("actual-secret" in encoded)
    }

    @Test
    fun requiredCredentialShouldPreserveValueOrFailWithStableCode() = runTest {
        val reference = KoogCredentialRef("provider-api-key")
        var resolverCalls = 0
        val resolver =
            KoogCredentialResolver { resolvedReference ->
                resolverCalls++
                assertEquals(reference, resolvedReference)
                " exact-secret-value "
            }

        assertEquals(
            " exact-secret-value ",
            resolver.resolveRequired("test", reference),
        )
        assertEquals(1, resolverCalls)

        listOf<String?>(null, "   ").forEach { unavailable ->
            val error =
                assertFailsWith<LlmException> {
                    KoogCredentialResolver { unavailable }
                        .resolveRequired("test", reference)
                }
            assertEquals(LlmErrorCode.INVALID_CREDENTIAL, error.code)
        }
    }
}
