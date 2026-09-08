package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Fiber 的核心生命周期测试。
 *
 * 当前主要验证：
 *
 * 1. Plugin 能正常启动
 * 2. Plugin 启动失败后进入 Failed
 * 3. Disposable 按相反顺序释放
 * 4. 缺少依赖时 Plugin 保持 Pending
 * 5. 依赖出现后 Plugin 可以启动
 * 6. 多个依赖必须全部满足后才能启动
 */
class FiberTest {

    /**
     * 测试中使用的简单 Service。
     *
     * 这里只是为了模拟未来的：
     *
     * LlmService
     * ToolsService
     * FileService
     *
     * 等 Runtime Service。
     */
    private interface TestService

    /**
     * 测试普通插件能够成功启动。
     *
     * 生命周期：
     *
     * Pending
     *    ↓
     * Loading
     *    ↓
     * Active
     */
    @Test
    fun pluginShouldBecomeActiveAfterStart() = runTest {
        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // 当前插件没有任何初始化逻辑。
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        val started =
            fiber.start()

        assertEquals(
            true,
            started,
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )
    }

    /**
     * Plugin.apply() 如果抛出异常，
     * Fiber 应该进入 Failed 状态。
     *
     * 生命周期：
     *
     * Pending
     *    ↓
     * Loading
     *    ↓
     * Failed
     */
    @Test
    fun pluginShouldBecomeFailedWhenApplyThrows() = runTest {
        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    error("boom")
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        var thrown = false

        try {
            fiber.start()
        } catch (_: IllegalStateException) {
            thrown = true
        }

        /**
         * apply() 抛出的异常不能被 Runtime 吃掉。
         */
        assertEquals(
            true,
            thrown,
        )

        /**
         * 同时 Fiber 应该记录自己启动失败。
         */
        assertEquals(
            FiberState.Failed,
            fiber.state,
        )
    }

    /**
     * Plugin 创建的资源必须按照创建顺序的反方向释放。
     *
     * 注册：
     *
     * first
     * second
     * third
     *
     * dispose：
     *
     * third
     * second
     * first
     *
     * 这是 Cordis Effect 生命周期中非常重要的设计。
     */
    @Test
    fun disposablesShouldBeDisposedInReverseOrder() = runTest {
        val calls =
            mutableListOf<String>()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add {
                        calls += "first"
                    }

                    scope.add {
                        calls += "second"
                    }

                    scope.add {
                        calls += "third"
                    }
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        fiber.start()
        fiber.stop()

        assertEquals(
            listOf(
                "third",
                "second",
                "first",
            ),
            calls,
        )

        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
    }

    /**
     * Plugin 声明了依赖，
     * 但对应 Service 不存在时，
     * Plugin 不应该执行 apply()。
     *
     * 此时它不是 Failed，
     * 而只是继续等待：
     *
     * Pending
     */
    @Test
    fun pluginShouldStayPendingWhenDependencyIsMissing() = runTest {
        val serviceKey =
            ServiceKey<TestService>("test")

        val plugin =
            object : SimplePlugin {

                /**
                 * 声明当前 Plugin 依赖 TestService。
                 */
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
                     * 如果依赖检查正确，
                     * 这里永远不应该执行。
                     */
                    error(
                        "apply should not be called"
                    )
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        val started =
            fiber.start()

        /**
         * start() 返回 false：
         *
         * 表示当前不能启动，
         * 原因是依赖还没有满足。
         */
        assertEquals(
            false,
            started,
        )

        assertEquals(
            FiberState.Pending,
            fiber.state,
        )

        /**
         * 应该能够知道具体缺少哪个 Service。
         */
        assertEquals(
            listOf(serviceKey),
            fiber.missingDependencies,
        )
    }

    /**
     * Plugin 最开始缺少依赖，
     * 所以第一次 start() 不启动。
     *
     * Service 被 provide 后，
     * 再次 start() 就能够成功。
     *
     * 注意：
     *
     * 当前版本还需要手动再次调用 start()。
     *
     * 下一阶段我们会加入 Runtime，
     * 让 Service 出现后自动唤醒等待中的 Plugin。
     */
    @Test
    fun pluginShouldStartAfterDependencyIsProvided() = runTest {
        val serviceKey =
            ServiceKey<TestService>("test")

        val context =
            Context()

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
                     * 能执行到这里时，
                     * Runtime 应该已经保证依赖存在。
                     */
                    context.require(serviceKey)
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = context,
            )

        /**
         * 第一次：
         *
         * TestService 不存在。
         */
        val firstStart =
            fiber.start()

        assertEquals(
            false,
            firstStart,
        )

        assertEquals(
            FiberState.Pending,
            fiber.state,
        )

        /**
         * 后来 TestService 被提供。
         */
        val service =
            object : TestService {}

        context.provide(
            key = serviceKey,
            service = service,
        )

        /**
         * 目前仍然需要手动再次尝试启动。
         */
        val secondStart =
            fiber.start()

        assertEquals(
            true,
            secondStart,
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )
    }

