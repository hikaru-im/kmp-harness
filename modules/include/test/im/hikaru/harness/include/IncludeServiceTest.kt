package im.hikaru.harness.include

import im.hikaru.harness.loader.ConfigAdapter
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.Loader
import im.hikaru.harness.loader.LoaderFactory
import im.hikaru.harness.loader.Registry
import im.hikaru.harness.loader.provideLoaderFactory
import im.hikaru.harness.loader.typedConfig
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IncludeServiceTest {
    private data class TestConfig(
        val value: String,
    )

    @Test
    fun refreshShouldReuseUnchangedFiberAndReplaceChangedConfig() = runTest {
        val runtime = Runtime()
        val registry = Registry()
        val starts = mutableListOf<String>()
        val stops = mutableListOf<String>()
        registry.register(
            name = "sample",
            plugin = recordingPlugin(starts, stops),
            config = jsonConfig(),
        )
        val loader = Loader(runtime, registry)
        var snapshot = snapshot("first", revision = "1")
        val service =
            IncludeService(
                loader = loader,
                source = ConfigSource { snapshot },
            )

        val first = service.refresh()
        val firstFiber = loader.fiber("worker")
        val unchanged = service.refresh()

        assertTrue(first.changed)
        assertFalse(unchanged.changed)
        assertSame(firstFiber, loader.fiber("worker"))

        snapshot = snapshot("second", revision = "2")
        service.refresh()

        assertEquals(listOf("first", "second"), starts)
        assertEquals(listOf("first"), stops)
        assertEquals("second", loader.entries.single().configValue())

        service.dispose()

        assertTrue(runtime.fibers.isEmpty())
        assertEquals(listOf("first", "second"), stops)
        assertEquals(null, service.snapshot())
    }

    @Test
    fun loadOrDecodeFailureShouldNotMutateRunningSnapshot() = runTest {
        val runtime = Runtime()
        val registry = Registry()
        registry.register(
            name = "sample",
            plugin = recordingPlugin(mutableListOf(), mutableListOf()),
            config = jsonConfig(),
        )
        val resource = InMemoryConfigResource(json("first"))
        val loader = Loader(runtime, registry)
        val service =
            IncludeService(
                loader = loader,
                source = DecodingConfigSource(resource, JsonEntryCodec()),
            )

        service.refresh()
        val fiber = loader.fiber("worker")
        resource.write("not-json", expectedRevision = "1")

        assertFailsWith<IllegalArgumentException> {
            service.refresh()
        }
        assertSame(fiber, loader.fiber("worker"))
        assertEquals("first", loader.entries.single().configValue())
    }

    @Test
    fun refreshShouldSerializeLoadThroughReconcile() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        val source =
            ConfigSource {
                calls += 1
                if (calls == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                ConfigSnapshot(
                    entries =
                        listOf(
                            Entry(
                                id = "entry",
                                name = "not-installed",
                                config = calls,
                                disabled = true,
                            )
                        ),
                    revision = calls.toString(),
                )
            }
        val loader = Loader(Runtime(), Registry())
        val service = IncludeService(loader, source)

        val first = async { service.refresh() }
        firstStarted.await()
        val second = async { service.refresh() }
        yield()

        assertEquals(1, calls)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(2, calls)
        assertEquals(2, loader.entries.single().config)
    }

    @Test
    fun mutableSourceShouldWriteRevisionAndRollbackRuntimeOnConflict() = runTest {
        val runtime = Runtime()
        val registry = Registry()
        registry.register(
            name = "sample",
            plugin = recordingPlugin(mutableListOf(), mutableListOf()),
            config = jsonConfig(),
        )
        val resource = InMemoryConfigResource(json("first"))
        val source = MutableDecodingConfigSource(resource, JsonEntryCodec())
        val loader = Loader(runtime, registry)
        val service = IncludeService(loader, source)

        service.refresh()
        val saved = service.save(listOf(entry("second")))

        assertEquals("2", saved.revision)
        assertEquals("second", loader.entries.single().configValue())
        assertEquals("second", JsonEntryCodec().decode(resource.read().content).single().configValue())

        resource.write(json("external"), expectedRevision = "2")

        assertFailsWith<IllegalStateException> {
            service.save(listOf(entry("third")))
        }
        assertEquals("second", loader.entries.single().configValue())
        assertEquals("external", JsonEntryCodec().decode(resource.read().content).single().configValue())
    }

    @Test
    fun pluginShouldOwnDedicatedChildLoaderAndIsolatedService() = runTest {
        val runtime = Runtime()
        val registry = Registry()
        var serviceSeenByChild: IncludeService? = null
        val values = mutableListOf<String>()

        registry.register(
            name = "worker",
            plugin =
                object : Plugin<TestConfig> {
                    override suspend fun apply(
                        context: Context,
                        config: TestConfig,
                        scope: EffectScope,
                    ) {
                        serviceSeenByChild = context.require(IncludeKey)
                        values += config.value
                    }
                },
            config = jsonConfig(),
        )
        registry.register(
            name = "include",
            plugin = IncludePlugin,
            config = typedConfig(),
        )
        runtime.provideLoaderFactory(registry)
        val rootLoader = LoaderFactory(runtime, registry).create()
        val childSource =
            ConfigSource {
                ConfigSnapshot(
                    entries = listOf(entry("nested", name = "worker")),
                    revision = "1",
                )
            }

        rootLoader.reconcile(
            listOf(
                Entry(
                    id = "config",
                    name = "include",
                    config = IncludeConfig(childSource),
                )
            )
        )

        assertEquals(listOf("nested"), values)
        assertEquals(listOf("config"), rootLoader.entries.map { it.id })
        assertEquals(2, runtime.fibers.size)
        assertFalse(runtime.context.has(IncludeKey))
        assertEquals("nested", serviceSeenByChild?.snapshot()?.entries?.single()?.configValue())

        rootLoader.reconcile(emptyList())

        assertTrue(runtime.fibers.isEmpty())
        assertEquals(null, serviceSeenByChild?.snapshot())
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
                starts += config.value
                scope.add(
                    Disposable {
                        stops += config.value
                    }
                )
            }
        }

    private fun jsonConfig(): ConfigAdapter<TestConfig> =
        ConfigAdapter { raw ->
            val objectValue = raw as? JsonObject
                ?: error("Expected JSON object")
            TestConfig(
                objectValue["value"]?.jsonPrimitive?.content
                    ?: error("Expected value"),
            )
        }

    private fun snapshot(
        value: String,
        revision: String,
    ): ConfigSnapshot =
        ConfigSnapshot(
            entries = listOf(entry(value)),
            revision = revision,
        )

    private fun entry(
        value: String,
        name: String = "sample",
    ): Entry =
        Entry(
            id = "worker",
            name = name,
            config = JsonObject(mapOf("value" to JsonPrimitive(value))),
        )

    private fun json(value: String): String =
        """
        [
          {
            "id": "worker",
            "name": "sample",
            "config": { "value": "$value" }
          }
        ]
        """.trimIndent()
}

private fun Entry.configValue(): String =
    ((config as JsonObject)["value"] as JsonPrimitive).content
