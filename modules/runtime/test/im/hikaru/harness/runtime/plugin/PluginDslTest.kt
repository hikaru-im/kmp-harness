package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginDslTest {

    private interface RequiredService
    private interface OptionalService

    private object RequiredKey :
        ServiceKey<RequiredService>("required")

    private object OptionalKey :
        ServiceKey<OptionalService>("optional")

    private data class Config(
        val value: String,
    )

    @Test
    fun pluginDslShouldBuildInjectAndApplyContract() = runTest {
        val calls =
            mutableListOf<String>()

        val definition =
            plugin<Config> {
                inject(RequiredKey)
                optional(OptionalKey)

                apply { context, config, scope ->
                    assertTrue(context.has(RequiredKey))
                    assertFalse(context.has(OptionalKey))

                    calls +=
                        config.value

                    scope.add(
                        Disposable {
                            calls +=
                                "disposed"
                        }
                    )
                }
            }

        assertEquals(
            setOf(
                InjectSpec.required(RequiredKey),
                InjectSpec.optional(OptionalKey),
            ),
            definition.inject,
        )

        val runtime =
            Runtime()

        runtime.provide(
            RequiredKey,
            object : RequiredService {},
        )

        val fiber =
            runtime.plugin(
                plugin = definition,
                config = Config("started"),
            )

        assertEquals(FiberState.Active, fiber.state)
        assertEquals(listOf("started"), calls)

        runtime.uninstall(fiber)
        assertEquals(listOf("started", "disposed"), calls)
    }

    @Test
    fun simplePluginDslShouldHideUnitConfig() = runTest {
        var starts =
            0

        val definition =
            simplePlugin {
                apply { _, _ ->
                    starts++
                }
            }

        val runtime =
            Runtime()

        runtime.plugin(definition)

        assertEquals(1, starts)
    }

    @Test
    fun pluginDslShouldGuardDuplicateInjectAndMissingApply() {
        assertFailsWith<IllegalStateException> {
            plugin<Config> {
                inject(RequiredKey)
                optional(RequiredKey)
                apply { _, _, _ -> }
            }
        }

        assertFailsWith<IllegalStateException> {
            plugin<Config> {
                inject(RequiredKey)
            }
        }
    }
}