    /**
     * 验证多个 dependency 必须全部满足。
     *
     * 例如真实场景：
     *
     * AgentPlugin
     *    ├── LlmKey
     *    └── ToolsKey
     *
     * 只有 LLM：
     *
     * Pending
     *
     * LLM + Tools 都存在：
     *
     * Active
     */
    @Test
    fun pluginShouldWaitUntilAllDependenciesAreAvailable() = runTest {
        val llmKey =
            ServiceKey<TestService>("llm")

        val toolsKey =
            ServiceKey<TestService>("tools")

        val context =
            Context()

        val plugin =
            object : SimplePlugin {

                override val inject =
                    setOf(
                        InjectSpec.required(
                            llmKey
                        ),
                        InjectSpec.required(
                            toolsKey
                        )
                    )

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    // 两个依赖全部存在后才能执行。
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = context,
            )

        /*
         * ──────────────────────
         * 1. 两个 Service 都没有
         * ──────────────────────
         */

        assertEquals(
            false,
            fiber.start(),
        )

        assertEquals(
            FiberState.Pending,
            fiber.state,
        )

        /*
         * ──────────────────────
         * 2. 只有 LLM
         * ──────────────────────
         */

        context.provide(
            llmKey,
            object : TestService {},
        )

        assertEquals(
            false,
            fiber.start(),
        )

        assertEquals(
            listOf(toolsKey),
            fiber.missingDependencies,
        )

        /*
         * ──────────────────────
         * 3. Tools 也出现
         * ──────────────────────
         */

        context.provide(
            toolsKey,
            object : TestService {},
        )

        assertEquals(
            true,
            fiber.start(),
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )
    }

    /**
     * Plugin 通过 context.provide() 提供的 Service，
     * 应该随着 Plugin 停止自动消失。
     *
     * 这是我们当前 Effect / Disposable 设计
     * 第一次真正和 Service 生命周期结合。
     */
    @Test
    fun serviceProvidedByPluginShouldBeRemovedAfterStop() = runTest {

        val serviceKey =
            ServiceKey<TestService>("test")

        val context =
            Context()

        val service =
            object : TestService {}

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {

                    /**
                     * Plugin 启动时提供 Service。
                     *
                     * 注意：
                     *
                     * provide() 返回的 Disposable
                     * 必须加入当前 Plugin 的 scope。
                     */
                    scope.add(
                        context.provide(
                            key = serviceKey,
                            service = service,
                        )
                    )
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = context,
            )

        /*
         * ──────────────────────
         * Plugin 尚未启动
         * ──────────────────────
         */

        assertEquals(
            false,
            context.has(serviceKey),
        )

        /*
         * ──────────────────────
         * Plugin 启动
         * ──────────────────────
         */

        fiber.start()

        /**
         * Plugin.apply() 已经执行 provide，
         * 所以 Service 应该存在。
         */
        assertEquals(
            true,
            context.has(serviceKey),
        )

        assertEquals(
            FiberState.Active,
            fiber.state,
        )

        /*
         * ──────────────────────
         * Plugin 停止
         * ──────────────────────
         */

        fiber.stop()

        /**
         * stop()
         *
         * → EffectScope.dispose()
         *
         * → provide() 返回的 Disposable 被执行
         *
         * → Service 被删除
         */
        assertEquals(
            false,
            context.has(serviceKey),
        )

        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
    }

