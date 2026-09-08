package im.hikaru.harness.loader

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Fiber
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.SimplePlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoaderTest {

    private data class TestConfig(
        val value: String,
    )

    @Test
    fun registryShouldGuardDuplicatesAndRegistrationOwnership() = runTest {
        val registry =
            Registry()

        val plugin =
            emptyPlugin()

        val first =
            registry.register(
                name = "sample",
                plugin = plugin,
            )

        assertEquals(1, registry.size)
        assertTrue("sample" in registry)

        assertFailsWith<IllegalStateException> {
            registry.register(
                name = "sample",
                plugin = plugin,
            )
        }

        first.dispose()

        val second =
            registry.register(
                name = "sample",
                plugin = plugin,
            )

        first.dispose()
        assertTrue("sample" in registry)

        second.dispose()
        assertFalse("sample" in registry)
    }

    @Test
    fun reconcileShouldPreserveUnchangedFiberAndRestartChangedConfig() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        val starts =
            mutableListOf<String>()

        val stops =
            mutableListOf<String>()

        registry.register(
            name = "sample",
            plugin = recordingPlugin(starts, stops),
            config = typedConfig(),
        )

        val loader =
            Loader(runtime, registry)

        val firstEntry =
            Entry(
                id = "main",
                name = "sample",
                config = TestConfig("first"),
            )

        loader.reconcile(
            listOf(firstEntry)
        )

        val firstFiber =
            loader.fiber("main")

        assertEquals(FiberState.Active, firstFiber?.state)
        assertEquals(listOf("first"), starts)

        loader.reconcile(
            listOf(firstEntry.copy())
        )

        assertSame(firstFiber, loader.fiber("main"))
        assertEquals(listOf("first"), starts)

        loader.reconcile(
            listOf(
                firstEntry.copy(
                    config = TestConfig("second")
                )
            )
        )

        val secondFiber =
            loader.fiber("main")

        assertNotEquals(firstFiber?.id, secondFiber?.id)
        assertEquals(listOf("first", "second"), starts)
        assertEquals(listOf("first"), stops)
        assertEquals(1, runtime.fibers.size)

        loader.reconcile(emptyList())

        assertTrue(loader.entries.isEmpty())
        assertTrue(runtime.fibers.isEmpty())
        assertEquals(listOf("first", "second"), stops)
    }

    @Test
    fun disabledEntryShouldNotRequireOrInstallPlugin() = runTest {
        val runtime =
            Runtime()

        val loader =
            Loader(
                runtime = runtime,
                registry = Registry(),
            )

        val disabled =
            Entry(
                id = "optional",
                name = "not-registered",
                disabled = true,
            )

        loader.reconcile(
            listOf(disabled)
        )

        assertEquals(listOf(disabled), loader.entries)
        assertNull(loader.fiber("optional"))
        assertTrue(runtime.fibers.isEmpty())

        assertFailsWith<IllegalStateException> {
            loader.reconcile(
                listOf(
                    disabled.copy(disabled = false)
                )
            )
        }

        assertEquals(listOf(disabled), loader.entries)
        assertTrue(runtime.fibers.isEmpty())
    }

    @Test
    fun failedBatchShouldRestoreCompletePreviousSnapshot() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        val starts =
            mutableListOf<String>()

        val stops =
            mutableListOf<String>()

        val plugin =
            object : Plugin<TestConfig> {

                override suspend fun apply(
                    context: Context,
                    config: TestConfig,
                    scope: EffectScope,
                ) {
                    starts +=
                        config.value

                    if (config.value == "bad") {
                        error("bad config")
                    }

                    scope.add(
                        Disposable {
                            stops +=
                                config.value
                        }
                    )
                }
            }

        registry.register(
            name = "sample",
            plugin = plugin,
            config = typedConfig(),
        )

        val loader =
            Loader(runtime, registry)

        val previous =
            listOf(
                Entry("first", "sample", TestConfig("one")),
                Entry("second", "sample", TestConfig("two")),
            )

        loader.reconcile(previous)

        val error =
            assertFailsWith<IllegalStateException> {
                loader.reconcile(
                    listOf(
                        Entry("second", "sample", TestConfig("bad"))
                    )
                )
            }

        assertEquals("bad config", error.message)
        assertEquals(previous, loader.entries)
        assertEquals(2, runtime.fibers.size)
        assertEquals(FiberState.Active, loader.fiber("first")?.state)
        assertEquals(FiberState.Active, loader.fiber("second")?.state)
        assertEquals(
            TestConfig("one"),
            configuredFiber(loader, "first").config,
        )
        assertEquals(
            TestConfig("two"),
            configuredFiber(loader, "second").config,
        )
        assertEquals(
            listOf("one", "two", "bad", "one", "two"),
            starts,
        )
        assertEquals(listOf("two", "one"), stops)
    }

    @Test
    fun configValidationShouldFinishBeforeRuntimeMutation() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        registry.register(
            name = "sample",
            plugin = recordingPlugin(
                starts = mutableListOf(),
                stops = mutableListOf(),
            ),
            config = typedConfig(),
        )

        val loader =
            Loader(runtime, registry)

        val original =
            Entry("main", "sample", TestConfig("valid"))

        loader.reconcile(listOf(original))

        val originalFiber =
            loader.fiber("main")

        assertFailsWith<IllegalStateException> {
            loader.reconcile(
                listOf(
                    Entry("other", "sample", "wrong-type")
                )
            )
        }

        assertEquals(listOf(original), loader.entries)
        assertSame(originalFiber, loader.fiber("main"))
        assertEquals(1, runtime.fibers.size)
    }

    @Test
    fun replacingRegistryDefinitionShouldRestartSameEntry() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        var firstStarts =
            0

        var secondStarts =
            0

        val firstRegistration =
            registry.register(
                name = "sample",
                plugin = emptyPlugin {
                    firstStarts++
                },
            )

        val loader =
            Loader(runtime, registry)

        val entry =
            Entry("main", "sample")

        loader.reconcile(listOf(entry))

        val firstFiber =
            loader.fiber("main")

        firstRegistration.dispose()

        registry.register(
            name = "sample",
            plugin = emptyPlugin {
                secondStarts++
            },
        )

        loader.reconcile(listOf(entry))

        assertEquals(1, firstStarts)
        assertEquals(1, secondStarts)
        assertNotEquals(firstFiber?.id, loader.fiber("main")?.id)
        assertEquals(1, runtime.fibers.size)
    }

    @Test
    fun duplicateEntryIdsShouldBeRejectedBeforeMutation() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        registry.register(
            name = "sample",
            plugin = emptyPlugin(),
        )

        val loader =
            Loader(runtime, registry)

        assertFailsWith<IllegalStateException> {
            loader.reconcile(
                listOf(
                    Entry("same", "sample"),
                    Entry("same", "sample"),
                )
            )
        }

        assertTrue(loader.entries.isEmpty())
        assertTrue(runtime.fibers.isEmpty())
    }

    @Test
    fun concurrentReconcileCallsShouldSerialize() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        val firstStarted =
            CompletableDeferred<Unit>()

        val releaseFirst =
            CompletableDeferred<Unit>()

        registry.register(
            name = "sample",
            plugin = object : Plugin<TestConfig> {

                override suspend fun apply(
                    context: Context,
                    config: TestConfig,
                    scope: EffectScope,
                ) {
                    if (config.value == "first") {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                }
            },
            config = typedConfig(),
        )

        val loader =
            Loader(runtime, registry)

        val first =
            async {
                loader.reconcile(
                    listOf(
                        Entry("main", "sample", TestConfig("first"))
                    )
                )
            }

        firstStarted.await()

        val second =
            async {
                loader.reconcile(
                    listOf(
                        Entry("main", "sample", TestConfig("second"))
                    )
                )
            }

        yield()
        assertFalse(second.isCompleted)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(
            TestConfig("second"),
            configuredFiber(loader, "main").config,
        )
        assertEquals(1, runtime.fibers.size)
    }

    @Test
    fun disposeShouldBeIdempotentAndRejectFurtherReconcile() = runTest {
        val runtime =
            Runtime()

        val registry =
            Registry()

        registry.register(
            name = "sample",
            plugin = emptyPlugin(),
        )

        val loader =
            Loader(runtime, registry)

        loader.reconcile(
            listOf(
                Entry("main", "sample")
            )
        )

        loader.dispose()
        loader.dispose()

        assertTrue(loader.entries.isEmpty())
        assertTrue(runtime.fibers.isEmpty())

        assertFailsWith<IllegalStateException> {
            loader.reconcile(emptyList())
        }
    }

    private fun recordingPlugin(
        starts: MutableList<String>,
        stops: MutableList<String>,
    ): Plugin<TestConfig> =
        object : Plugin<TestConfig> {

            override suspend fun apply(
                context: Context,
                config: TestConfig,
                scope: EffectScope,
            ) {
                starts +=
                    config.value

                scope.add(
                    Disposable {
                        stops +=
                            config.value
                    }
                )
            }
        }

    private fun emptyPlugin(
        onApply: () -> Unit = {},
    ): SimplePlugin =
        object : SimplePlugin {

            override suspend fun apply(
                context: Context,
                scope: EffectScope,
            ) {
                onApply()
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun configuredFiber(
        loader: Loader,
        id: String,
    ): Fiber<TestConfig> =
        loader.fiber(id) as Fiber<TestConfig>
}
