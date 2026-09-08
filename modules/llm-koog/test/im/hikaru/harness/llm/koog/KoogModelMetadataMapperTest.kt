package im.hikaru.harness.llm.koog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KoogModelMetadataMapperTest {

    @Test
    fun modelOutputCapabilityShouldNotBecomeARequestDefault() {
        val model = testKoogModel()
        val mapper = KoogModelMetadataMapper()

        assertNull(
            mapper.resolveModel(
                provider = "test",
                route = KoogModelRoute(model = model),
            ).defaultMaxTokens
        )
        assertEquals(
            1_024L,
            mapper.resolveModel(
                provider = "test",
                route =
                    KoogModelRoute(
                        model = model,
                        defaultMaxTokens = 1_024,
                    ),
            ).defaultMaxTokens,
        )
    }

    @Test
    fun routeDefaultShouldRemainWithinTheModelCapability() {
        val model = testKoogModel()

        assertFailsWith<IllegalArgumentException> {
            KoogModelRoute(
                model = model,
                defaultMaxTokens = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            KoogModelRoute(
                model = model,
                defaultMaxTokens = model.maxOutputTokens!! + 1,
            )
        }
    }
}
