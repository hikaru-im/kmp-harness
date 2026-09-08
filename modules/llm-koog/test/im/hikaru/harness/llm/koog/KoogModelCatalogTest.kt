package im.hikaru.harness.llm.koog

import ai.koog.prompt.llm.LLMCapability
import im.hikaru.harness.llm.LlmModelReasoningInfo
import im.hikaru.harness.llm.LlmReasoningEffortInfo
import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.ReasoningEffortId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoogModelCatalogTest {

    @Test
    fun absentOrEmptyModelsShouldKeepInstalledCatalog() {
        val installed = installedRoute()

        listOf<List<KoogModelProfile>?>(null, emptyList()).forEach { models ->
            val resolved = settings(models = models).resolveRoutes(listOf(installed)).single()

            assertEquals(
                listOf("installed-one", "installed-two"),
                resolved.models.map { route -> route.model.id },
            )
            assertEquals("Installed One", resolved.models.first().name)
            assertNotSame(installed.models.first().model, resolved.models.first().model)
        }
    }

    @Test
    fun nonEmptyModelsShouldReplaceCatalogAndMaterializeRealModels() {
        val installed = installedRoute()
        val resolved =
            settings(
                models =
                    listOf(
                        KoogModelProfile(id = "installed-one", name = "Renamed One"),
                        KoogModelProfile(
                            id = "custom-model",
                            name = "Custom Model",
                            contextWindow = 65_536,
                            maxTokens = 2_048,
                            input = listOf(ModelModality.TEXT),
                        ),
                    )
            ).resolveRoutes(listOf(installed)).single()

        assertEquals(
            listOf("installed-one", "custom-model"),
            resolved.models.map { route -> route.model.id },
        )
        val inherited = resolved.models.first()
        assertEquals("Renamed One", inherited.name)
        assertEquals(32_000, inherited.model.contextLength)
        assertTrue(inherited.model.supports(LLMCapability.Completion))
        assertEquals(installed.models.first().reasoning, inherited.reasoning)

        val custom = resolved.models.last()
        assertEquals(installed.customModelTemplate.provider, custom.model.provider)
        assertEquals(65_536, custom.model.contextLength)
        assertEquals(2_048, custom.model.maxOutputTokens)
        assertEquals(2_048, custom.defaultMaxTokens)
        assertTrue(custom.model.supports(LLMCapability.Completion))
        assertFalse(custom.model.supports(LLMCapability.Thinking))
    }

    @Test
    fun providerFallbackShouldSetCapabilityWithoutCreatingRequestDefault() {
        val resolved =
            settings(
                models = listOf(KoogModelProfile(id = "custom-model")),
                defaultContextWindow = 100_000,
                defaultMaxTokens = 8_000,
            ).resolveRoutes(listOf(installedRoute())).single().models.single()

        assertEquals(100_000, resolved.model.contextLength)
        assertEquals(8_000, resolved.model.maxOutputTokens)
        assertNull(resolved.defaultMaxTokens)
    }

    @Test
    fun emptyModelInputShouldInheritTheNextAvailableDeclaration() {
        val catalogModel =
            settings(
                models =
                    listOf(
                        KoogModelProfile(
                            id = "installed-one",
                            input = emptyList(),
                        )
                    )
            ).resolveRoutes(listOf(installedRoute())).single().models.single()
        val customModel =
            settings(
                models =
                    listOf(
                        KoogModelProfile(
                            id = "custom-model",
                            input = emptyList(),
                        )
                    )
            ).resolveRoutes(listOf(installedRoute())).single().models.single()

        assertEquals(listOf(ModelModality.TEXT), catalogModel.inputModalities)
        assertEquals(listOf(ModelModality.TEXT), customModel.inputModalities)
    }

    @Test
    fun modelOverridesShouldChangeOneInstalledModelAndPreserveTheRest() {
        val resolved =
            settings(
                modelOverrides =
                    mapOf(
                        "installed-one" to
                            KoogModelOverride(
                                name = "Overridden One",
                                contextWindow = 64_000,
                                maxTokens = 1_024,
                                reasoningEfforts = KoogReasoningEfforts.Disabled,
                            )
                    )
            ).resolveRoutes(listOf(installedRoute())).single()

        assertEquals(
            listOf("installed-one", "installed-two"),
            resolved.models.map { route -> route.model.id },
        )
        assertEquals("Overridden One", resolved.models.first().name)
        assertEquals(64_000, resolved.models.first().model.contextLength)
        assertEquals(1_024, resolved.models.first().defaultMaxTokens)
        assertNull(resolved.models.first().reasoning)
        assertFalse(resolved.models.first().model.supports(LLMCapability.Thinking))
        assertEquals("Installed Two", resolved.models.last().name)
    }

    @Test
    fun invalidOverrideCombinationsShouldBeRejected() {
        val installed = installedRoute()

        assertFailsWith<IllegalArgumentException> {
            settings(
                models = listOf(KoogModelProfile("custom-model")),
                modelOverrides = mapOf("installed-one" to KoogModelOverride(name = "Invalid")),
            ).resolveRoutes(listOf(installed))
        }
        assertFailsWith<IllegalArgumentException> {
            settings(
                modelOverrides = mapOf("unknown" to KoogModelOverride(name = "Invalid")),
            ).resolveRoutes(listOf(installed))
        }
        assertFailsWith<IllegalArgumentException> {
            KoogProviderSettings(
                provider = "test",
                models = listOf(KoogModelProfile("duplicate"), KoogModelProfile("duplicate")),
            )
        }
    }

    private fun settings(
        models: List<KoogModelProfile>? = null,
        modelOverrides: Map<String, KoogModelOverride> = emptyMap(),
        defaultContextWindow: Long? = null,
        defaultMaxTokens: Long? = null,
    ): KoogLlmSettings =
        KoogLlmSettings(
            providers =
                mapOf(
                    "test" to
                        KoogProviderSettings(
                            provider = "test",
                            models = models,
                            modelOverrides = modelOverrides,
                            defaultContextWindow = defaultContextWindow,
                            defaultMaxTokens = defaultMaxTokens,
                        )
                )
        )

    private fun installedRoute(): KoogProviderRoute {
        val low = ReasoningEffortId("low")
        val first =
            testKoogModel(id = "installed-one").copy(
                capabilities =
                    listOf(
                        LLMCapability.Completion,
                        LLMCapability.Temperature,
                        LLMCapability.Thinking,
                    )
            )
        return KoogProviderRoute(
            id = "test",
            name = "Installed Provider",
            models =
                listOf(
                    KoogModelRoute(
                        model = first,
                        name = "Installed One",
                        description = "First installed model",
                        inputModalities = listOf(ModelModality.TEXT),
                        reasoning =
                            LlmModelReasoningInfo(
                                efforts = listOf(LlmReasoningEffortInfo(low, "Low")),
                                defaultEffort = low,
                            ),
                    ),
                    KoogModelRoute(
                        model = testKoogModel(id = "installed-two"),
                        name = "Installed Two",
                    ),
                ),
            customModelTemplate = first,
        )
    }
}
