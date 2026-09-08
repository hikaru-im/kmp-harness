package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createAssistantMessage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KoogReplayRestorerTest {

    @Test
    fun defaultRequestPathShouldRejectReplayInsteadOfDroppingIt() {
        val model = testKoogModel()
        val error =
            assertFailsWith<LlmException> {
                KoogRequestMapper().map(
                    options =
                        GenerateOptions(
                            provider = "test",
                            model = model.id,
                            messages = listOf(replayedAssistant()),
                        ),
                    model = model,
                )
            }

        assertEquals(KoogLlmErrorCode.REPLAY_MAPPING_NOT_IMPLEMENTED, error.code)
    }

    @Test
    fun customRestorerShouldReceiveSourceAndResolvedTargetIdentity() {
        val model = testKoogModel(id = "target-model", provider = "koog-target")
        val assistant = replayedAssistant()
        var restoredContext: KoogReplayContext? = null
        val mapper =
            KoogRequestMapper(
                messageMapper =
                    DefaultKoogMessageMapper(
                        replayRestorer =
                            KoogReplayRestorer { message, base, context ->
                                assertSame(assistant, message)
                                restoredContext = context
                                base.copy(finishReason = "restored")
                            }
                    )
            )

        val request =
            mapper.map(
                options =
                    GenerateOptions(
                        provider = "target-route",
                        model = model.id,
                        messages = listOf(assistant),
                    ),
                model = model,
            )

        assertEquals(
            KoogReplayContext(
                sourceProvider = "source-route",
                sourceModel = "source-model",
                targetProvider = "target-route",
                targetModel = "target-model",
                targetKoogProvider = "koog-target",
            ),
            restoredContext,
        )
        assertEquals(
            "restored",
            (request.prompt.messages.single() as KoogMessage.Assistant).finishReason,
        )
    }

    @Test
    fun customMessageMapperShouldStillPassThroughReplayProtection() {
        val model = testKoogModel()
        var messageMapperCalls = 0
        var replayCalls = 0
        val mapper =
            KoogRequestMapper(
                messageMapper =
                    ReplayAwareKoogMessageMapper(
                        delegate =
                            KoogMessageMapper { message, _ ->
                                messageMapperCalls++
                                KoogMessage.Assistant(
                                    parts = listOf(MessagePart.Text("custom")),
                                    metaInfo = ResponseMetaInfo.Empty,
                                    id = message.id.value,
                                )
                            },
                        replayRestorer =
                            KoogReplayRestorer { _, base, _ ->
                                replayCalls++
                                base.copy(finishReason = "restored-custom")
                            },
                    )
            )

        val request =
            mapper.map(
                options =
                    GenerateOptions(
                        provider = "test",
                        model = model.id,
                        messages = listOf(replayedAssistant()),
                    ),
                model = model,
            )

        assertEquals(1, messageMapperCalls)
        assertEquals(1, replayCalls)
        assertEquals(
            "restored-custom",
            (request.prompt.messages.single() as KoogMessage.Assistant).finishReason,
        )
    }

    @Test
    fun replayWithoutResolvedRequestTargetShouldUseStableInvalidCode() {
        val error =
            assertFailsWith<LlmException> {
                DefaultKoogMessageMapper().map(
                    replayedAssistant(),
                    KoogMessageMappingContext(),
                )
            }

        assertEquals(KoogLlmErrorCode.INVALID_REPLAY_STATE, error.code)
    }

    @Test
    fun semanticsRegistryShouldTreatAnotherAdaptersReplayAsForeignContent() {
        val registry =
            KoogProviderSemanticsRegistry(
                routes = listOf(testKoogRoute()),
                semantics = listOf(KoogProviderSemantics("test")),
            )
        val base =
            KoogMessage.Assistant(
                parts = listOf(MessagePart.Text("answer")),
                metaInfo = ResponseMetaInfo.Empty,
            )

        assertSame(
            base,
            registry.restore(
                message = replayedAssistant(),
                base = base,
                context =
                    KoogReplayContext(
                        sourceProvider = "foreign-route",
                        sourceModel = "foreign-model",
                        targetProvider = "test",
                        targetModel = "test-model",
                        targetKoogProvider = "test",
                    ),
            ),
        )
    }

    @Test
    fun semanticsRegistryShouldSelectReplayCodecByHistoricalSourceRoute() {
        var sourceCalls = 0
        var targetCalls = 0
        val sourceModel = testKoogModel(id = "source-model", provider = "koog-source")
        val targetModel = testKoogModel(id = "target-model", provider = "koog-target")
        val registry =
            KoogProviderSemanticsRegistry(
                routes =
                    listOf(
                        testKoogRoute(model = sourceModel, provider = "source-route"),
                        testKoogRoute(model = targetModel, provider = "target-route"),
                    ),
                semantics =
                    listOf(
                        KoogProviderSemantics(
                            provider = "source-route",
                            replayRestorer =
                                KoogReplayRestorer { _, base, _ ->
                                    sourceCalls++
                                    base.copy(finishReason = "source-restored")
                                },
                        ),
                        KoogProviderSemantics(
                            provider = "target-route",
                            replayRestorer =
                                KoogReplayRestorer { _, base, _ ->
                                    targetCalls++
                                    base.copy(finishReason = "target-restored")
                                },
                        ),
                    ),
            )
        val base =
            KoogMessage.Assistant(
                parts = listOf(MessagePart.Text("answer")),
                metaInfo = ResponseMetaInfo.Empty,
            )

        val restored =
            registry.restore(
                message = replayedAssistant(),
                base = base,
                context =
                    KoogReplayContext(
                        sourceProvider = "source-route",
                        sourceModel = sourceModel.id,
                        targetProvider = "target-route",
                        targetModel = targetModel.id,
                        targetKoogProvider = targetModel.provider.id,
                    ),
            )

        assertEquals("source-restored", restored.finishReason)
        assertEquals(1, sourceCalls)
        assertEquals(0, targetCalls)
    }

    private fun replayedAssistant() =
        createAssistantMessage(
            content = listOf(TextBlock("answer")),
            provider = "source-route",
            model = "source-model",
            replayState =
                buildJsonObject {
                    put("kind", "provider-private")
                    put("version", 1)
                },
        )
}
