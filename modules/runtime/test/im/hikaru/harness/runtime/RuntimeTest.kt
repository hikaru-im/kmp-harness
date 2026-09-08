@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
)

package im.hikaru.harness.runtime


import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.*

/**
 * Runtime 整体行为测试。
 *
 * 这里不再只测试单独一个 Fiber，
 * 而是验证多个 Plugin 之间通过 Service dependency
 * 形成的动态关系。
 */
class RuntimeTest {

    private interface TestService
    private interface TestLlmService

    private object LlmKey :
        ServiceKey<TestLlmService>("llm")

    private data class TestPluginConfig(
        val name: String,
        val value: Int,
    )

    private data class TestRuntimeEvent(
        val value: String,
    )

    private object TestRuntimeEventKey :
        EventKey<TestRuntimeEvent>(
            "runtime.test",
        )

    /**
     * AgentPlugin 比 LlmPlugin 先安装。
     *
     * 因为没有 LlmKey，
     * Agent 应该保持 Pending。
     */
    @Test
    fun pluginShouldStayPendingWhenDependencyIsMissing() = runTest {
        val runtime =
            Runtime()

        val agentPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            LlmKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // 当前测试不需要实际逻辑。
                }
            }

        val agent =
            runtime.install(agentPlugin)

        assertEquals(
            FiberState.Pending,
            agent.state,
        )
    }

    /**
     * 验证：
     *
     * Agent 先安装时因为缺 LLM 保持 Pending。
     *
     * 后面 LlmPlugin 启动并 provide LlmKey 后，
     * Runtime 应该自动启动 Agent。
     */
    @Test
    fun pendingPluginShouldStartWhenDependencyAppears() = runTest {
        val runtime =
            Runtime()

        val agentPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            LlmKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    /**
                     * 能执行到这里，
                     * 就说明 Runtime 已经保证 LLM 存在。
                     */
                    context.require(LlmKey)
                }
            }

        val agent =
            runtime.install(agentPlugin)

        /**
         * LLM 还不存在。
         */
        assertEquals(
            FiberState.Pending,
            agent.state,
        )

        val llmPlugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    val service =
                        object : TestLlmService {}

                    /**
                     * LlmPlugin 提供 LLM Service。
                     *
                     * Disposable 放进 scope，
                     * 所以 LlmPlugin 卸载时，
                     * LlmKey 会自动消失。
                     */
                    scope.add(
                        context.provide(
                            key = LlmKey,
                            service = service,
                        )
                    )
                }
            }

        val llm =
            runtime.install(llmPlugin)

        /**
         * LlmPlugin 自己应该已经 Active。
         */
        assertEquals(
            FiberState.Active,
            llm.state,
        )

        /**
         * 更重要的是：
         *
         * Runtime 自动发现 Agent 的 dependency
         * 已经满足，并启动 Agent。
         *
         * 我们不再需要：
         *
         * agent.start()
         */
        assertEquals(
            FiberState.Active,
            agent.state,
        )
    }

    /**
     * 验证 Service 消失以后，
     * 依赖它的 Plugin 会自动停止。
     */
    @Test
    fun activePluginShouldBecomePendingWhenDependencyDisappears() = runTest {
        val runtime =
            Runtime()

        var agentCleanupCalled = false

        val agentPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            LlmKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    context.require(LlmKey)

                    /**
                     * 用来验证 Agent 确实被 deactivate。
                     */
                    scope.add {
                        agentCleanupCalled = true
                    }
                }
            }

        val agent =
            runtime.install(agentPlugin)

        val llmPlugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add(
                        context.provide(
                            LlmKey,
                            object : TestLlmService {},
                        )
                    )
                }
            }

        val llm =
            runtime.install(llmPlugin)

        /**
         * 两个插件现在都运行。
         */
        assertEquals(
            FiberState.Active,
            llm.state,
        )

        assertEquals(
            FiberState.Active,
            agent.state,
        )

        /**
         * 永久卸载 LlmPlugin。
         *
         * LlmPlugin.stop()
         *      ↓
         * scope.dispose()
         *      ↓
         * LlmKey remove
         *      ↓
         * Runtime.reconcile()
         */
        runtime.uninstall(llm)

        /**
         * Agent 因为失去了 LlmKey，
         * 应该自动 deactivate。
         *
         * 注意：
         *
         * 是 Pending，
         * 不是 Disposed。
         *
         * 因为以后 LLM 再回来，
         * Agent 还可以重新启动。
         */
        assertEquals(
            FiberState.Pending,
            agent.state,
        )

        /**
         * Agent 这一轮运行创建的资源
         * 也应该已经释放。
         */
        assertEquals(
            true,
            agentCleanupCalled,
        )
    }

    /**
     * 每一个安装到 Runtime 的 Plugin，
     * 都应该拥有独立的 child Context。
     *
     * Plugin 不应该直接使用 Runtime RootContext。
     */
    @Test
    fun eachPluginShouldHaveItsOwnContext() = runTest {
        val runtime =
            Runtime()

        val firstPlugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // 当前测试不需要执行逻辑。
                }
            }

        val secondPlugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // 当前测试不需要执行逻辑。
                }
            }

        val first =
            runtime.install(firstPlugin)

        val second =
            runtime.install(secondPlugin)

        /**
         * Plugin Context
         * 不应该就是 RootContext。
         */
        assertNotSame(
            runtime.context,
            first.context,
        )

        assertNotSame(
            runtime.context,
            second.context,
        )

        /**
         * 两个 Fiber
         * 也应该拥有不同 Context。
         */
        assertNotSame(
            first.context,
            second.context,
        )
    }

    /**
     * PluginContext 的 parent
     * 应该指向 Runtime RootContext。
     */
    @Test
    fun pluginContextShouldHaveContextAsParent() = runTest {
        val runtime =
            Runtime()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        val fiber =
            runtime.install(plugin)

        assertSame(
            runtime.context,
            fiber.context.parent,
        )
    }

    /**
     * 无论当前 Context 在树的哪一层，
     * root 都应该返回最顶部的 RootContext。
     */
    @Test
    fun pluginContextShouldResolveRootContext() = runTest {
        val runtime =
            Runtime()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        val fiber =
            runtime.install(plugin)

        assertSame(
            runtime.context,
            fiber.context.root,
        )

        /**
         * RootContext 的 root 当然也是自己。
         */
        assertSame(
            runtime.context,
            runtime.context.root,
        )
    }


    /**
     * 虽然每个 Plugin 已经拥有不同 Context，
     * 但当前阶段它们仍然共享 Runtime Service 空间。
     *
     * 因此：
     *
     * LlmPlugin 提供的 Service
     * 应该能够被 AgentPlugin 使用。
     *
     * isolate 还没有实现，
     * 所以目前 Service 默认仍然全局可见。
     */
    @Test
    fun serviceProvidedByOnePluginShouldBeVisibleToAnotherPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        var resolvedService: TestService? = null

        /**
         * Consumer 先安装。
         *
         * 因为 Service 还不存在，
         * 它应该保持 Pending。
         */
        val consumerPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    /**
                     * Provider 出现以后，
                     * Runtime 会重新启动 Consumer。
                     *
                     * 此时 ConsumerContext
                     * 应该能够看到 Provider 提供的 Service。
                     */
                    resolvedService =
                        context.require(serviceKey)
                }
            }

        val consumer =
            runtime.install(
                consumerPlugin,
            )

        assertEquals(
            FiberState.Pending,
            consumer.state,
        )

        val service =
            object : TestService {}

        val providerPlugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add(
                        context.provide(
                            key = serviceKey,
                            service = service,
                        )
                    )
                }
            }

        val provider =
            runtime.install(
                providerPlugin,
            )

        /**
         * Provider 已经启动。
         */
        assertEquals(
            FiberState.Active,
            provider.state,
        )

        /**
         * Consumer 也应该因为依赖满足
         * 被 Runtime 自动启动。
         */
        assertEquals(
            FiberState.Active,
            consumer.state,
        )

        /**
         * Consumer 实际拿到的
         * 就是 Provider 注册的那个 Service。
         */
        assertSame(
            service,
            resolvedService,
        )

        /**
         * 但两个 Plugin Context 本身不是同一个对象。
         */
        assertNotSame(
            provider.context,
            consumer.context,
        )
    }

    /**
     * Runtime 创建的每一个 Fiber
     * 都应该拥有不同的唯一 ID。
     */
    @Test
    fun eachFiberShouldHaveUniqueId() = runTest {
        val runtime =
            Runtime()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        val first =
            runtime.install(plugin)

        val second =
            runtime.install(plugin)

        /**
         * 即使是同一个 Plugin 对象安装两次，
         * 也应该得到两个不同的 Fiber。
         */
        assertNotEquals(
            first.id,
            second.id,
        )
    }

    /**
     * Plugin 提供的 ServiceBinding
     * 应该记录提供它的 Fiber ID。
     */
    @Test
    fun serviceBindingShouldRecordPluginOwner() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val providerPlugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add(
                        context.provide(
                            serviceKey,
                            object : TestService {},
                        )
                    )
                }
            }

        val provider =
            runtime.install(providerPlugin)

        /**
         * 从 Runtime 中找到当前 ServiceBinding。
         */
        val binding =
            runtime.context.binding(serviceKey)

        checkNotNull(binding)

        /**
         * ServiceBinding 应该知道：
         *
         * “我是 provider 这个 Fiber 提供的。”
         */
        assertEquals(
            provider.id,
            binding.ownerId,
        )
    }

    /**
     * Runtime Root 直接提供的 Service
     * 不属于任何 Fiber。
     */
    @Test
    fun rootServiceShouldHaveNoPluginOwner() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("root")

        runtime.provide(
            serviceKey,
            object : TestService {},
        )

        val binding =
            runtime.context.binding(serviceKey)

        checkNotNull(binding)

        assertEquals(
            null,
            binding.ownerId,
        )
    }

    /**
     * 同一个 ServiceKey 被重新 provide 时，
     * 应该产生新的 ServiceBinding ID。
     *
     * 即使 ServiceKey 没变化，
     * Runtime 也应该能区分：
     *
     * 旧 implementation
     *
     * 和
     *
     * 新 implementation。
     */
    @Test
    fun reProvidedServiceShouldHaveDifferentBindingId() = runTest {
        val context =
            Context()

        val serviceKey =
            ServiceKey<TestService>("test")

        /*
         * ──────────────────────
         * 第一次 provide
         * ──────────────────────
         */

        val firstDisposable =
            context.provide(
                serviceKey,
                object : TestService {},
            )

        val firstBinding =
            context.binding(serviceKey)

        checkNotNull(firstBinding)

        /*
         * 先移除第一次 Service。
         */
        firstDisposable.dispose()

        /*
         * ──────────────────────
         * 第二次 provide
         * ──────────────────────
         */

        context.provide(
            serviceKey,
            object : TestService {},
        )

        val secondBinding =
            context.binding(serviceKey)

        checkNotNull(secondBinding)

        /**
         * 虽然 key 都是 serviceKey，
         * 但它们属于两次不同 Service registration。
         */
        assertNotEquals(
            firstBinding.id,
            secondBinding.id,
        )
    }

    /**
     * 当 dependency 的 ServiceKey 没有消失，
     * 但具体 Binding implementation 被替换后，
     * 依赖它的 Plugin 应该自动 reload。
     */
    @Test
    fun activePluginShouldReloadWhenDependencyImplementationChanges() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val firstService =
            object : TestService {}

        val secondService =
            object : TestService {}

        /**
         * 记录 Agent 一共启动了几次。
         */
        var startCount = 0

        /**
         * 记录 Agent 被清理了几次。
         */
        var cleanupCount = 0

        /**
         * 记录每次 Agent 启动时，
         * 实际拿到的是哪个 Service。
         */
        val resolvedServices =
            mutableListOf<TestService>()

        /*
         * ──────────────────────
         * 先提供第一版 Service
         * ──────────────────────
         */

        runtime.provide(
            serviceKey,
            firstService,
        )

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    startCount += 1

                    resolvedServices +=
                        context.require(serviceKey)

                    scope.add {
                        cleanupCount += 1
                    }
                }
            }

        val fiber =
            runtime.install(plugin)

        /**
         * 第一次应该正常启动。
         */
        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        assertEquals(
            1,
            startCount,
        )

        assertSame(
            firstService,
            resolvedServices[0],
        )

        /*
         * ──────────────────────
         * 原地替换 Service
         * ──────────────────────
         *
         * 注意：
         *
         * ServiceKey 没有消失。
         *
         * 只是：
         *
         * Binding #1
         *
         * 变成：
         *
         * Binding #2
         */

        runtime.replace(
            serviceKey,
            secondService,
        )

        /**
         * Agent 最终仍然应该是 Active，
         * 因为新 Service 是存在的。
         */
        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        /**
         * 但应该重新运行了一次。
         */
        assertEquals(
            2,
            startCount,
        )

        /**
         * 第一轮运行应该已经 cleanup。
         */
        assertEquals(
            1,
            cleanupCount,
        )

        /**
         * 第二次启动拿到的应该是新 Service。
         */
        assertSame(
            secondService,
            resolvedServices[1],
        )
    }

    /**
     * Service 被 replace 后，
     * 旧 Binding 对应的 Disposable
     * 不应该误删新的 Service。
     *
     * 这个测试验证我们之前设计的：
     *
     * remove(binding)
     *
     * 而不是：
     *
     * remove(key)
     */
    @Test
    fun oldDisposableShouldNotRemoveReplacedService() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val firstService =
            object : TestService {}

        val secondService =
            object : TestService {}

        /**
         * 保存第一次 provide 返回的 Disposable。
         */
        val oldDisposable =
            runtime.provide(
                serviceKey,
                firstService,
            )

        /**
         * 原地替换。
         */
        runtime.replace(
            serviceKey,
            secondService,
        )

        /**
         * 此时再执行旧 Service 的 Disposable。
         */
        oldDisposable.dispose()

        /**
         * 新 Service 不能被删掉。
         */
        assertSame(
            secondService,
            runtime.context.require(serviceKey),
        )
    }

    /**
     * Runtime.install(plugin, config)
     * 应该把类型正确的 Config
     * 传递给 Plugin.apply()。
     */
    @Test
    fun configuredPluginShouldReceiveItsConfig() = runTest {
        val runtime =
            Runtime()

        var receivedConfig:
                TestPluginConfig? = null

        /**
         * 这是一个真正带 Config 的 Plugin。
         */
        val plugin =
            object : Plugin<TestPluginConfig> {

                override suspend fun apply(
                    context: Context,
                    config: TestPluginConfig,
                    scope: EffectScope,
                ) {
                    /**
                     * 保存 Runtime 传进来的配置，
                     * 后面验证是不是同一个。
                     */
                    receivedConfig =
                        config
                }
            }

        val config =
            TestPluginConfig(
                name = "hello",
                value = 42,
            )

        val fiber =
            runtime.install(
                plugin = plugin,
                config = config,
            )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        /**
         * Plugin 实际收到的，
         * 就应该是安装时传入的 Config。
         */
        assertEquals(
            config,
            receivedConfig,
        )

        /**
         * Fiber 自己也保存 Config。
         */
        assertEquals(
            config,
            fiber.config,
        )
    }

    /**
     * 同一个 Plugin 定义应该能够安装多次，
     * 每个 Fiber 保存自己的 Config。
     *
     * 这进一步证明：
     *
     * Config 属于 Fiber，
     * 而不是 Plugin Definition。
     */
    @Test
    fun samePluginShouldSupportMultipleConfigs() = runTest {
        val runtime =
            Runtime()

        val receivedNames =
            mutableListOf<String>()

        val plugin =
            object : Plugin<TestPluginConfig> {

                override suspend fun apply(
                    context: Context,
                    config: TestPluginConfig,
                    scope: EffectScope,
                ) {
                    receivedNames +=
                        config.name
                }
            }

        val first =
            runtime.install(
                plugin = plugin,
                config = TestPluginConfig(
                    name = "first",
                    value = 1,
                ),
            )

        val second =
            runtime.install(
                plugin = plugin,
                config = TestPluginConfig(
                    name = "second",
                    value = 2,
                ),
            )

        /**
         * 两次安装是两个独立实例。
         */
        assertNotEquals(
            first.id,
            second.id,
        )

        assertEquals(
            "first",
            first.config.name,
        )

        assertEquals(
            "second",
            second.config.name,
        )

        assertEquals(
            listOf(
                "first",
                "second",
            ),
            receivedNames,
        )
    }

    /**
     * SimplePlugin 应该继续支持：
     *
     * runtime.install(plugin)
     *
     * 调用方不需要手动传 Unit。
     */
    @Test
    fun simplePluginShouldInstallWithoutExplicitConfig() = runTest {
        val runtime =
            Runtime()

        var applied = false

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    applied = true
                }
            }

        val fiber =
            runtime.install(plugin)

        assertEquals(
            true,
            applied,
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        /**
         * SimplePlugin 底层实际还是 Plugin<Unit>。
         */
        assertEquals(
            Unit,
            fiber.config,
        )
    }

    /**
     * Plugin 通过 EventsService 注册的 Listener，
     * 应该随着 Plugin 生命周期自动注销。
     *
     * 这验证：
     *
     * EventsService.on()
     *      ↓
     * Disposable
     *      ↓
     * EffectScope
     *      ↓
     * Plugin.stop()
     */
    @Test
    fun pluginListenerShouldBeRemovedAfterPluginStops() = runTest {
        val runtime =
            Runtime()

        var callCount = 0

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {

                    /**
                     * Listener 属于当前 Plugin
                     * 的运行时副作用。
                     */
                    scope.add(
                        context.events.on(
                            TestRuntimeEventKey,
                        ) {
                            callCount += 1
                        }
                    )
                }
            }

        val fiber =
            runtime.install(plugin)

        /*
         * ─────────────────────
         * Plugin Active
         * ─────────────────────
         */

        runtime.context.events.emit(
            TestRuntimeEventKey,
            TestRuntimeEvent("first"),
        )

        assertEquals(
            1,
            callCount,
        )

        /*
         * ─────────────────────
         * Plugin uninstall
         * ─────────────────────
         */

        runtime.uninstall(fiber)

        /**
         * stop()
         *
         * → scope.dispose()
         *
         * → Event Listener dispose
         */
        runtime.context.events.emit(
            TestRuntimeEventKey,
            TestRuntimeEvent("second"),
        )

        /**
         * callCount 不应该再增加。
         */
        assertEquals(
            1,
            callCount,
        )
    }

    /**
     * Plugin 因 dependency implementation 变化而 reload 时，
     * 旧 Listener 应该被注销，
     * 新 Listener 应该重新注册。
     *
     * 不能发生 Listener 累积。
     */
    @Test
    fun pluginReloadShouldNotDuplicateEventListeners() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        runtime.provide(
            serviceKey,
            object : TestService {},
        )

        var eventCallCount = 0

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {

                    /**
                     * 每一次 Plugin start
                     * 都会注册一个新的 Listener。
                     *
                     * 旧一轮 Listener 应该在 deactivate
                     * 时被自动清理。
                     */
                    scope.add(
                        context.events.on(
                            TestRuntimeEventKey,
                        ) {
                            eventCallCount += 1
                        }
                    )
                }
            }

        runtime.install(plugin)

        /**
         * 第一次：
         *
         * 应该只有一个 Listener。
         */
        runtime.context.events.emit(
            TestRuntimeEventKey,
            TestRuntimeEvent("before"),
        )

        assertEquals(
            1,
            eventCallCount,
        )

        /**
         * 替换 dependency。
         *
         * Plugin 应该：
         *
         * deactivate
         * ↓
         * old listener dispose
         * ↓
         * start
         * ↓
         * new listener register
         */
        runtime.replace(
            serviceKey,
            object : TestService {},
        )

        runtime.context.events.emit(
            TestRuntimeEventKey,
            TestRuntimeEvent("after"),
        )

        /**
         * 正确结果：
         *
         * 第一次 emit = +1
         * 第二次 emit = +1
         *
         * 总共 2。
         *
         * 如果旧 Listener 没清理，
         * 这里会变成 3。
         */
        assertEquals(
            2,
            eventCallCount,
        )
    }

    /**
     * Plugin 安装到指定的 isolated parent Context 后，
     * 应该使用这个 Context 的 isolated Service。
     *
     *
     * Root:
     *
     * LlmKey -> rootService
     *
     *
     * IsolatedScope:
     *
     * LlmKey -> isolatedService
     *
     *
     * Plugin 安装在 IsolatedScope 下：
     *
     * Plugin 应该获取 isolatedService。
     */
    @Test
    fun pluginInstalledUnderIsolatedContextShouldUseIsolatedService() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val rootService =
            object : TestService {}

        val isolatedService =
            object : TestService {}

        /*
         * ──────────────────────
         * Root 提供默认 Service
         * ──────────────────────
         */

        runtime.provide(
            serviceKey,
            rootService,
        )

        /*
         * ──────────────────────
         * 创建 isolated Scope
         * ──────────────────────
         */

        val isolatedScope =
            runtime.context.isolate(
                serviceKey,
            )

        isolatedScope.provide(
            serviceKey,
            isolatedService,
        )

        var resolvedService:
                TestService? = null

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    /**
                     * Plugin 本身完全不知道 isolate。
                     *
                     * 它还是普通地：
                     *
                     * context.require(serviceKey)
                     *
                     * Context Resolution 会决定
                     * 最终使用哪一个 ServiceSlot。
                     */
                    resolvedService =
                        context.require(
                            serviceKey,
                        )
                }
            }

        val fiber =
            runtime.install(
                plugin = plugin,

                /**
                 * 关键：
                 *
                 * Plugin 挂载在 isolatedScope 下面。
                 */
                parent = isolatedScope,
            )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        assertSame(
            isolatedService,
            resolvedService,
        )

        /**
         * Root Service 没有受到影响。
         */
        assertSame(
            rootService,
            runtime.context.require(serviceKey),
        )
    }

    /**
     * 没有指定 parent Context 时，
     * Plugin 应该仍然安装在 Runtime RootContext 下，
     * 并使用默认 Service。
     */
    @Test
    fun pluginInstalledNormallyShouldUseRootService() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val rootService =
            object : TestService {}

        runtime.provide(
            serviceKey,
            rootService,
        )

        var resolvedService:
                TestService? = null

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    resolvedService =
                        context.require(serviceKey)
                }
            }

        runtime.install(plugin)

        assertSame(
            rootService,
            resolvedService,
        )
    }

    /**
     * 同一个 isolated Context
     * 应该能够作为多个 Plugin 的共同 parent。
     *
     *
     * Root
     *  │
     *  └── IsolatedScope
     *       ├── Plugin A
     *       └── Plugin B
     *
     *
     * 两个 Plugin 都应该继承
     * IsolatedScope 的 Service Resolution。
     */
    @Test
    fun multiplePluginsUnderSameIsolatedContextShouldShareIsolation() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        runtime.provide(
            serviceKey,
            object : TestService {},
        )

        val isolatedService =
            object : TestService {}

        val isolatedScope =
            runtime.context.isolate(
                serviceKey,
            )

        isolatedScope.provide(
            serviceKey,
            isolatedService,
        )

        val resolvedServices =
            mutableListOf<TestService>()

        fun createPlugin(): SimplePlugin {
            return object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    resolvedServices +=
                        context.require(serviceKey)
                }
            }
        }

        runtime.install(
            plugin = createPlugin(),
            parent = isolatedScope,
        )

        runtime.install(
            plugin = createPlugin(),
            parent = isolatedScope,
        )

        assertEquals(
            2,
            resolvedServices.size,
        )

        assertSame(
            isolatedService,
            resolvedServices[0],
        )

        assertSame(
            isolatedService,
            resolvedServices[1],
        )
    }

    /**
     * 当 Runtime.install 指定 parent 时，
     * Fiber.context.parent
     * 应该就是这个指定的 Context。
     */
    @Test
    fun pluginContextShouldUseSpecifiedParent() = runTest {
        val runtime =
            Runtime()

        val scope =
            runtime.context.child()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        val fiber =
            runtime.install(
                plugin = plugin,
                parent = scope,
            )

        assertSame(
            scope,
            fiber.context.parent,
        )
    }

    /**
     * Runtime 不应该允许 Plugin
     * 安装到另一个 Runtime 的 Context Tree。
     *
     * 否则 Runtime ownership 会发生混乱。
     */
    @Test
    fun installShouldRejectContextFromAnotherRuntime() = runTest {
        val firstRuntime =
            Runtime()

        val secondRuntime =
            Runtime()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        var failed = false

        try {
            firstRuntime.install(
                plugin = plugin,

                /**
                 * 错误：
                 *
                 * secondRuntime.context
                 * 不属于 firstRuntime。
                 */
                parent = secondRuntime.context,
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertEquals(
            true,
            failed,
        )
    }

    /**
     * 通过普通 Context.provide()
     * 提供 Service 时，
     * Runtime 也应该自动刷新 dependency。
     *
     * 不应该只有 runtime.provide()
     * 才能唤醒 Pending Plugin。
     */
    @Test
    fun contextProvideShouldWakePendingPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        /**
         * 创建一个 isolate Scope，
         * 但是暂时没有提供 Service。
         */
        val scope =
            runtime.context.isolate(
                serviceKey,
            )

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    context.require(serviceKey)
                }
            }

        /**
         * Plugin 挂在 isolated Scope 下面。
         *
         * 由于 isolated Slot 目前为空，
         * Plugin 应该保持 Pending。
         */
        val fiber =
            runtime.install(
                plugin = plugin,
                parent = scope,
            )

        assertEquals(
            FiberState.Pending,
            fiber.state,
        )

        /**
         * 注意：
         *
         * 这里不是 runtime.provide()。
         *
         * 而是直接调用 Context。
         */
        scope.provide(
            serviceKey,
            object : TestService {},
        )

        /**
         * Context 的 Service Change
         * 应该自动通知 Runtime。
         */
        assertEquals(
            FiberState.Active,
            fiber.state,
        )
    }

    /**
     * Context 中的 Service 被 dispose 后，
     * 依赖它的 Active Plugin
     * 应该自动 deactivate 回到 Pending。
     */
    @Test
    fun disposingContextServiceShouldDeactivateDependentPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val scope =
            runtime.context.isolate(
                serviceKey,
            )

        /**
         * 保存 Service Registration 对应的 Disposable。
         */
        val serviceDisposable =
            scope.provide(
                serviceKey,
                object : TestService {},
            )

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    context.require(serviceKey)
                }
            }

        val fiber =
            runtime.install(
                plugin = plugin,
                parent = scope,
            )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        /**
         * Service 消失。
         *
         * dispose()
         *   ↓
         * notify Runtime
         *   ↓
         * reconcile
         */
        serviceDisposable.dispose()

        assertEquals(
            FiberState.Pending,
            fiber.state,
        )
    }

    /**
     * Context 中某个 Service implementation 被替换后，
     * 依赖它的 Plugin 应该自动 reload。
     *
     * 这验证：
     *
     * Context Service Change
     * +
     * Binding Identity
     * +
     * Dependency Snapshot
     *
     * 三套机制已经真正串起来。
     */
    @Test
    fun contextReplaceShouldReloadDependentPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        val scope =
            runtime.context.isolate(
                serviceKey,
            )

        val firstService =
            object : TestService {}

        val secondService =
            object : TestService {}

        scope.provide(
            serviceKey,
            firstService,
        )

        var startCount = 0

        val resolvedServices =
            mutableListOf<TestService>()

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    startCount += 1

                    resolvedServices +=
                        context.require(serviceKey)
                }
            }

        val fiber =
            runtime.install(
                plugin = plugin,
                parent = scope,
            )

        assertEquals(
            1,
            startCount,
        )

        assertSame(
            firstService,
            resolvedServices[0],
        )

        /**
         * ServiceKey 没消失，
         * 但是 Binding Identity 改变。
         */
        scope.replace(
            serviceKey,
            secondService,
        )

        /**
         * Plugin 应该已经自动 reload。
         */
        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        assertEquals(
            2,
            startCount,
        )

        assertSame(
            secondService,
            resolvedServices[1],
        )
    }

    /**
     * 同一个 ServiceKey 在两个不同 ServiceSlot 中存在时，
     * 修改 Root Slot 不应该 reload
     * 使用 isolated Slot 的 Plugin。
     *
     *
     * Root LlmSlot
     *     -> rootPlugin
     *
     * Isolated LlmSlot
     *     -> isolatedPlugin
     *
     *
     * Root Slot replace：
     *
     * rootPlugin     reload
     * isolatedPlugin 不动
     */
    @Test
    fun replacingRootServiceShouldNotReloadPluginUsingIsolatedSlot() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        /*
         * ─────────────────────
         * Root Service
         * ─────────────────────
         */

        runtime.provide(
            serviceKey,
            object : TestService {},
        )

        /*
         * ─────────────────────
         * Isolated Service
         * ─────────────────────
         */

        val isolatedScope =
            runtime.context.isolate(
                serviceKey
            )

        isolatedScope.provide(
            serviceKey,
            object : TestService {},
        )

        var rootStartCount = 0
        var isolatedStartCount = 0

        val rootPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    rootStartCount += 1
                }
            }

        val isolatedPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    isolatedStartCount += 1
                }
            }

        runtime.install(
            rootPlugin
        )

        runtime.install(
            plugin = isolatedPlugin,
            parent = isolatedScope,
        )

        assertEquals(
            1,
            rootStartCount,
        )

        assertEquals(
            1,
            isolatedStartCount,
        )

        /*
         * ─────────────────────
         * 只替换 Root Slot
         * ─────────────────────
         */

        runtime.replace(
            serviceKey,
            object : TestService {},
        )

        /**
         * Root Plugin 应该 reload。
         */
        assertEquals(
            2,
            rootStartCount,
        )

        /**
         * isolated Plugin 对应的是另一个 Slot，
         * 所以不应该 reload。
         */
        assertEquals(
            1,
            isolatedStartCount,
        )
    }

    /**
     * 修改 isolated ServiceSlot 时，
     * 不应该 reload 使用 Root Slot 的 Plugin。
     */
    @Test
    fun replacingIsolatedServiceShouldNotReloadPluginUsingRootSlot() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("test")

        runtime.provide(
            serviceKey,
            object : TestService {},
        )

        val isolatedScope =
            runtime.context.isolate(
                serviceKey
            )

        isolatedScope.provide(
            serviceKey,
            object : TestService {},
        )

        var rootStartCount = 0
        var isolatedStartCount = 0

        val rootPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    rootStartCount += 1
                }
            }

        val isolatedPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    isolatedStartCount += 1
                }
            }

        runtime.install(
            rootPlugin
        )

        runtime.install(
            plugin = isolatedPlugin,
            parent = isolatedScope,
        )

        /**
         * 只替换 isolated Slot。
         */
        isolatedScope.replace(
            serviceKey,
            object : TestService {},
        )

        assertEquals(
            1,
            rootStartCount,
        )

        assertEquals(
            2,
            isolatedStartCount,
        )
    }

    /**
     * 定向刷新以后，
     * Plugin A -> Service A
     * Plugin B -> Service B
     * Plugin C
     *
     * 这样的依赖链仍然应该自动传播。
     */
    @Test
    fun targetedRefreshShouldSupportDependencyChains() = runTest {
        val runtime =
            Runtime()

        val firstKey =
            ServiceKey<TestService>("first")

        val secondKey =
            ServiceKey<TestService>("second")

        var secondStarted = false
        var thirdStarted = false

        /**
         * B:
         *
         * depends first
         * provide second
         */
        val secondPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            firstKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    secondStarted = true

                    scope.add(
                        context.provide(
                            secondKey,
                            object : TestService {},
                        )
                    )
                }
            }

        /**
         * C:
         *
         * depends second
         */
        val thirdPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            secondKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    thirdStarted = true
                }
            }

        /**
         * 先安装 B / C。
         *
         * 两者都应该 Pending。
         */
        val second =
            runtime.install(
                secondPlugin
            )

        val third =
            runtime.install(
                thirdPlugin
            )

        assertEquals(
            FiberState.Pending,
            second.state,
        )

        assertEquals(
            FiberState.Pending,
            third.state,
        )

        /*
         * 提供 first：
         *
         * first changed
         * ↓
         * refresh B
         * ↓
         * B provide second
         * ↓
         * second changed
         * ↓
         * refresh C
         */

        runtime.provide(
            firstKey,
            object : TestService {},
        )

        assertEquals(
            true,
            secondStarted,
        )

        assertEquals(
            true,
            thirdStarted,
        )

        assertEquals(
            FiberState.Active,
            second.state,
        )

        assertEquals(
            FiberState.Active,
            third.state,
        )
    }

    /**
     * required dependency 不存在时，
     * Plugin 必须保持 Pending。
     */
    @Test
    fun requiredDependencyShouldBlockPluginStart() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("required")

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        val fiber =
            runtime.install(plugin)

        assertEquals(
            FiberState.Pending,
            fiber.state,
        )
    }

    /**
     * optional dependency 不存在时，
     * Plugin 仍然应该能够正常启动。
     */
    @Test
    fun optionalDependencyShouldNotBlockPluginStart() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("optional")

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.optional(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    /**
                     * optional dependency
                     * 可以安全使用 get()。
                     *
                     * 当前应该为 null。
                     */
                    assertEquals(
                        null,
                        context.get(serviceKey),
                    )
                }
            }

        val fiber =
            runtime.install(plugin)

        assertEquals(
            FiberState.Active,
            fiber.state,
        )
    }

    /**
     * optional Service 虽然不阻塞 Plugin 启动，
     * 但是它后来出现时，
     * Plugin 应该 reload。
     *
     *
     * start #1:
     *
     * optional = null
     *
     *
     * Service 出现：
     *
     * null -> Binding #1
     *
     *
     * start #2:
     *
     * optional = service
     */
    @Test
    fun optionalDependencyAppearingShouldReloadPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("optional")

        var startCount = 0

        val resolvedServices =
            mutableListOf<TestService?>()

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.optional(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    startCount += 1

                    resolvedServices +=
                        context.get(serviceKey)
                }
            }

        val fiber =
            runtime.install(plugin)

        /**
         * optional 不存在，
         * 仍然成功启动。
         */
        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        assertEquals(
            1,
            startCount,
        )

        assertEquals(
            null,
            resolvedServices[0],
        )

        val service =
            object : TestService {}

        /**
         * optional Service 后来出现。
         *
         * dependencyIndex 会找到这个 Plugin，
         * dependenciesChanged 检测：
         *
         * null -> Binding ID
         *
         * 因而 reload。
         */
        runtime.provide(
            serviceKey,
            service,
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        assertEquals(
            2,
            startCount,
        )

        assertSame(
            service,
            resolvedServices[1],
        )
    }

    /**
     * optional Service 消失时：
     *
     * Plugin 应该 reload，
     * 但是不能进入长期 Pending。
     *
     *
     * Binding #1 -> null
     *
     * 只是运行环境发生变化，
     * 而不是启动条件不满足。
     */
    @Test
    fun optionalDependencyDisappearingShouldReloadPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("optional")

        val service =
            object : TestService {}

        val serviceDisposable =
            runtime.provide(
                serviceKey,
                service,
            )

        var startCount = 0

        val resolvedServices =
            mutableListOf<TestService?>()

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.optional(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    startCount += 1

                    resolvedServices +=
                        context.get(serviceKey)
                }
            }

        val fiber =
            runtime.install(plugin)

        assertEquals(
            1,
            startCount,
        )

        assertSame(
            service,
            resolvedServices[0],
        )

        /**
         * optional Service 消失。
         */
        serviceDisposable.dispose()

        /**
         * Plugin reload 了一次。
         */
        assertEquals(
            2,
            startCount,
        )

        /**
         * 但仍然保持 Active。
         */
        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        assertEquals(
            null,
            resolvedServices[1],
        )
    }

    /**
     * optional Service implementation 被替换时，
     * Plugin 应该 reload。
     */
    @Test
    fun optionalDependencyReplacementShouldReloadPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("optional")

        val firstService =
            object : TestService {}

        val secondService =
            object : TestService {}

        runtime.provide(
            serviceKey,
            firstService,
        )

        var startCount = 0

        val resolvedServices =
            mutableListOf<TestService?>()

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.optional(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    startCount += 1

                    resolvedServices +=
                        context.get(serviceKey)
                }
            }

        runtime.install(plugin)

        assertEquals(
            1,
            startCount,
        )

        assertSame(
            firstService,
            resolvedServices[0],
        )

        runtime.replace(
            serviceKey,
            secondService,
        )

        assertEquals(
            2,
            startCount,
        )

        assertSame(
            secondService,
            resolvedServices[1],
        )
    }

    @Test
    fun disposingScopeShouldUnloadDescendantPluginsAndOwnedService() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("scope-owned")

        val contextScope =
            runtime.context.isolate(serviceKey)

        contextScope.provide(
            serviceKey,
            object : TestService {},
        )

        val stopOrder =
            mutableListOf<String>()

        fun plugin(
            name: String,
        ): SimplePlugin {
            return object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    context.require(serviceKey)

                    scope.add {
                        stopOrder += name
                    }
                }
            }
        }

        val first =
            runtime.install(
                plugin = plugin("first"),
                parent = contextScope,
            )

        val nestedScope =
            first.context.child()

        val nested =
            runtime.install(
                plugin = plugin("nested"),
                parent = nestedScope,
            )

        val sibling =
            runtime.install(
                plugin = plugin("sibling"),
                parent = contextScope,
            )

        contextScope.dispose()

        assertEquals(
            listOf(
                "nested",
                "sibling",
                "first",
            ),
            stopOrder,
        )

        assertEquals(
            FiberState.Disposed,
            first.state,
        )

        assertEquals(
            FiberState.Disposed,
            nested.state,
        )

        assertEquals(
            FiberState.Disposed,
            sibling.state,
        )

        assertEquals(
            emptyList(),
            runtime.fibers,
        )

        assertEquals(
            true,
            contextScope.isDisposed,
        )

        assertEquals(
            true,
            nestedScope.isDisposed,
        )

        assertEquals(
            true,
            first.context.isDisposed,
        )

    }

    @Test
    fun disposingContextShouldRemoveOwnedServiceAndRefreshExternalDependent() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("context-owned")

        /**
         * 普通 child 与 Root 解析到同一个默认 Slot，
         * 但 Service registration 的生命周期属于 child Context。
         */
        val providerScope =
            runtime.context.child()

        providerScope.provide(
            serviceKey,
            object : TestService {},
        )

        val dependent =
            runtime.install(
                object : SimplePlugin {

                    override val inject =
                        setOf(
                            InjectSpec.required(
                                serviceKey
                            )
                        )

                    override suspend fun apply(
                        context: Context,
                        scope: EffectScope,
                    ) {
                        context.require(serviceKey)
                    }
                }
            )

        assertEquals(
            FiberState.Active,
            dependent.state,
        )

        providerScope.dispose()

        /**
         * dependent 不在 providerScope 子树中，因此不会被卸载；
         * 它只会因为 Context-owned Service 消失而回到 Pending。
         */
        assertEquals(
            FiberState.Pending,
            dependent.state,
        )

        assertEquals(
            listOf(dependent),
            runtime.fibers,
        )
    }

    @Test
    fun disposingIsolatedScopeShouldNotReloadRootPlugin() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("targeted-dispose")

        runtime.provide(
            serviceKey,
            object : TestService {},
        )

        val isolatedScope =
            runtime.context.isolate(serviceKey)

        isolatedScope.provide(
            serviceKey,
            object : TestService {},
        )

        var rootStartCount = 0

        val rootPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    rootStartCount += 1
                }
            }

        val isolatedPlugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            serviceKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    context.require(serviceKey)
                }
            }

        val rootInstance =
            runtime.install(rootPlugin)

        val isolatedInstance =
            runtime.install(
                plugin = isolatedPlugin,
                parent = isolatedScope,
            )

        isolatedScope.dispose()

        assertEquals(
            1,
            rootStartCount,
        )

        assertEquals(
            FiberState.Active,
            rootInstance.state,
        )

        assertEquals(
            FiberState.Disposed,
            isolatedInstance.state,
        )

        assertEquals(
            listOf(rootInstance),
            runtime.fibers,
        )
    }

    @Test
    fun uninstallingPluginShouldDisposeItsContextAndDescendantPlugins() = runTest {
        val runtime =
            Runtime()

        val stopOrder =
            mutableListOf<String>()

        fun plugin(
            name: String,
        ): SimplePlugin {
            return object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add {
                        stopOrder += name
                    }
                }
            }
        }

        val parent =
            runtime.install(
                plugin("parent")
            )

        val childScope =
            parent.context.child()

        val child =
            runtime.install(
                plugin = plugin("child"),
                parent = childScope,
            )

        runtime.uninstall(parent)

        assertEquals(
            listOf(
                "child",
                "parent",
            ),
            stopOrder,
        )

        assertEquals(
            FiberState.Disposed,
            parent.state,
        )

        assertEquals(
            FiberState.Disposed,
            child.state,
        )

        assertEquals(
            true,
            parent.context.isDisposed,
        )

        assertEquals(
            true,
            childScope.isDisposed,
        )

        assertEquals(
            true,
            child.context.isDisposed,
        )

        assertEquals(
            emptyList(),
            runtime.fibers,
        )
    }

    @Test
    fun installShouldRejectDisposedParentContext() = runTest {
        val runtime =
            Runtime()

        val disposedScope =
            runtime.context.child()

        disposedScope.dispose()

        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // no-op
                }
            }

        var failed = false

        try {
            runtime.install(
                plugin = plugin,
                parent = disposedScope,
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertEquals(
            true,
            failed,
        )

        assertEquals(
            emptyList(),
            runtime.fibers,
        )
    }

    @Test
    fun runtimeShouldRetryManagedFailedFiber() = runTest {
        val runtime =
            Runtime()

        var attempts = 0

        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    attempts++

                    if (attempts == 1) {
                        error("first attempt")
                    }
                }
            }

        assertFailsWith<IllegalStateException> {
            runtime.install(plugin)
        }

        val fiber =
            runtime.fibers.single()

        assertEquals(
            FiberState.Failed,
            fiber.state,
        )
        assertNotNull(
            fiber.failure,
        )

        assertTrue(
            runtime.retry(fiber)
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )
        assertNull(
            fiber.failure,
        )
        assertEquals(
            2,
            attempts,
        )
    }

    @Test
    fun uninstallShouldBeIdempotent() = runTest {
        val runtime =
            Runtime()

        var cleanups = 0

        val fiber =
            runtime.install(
                object : SimplePlugin {
                    override suspend fun apply(
                        context: Context,
                        scope: EffectScope,
                    ) {
                        scope.add {
                            cleanups++
                        }
                    }
                }
            )

        runtime.uninstall(fiber)
        runtime.uninstall(fiber)

        assertEquals(
            1,
            cleanups,
        )
        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
        assertEquals(
            emptyList(),
            runtime.fibers,
        )
    }

    @Test
    fun uninstallShouldRejectUnmanagedFiber() = runTest {
        val runtime =
            Runtime()

        val unmanagedContext =
            runtime.context.child()

        val unmanagedFiber =
            im.hikaru.harness.runtime.plugin.Fiber(
                plugin =
                    object : SimplePlugin {
                        override suspend fun apply(
                            context: Context,
                            scope: EffectScope,
                        ) {
                            // no-op
                        }
                    },
                config = Unit,
                context = unmanagedContext,
            )

        assertFailsWith<IllegalStateException> {
            runtime.uninstall(unmanagedFiber)
        }

        assertFalse(
            unmanagedContext.isDisposed,
        )

        unmanagedContext.dispose()
    }

    @Test
    fun concurrentInstallsShouldUseSingleMutationLane() = runTest {
        val runtime =
            Runtime()

        var activeApplies = 0
        var maxActiveApplies = 0

        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    activeApplies++
                    maxActiveApplies =
                        maxOf(
                            maxActiveApplies,
                            activeApplies,
                        )

                    yield()

                    activeApplies--
                }
            }

        val installed =
            (1..20)
                .map {
                    async {
                        runtime.install(plugin)
                    }
                }
                .awaitAll()

        assertEquals(
            1,
            maxActiveApplies,
        )
        assertEquals(
            20,
            installed.map { it.id }.distinct().size,
        )
        assertEquals(
            20,
            runtime.fibers.size,
        )
        assertTrue(
            runtime.fibers.all {
                it.state == FiberState.Active
            }
        )
    }

    @Test
    fun retryAndUninstallShouldSerializeToDisposedState() = runTest {
        val runtime =
            Runtime()

        var attempts = 0
        val retryStarted =
            CompletableDeferred<Unit>()
        val allowRetryToFinish =
            CompletableDeferred<Unit>()

        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    attempts++

                    if (attempts == 1) {
                        error("first attempt")
                    }

                    retryStarted.complete(Unit)
                    allowRetryToFinish.await()
                }
            }

        assertFailsWith<IllegalStateException> {
            runtime.install(plugin)
        }

        val fiber =
            runtime.fibers.single()

        val retry =
            async {
                runtime.retry(fiber)
            }

        retryStarted.await()

        val uninstall =
            async {
                runtime.uninstall(fiber)
            }

        allowRetryToFinish.complete(Unit)

        assertTrue(
            retry.await()
        )
        uninstall.await()

        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
        assertEquals(
            emptyList(),
            runtime.fibers,
        )
    }

    @Test
    fun lifecycleMutationShouldRemainReentrantAcrossContextSwitch() = runTest {
        val runtime =
            Runtime()

        val serviceKey =
            ServiceKey<TestService>("context-switch")

        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                runtime.install(
                    object : SimplePlugin {
                        override suspend fun apply(
                            context: Context,
                            scope: EffectScope,
                        ) {
                            withContext(Dispatchers.Default) {
                                scope.add(
                                    context.provide(
                                        serviceKey,
                                        object : TestService {},
                                    )
                                )
                            }
                        }
                    }
                )
            }
        }

        assertTrue(
            runtime.context.has(serviceKey)
        )
    }
}