    /**
     * Plugin 启动到一半失败时，
     * 已经产生的副作用也必须撤销。
     *
     * 这是“可逆副作用”非常重要的一部分。
     */
    @Test
    fun providedServiceShouldBeRemovedWhenPluginStartFails() = runTest {

        val serviceKey =
            ServiceKey<TestService>("test")

        val context =
            Context()

        val plugin =
            object : SimplePlugin {

                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    /**
                     * 第一步成功提供 Service。
                     */
                    scope.add(
                        context.provide(
                            serviceKey,
                            object : TestService {},
                        )
                    )

                    /**
                     * 模拟后续初始化失败。
                     */
                    error("startup failed")
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = context,
            )

        try {
            fiber.start()
        } catch (_: IllegalStateException) {
            // 当前测试只关心清理结果。
        }

        /**
         * Plugin 已经启动失败。
         */
        assertEquals(
            FiberState.Failed,
            fiber.state,
        )

        /**
         * 即使 Service 曾经 provide 成功，
         * 也必须被清理掉。
         */
        assertEquals(
            false,
            context.has(serviceKey),
        )
    }

    /**
     * 启动失败后应该保留原始错误，并允许显式重试。
     * 第一次启动产生的副作用必须在第二次启动前完成回滚。
     */
    @Test
    fun failedFiberShouldRollbackAndRetry() = runTest {
        var attempts = 0
        var cleanups = 0

        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    attempts++
                    scope.add {
                        cleanups++
                    }

                    if (attempts == 1) {
                        error("first attempt")
                    }
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        val firstFailure =
            assertFailsWith<IllegalStateException> {
                fiber.start()
            }

        assertEquals(
            FiberState.Failed,
            fiber.state,
        )
        assertSame(
            firstFailure,
            fiber.failure,
        )
        assertEquals(
            1,
            cleanups,
        )

        assertTrue(
            fiber.retry()
        )
        assertEquals(
            FiberState.Active,
            fiber.state,
        )
        assertNull(
            fiber.failure
        )
        assertEquals(
            2,
            attempts,
        )

        fiber.stop()

        assertEquals(
            2,
            cleanups,
        )
    }

    /**
     * apply 与 rollback 同时失败时，apply 错误仍是主错误，
     * rollback 错误作为 suppressed error 保留，状态不能卡在 Loading。
     */
    @Test
    fun rollbackFailureShouldNotMaskStartupFailure() = runTest {
        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add {
                        error("rollback failed")
                    }
                    error("startup failed")
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        val failure =
            assertFailsWith<IllegalStateException> {
                fiber.start()
            }

        assertEquals(
            "startup failed",
            failure.message,
        )
        assertEquals(
            listOf("rollback failed"),
            failure.suppressedExceptions.map { it.message },
        )
        assertEquals(
            FiberState.Failed,
            fiber.state,
        )
        assertSame(
            failure,
            fiber.failure,
        )
    }

    /**
     * deactivate 清理失败时必须离开 Unloading，
     * 并把错误记录为可观察的 Failed 状态。
     */
    @Test
    fun deactivateFailureShouldLeaveStableFailedState() = runTest {
        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add {
                        error("deactivate failed")
                    }
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        fiber.start()

        val failure =
            assertFailsWith<IllegalStateException> {
                fiber.deactivate()
            }

        assertEquals(
            FiberState.Failed,
            fiber.state,
        )
        assertSame(
            failure,
            fiber.failure,
        )

        fiber.stop()

        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
    }

    /**
     * stop 清理失败仍然是永久终态；重复 stop 必须保持幂等。
     */
    @Test
    fun stopFailureShouldStillDisposeAndRemainIdempotent() = runTest {
        val plugin =
            object : SimplePlugin {
                override suspend fun apply(
                    context: Context,
                    scope: EffectScope,
                ) {
                    scope.add {
                        error("stop failed")
                    }
                }
            }

        val fiber =
            Fiber(
                plugin = plugin,
                config = Unit,
                context = Context(),
            )

        fiber.start()

        val failure =
            assertFailsWith<IllegalStateException> {
                fiber.stop()
            }

        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
        assertSame(
            failure,
            fiber.failure,
        )

        fiber.stop()

        assertEquals(
            FiberState.Disposed,
            fiber.state,
        )
    }
}
