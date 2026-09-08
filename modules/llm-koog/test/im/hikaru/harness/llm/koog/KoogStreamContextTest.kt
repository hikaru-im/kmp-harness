package im.hikaru.harness.llm.koog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KoogStreamContextTest {

    @Test
    fun shouldPreserveHarnessAndKoogRouteIdentitiesSeparately() {
        val context =
            KoogStreamContext(
                provider = "harness-route",
                model = "provider-model",
                koogProvider = "koog-client-provider",
            )

        assertEquals("harness-route", context.provider)
        assertEquals("provider-model", context.model)
        assertEquals("koog-client-provider", context.koogProvider)
    }

    @Test
    fun shouldRejectBlankRouteIdentityFields() {
        listOf(
            Triple("", "model", "koog"),
            Triple("route", "", "koog"),
            Triple("route", "model", ""),
        ).forEach { (provider, model, koogProvider) ->
            assertFailsWith<IllegalArgumentException> {
                KoogStreamContext(provider, model, koogProvider)
            }
        }
    }
}
