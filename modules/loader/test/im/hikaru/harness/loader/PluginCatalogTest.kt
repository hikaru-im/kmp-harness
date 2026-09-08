package im.hikaru.harness.loader

import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginCatalogTest {
    private val emptyPlugin =
        object : SimplePlugin {
            override suspend fun apply(
                context: Context,
                scope: EffectScope,
            ) = Unit
        }

    @Test
    fun catalogShouldRegisterAndDisposeAllDefinitions() = runTest {
        val registry = Registry()
        val catalog =
            PluginCatalog(
                listOf(
                    pluginDefinition("first", emptyPlugin),
                    pluginDefinition("second", emptyPlugin),
                )
            )

        val registration = catalog.registerInto(registry)

        assertEquals(setOf("first", "second"), registry.names)

        registration.dispose()

        assertTrue(registry.names.isEmpty())
    }

    @Test
    fun duplicateDefinitionNamesShouldFailBeforeRegistration() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                PluginCatalog(
                    listOf(
                        pluginDefinition("same", emptyPlugin),
                        pluginDefinition("same", emptyPlugin),
                    )
                )
            }

        assertTrue(error.message.orEmpty().contains("duplicate"))
    }

    @Test
    fun failedCatalogRegistrationShouldRemoveEarlierDefinitions() = runTest {
        val registry = Registry()
        registry.register("reserved", emptyPlugin)
        val catalog =
            PluginCatalog(
                listOf(
                    pluginDefinition("temporary", emptyPlugin),
                    pluginDefinition("reserved", emptyPlugin),
                )
            )

        assertFailsWith<IllegalStateException> {
            catalog.registerInto(registry)
        }

        assertFalse("temporary" in registry)
        assertTrue("reserved" in registry)
    }
}
