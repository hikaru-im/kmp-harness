package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.Fiber
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.runtime.plugin.InstallResult
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.runtime.service.ServiceSlot

/**
 * 整个 Plugin Runtime 的核心管理器。
 *
 *
 * Runtime 负责：
 *
 * - RootContext
 * - Plugin install / uninstall
 * - Fiber 生命周期
 * - dependency index
 * - ServiceSlot change notification
 * - 定向 refresh
 *
 *
 * 当前 dependency 模型已经支持：
 *
 * required dependency
 * optional dependency
 *
 *
 * Service 变化链路：
 *
 * Context
 *      ↓
 * notifyServiceChanged(slot)
 *      ↓
 * RuntimeCore
 *      ↓
 * dependencyIndex[slot]
 *      ↓
 * pendingRefresh
 *      ↓
 * refresh(fiber)
 */
class Runtime {

    /**
     * 当前 Runtime 独享的共享状态。
     */
    private val core =
        RuntimeCore()

    /**
     * Runtime RootContext。
     */
    val context: Context =
        Context.root(core)

    /**
     * Runtime 当前管理的所有 Fiber。
     */
    private val mutableFibers =
        mutableListOf<Fiber<*>>()

    /**
     * 对外只读 Fiber 列表。
     */
    val fibers: List<Fiber<*>>
        get() = mutableFibers.toList()

    /**
     * ServiceSlot -> 依赖这个 Slot 的 Fiber。
     *
     *
     * 注意：
     *
     * required dependency
     * 和
     * optional dependency
     *
     * 都必须注册到这个 index。
     *
     *
     * 为什么 optional 也要注册？
     *
     * 因为：
     *
     * optional Service 出现 / 消失 / 替换
     *
     * 都应该触发 Plugin reload。
     */
    private val dependencyIndex =
        mutableMapOf<
                ServiceSlot<*>,
                MutableSet<Fiber<*>>,
                >()

    /**
     * 等待重新检查生命周期的 Plugin。
     */
    private val pendingRefresh =
        linkedSetOf<Fiber<*>>()

    /**
     * Fiber ID 生成器。
     */
    private var nextFiberId =
        1L

    /**
     * 是否正在处理 refresh queue。
     *
     * 防止：
     *
     * refresh
     *   ↓
     * Plugin.start()
     *   ↓
     * context.provide()
     *   ↓
     * notify
     *   ↓
     * 再次递归 refresh
     */
    private var isReconciling =
        false

    init {

        /**
         * 某个 ServiceSlot 发生变化时：
         *
         * 找到真正依赖它的 Plugin，
         * 加入 pendingRefresh。
         *
         *
         * required / optional
         * 在这里没有区别。
         *
         * 区别只体现在 refresh 时：
         *
         * required 缺失
         *     → canStart == false
         *
         * optional 缺失
         *     → canStart 仍然为 true
         */
        core.bindServiceChangedHandler { slot ->

            val dependents =
                dependencyIndex[slot]
                    ?.toList()
                    .orEmpty()

            pendingRefresh +=
                dependents

            reconcile()
        }

        /**
         * Context Scope dispose 时，Runtime 负责先移除并停止
         * 这个 Context 子树中管理的全部 Fiber。
         */
        core.bindContextDisposingHandler { disposingContext ->
            disposePluginsUnder(
                disposingContext
            )
        }
    }

    /**
     * 安装带配置的 Plugin。
     *
     *
     * parent 默认是 Runtime RootContext。
     *
     * 也可以安装到 isolated Scope：
     *
     * runtime.install(
     *     plugin = agentPlugin,
     *     config = config,
     *     parent = researchScope,
     * )
     */
    suspend fun <C : Any> install(
        plugin: Plugin<C>,
        config: C,
        parent: Context = context,
    ): Fiber<C> {
        return installCatching(
            plugin = plugin,
            config = config,
            parent = parent,
        ).getOrThrow()
    }

    /**
     * 和 install 相同，但启动失败时返回 Failed Fiber 与原始错误，
     * 供 Loader 等管理层执行显式 rollback。
     */
    suspend fun <C : Any> installCatching(
        plugin: Plugin<C>,
        config: C,
        parent: Context = context,
    ): InstallResult<C> {
        return core.mutate {
            installNow(
                plugin = plugin,
                config = config,
                parent = parent,
            )
        }
    }

    private suspend fun <C : Any> installNow(
        plugin: Plugin<C>,
        config: C,
        parent: Context,
    ): InstallResult<C> {

        /**
         * Plugin 必须安装到当前 Runtime
         * 自己的 Context Tree。
         */
        check(parent.root === context) {
            "Parent context does not belong to this Runtime"
        }

        check(!parent.isDisposing && !parent.isDisposed) {
            "Parent context is disposed"
        }

        val instanceId =
            nextFiberId++

        /**
         * 每一个 Fiber
         * 都有自己的 Context。
         */
        val pluginContext =
            parent.child(
                ownerId = instanceId,
            )

        val fiber =
            try {
                Fiber(
                    id = instanceId,
                    plugin = plugin,
                    config = config,
                    context = pluginContext,
                )
            } catch (error: Throwable) {
                try {
                    pluginContext.dispose()
                } catch (rollbackError: Throwable) {
                    if (rollbackError !== error) {
                        error.addSuppressed(
                            rollbackError
                        )
                    }
                }

                throw error
            }

        mutableFibers +=
            fiber

        /**
         * required / optional dependencies
         * 全部建立 ServiceSlot index。
         */
        registerDependencies(
            fiber
        )

        /**
         * 新安装 Plugin 至少检查一次。
         *
         * 即使：
         *
         * inject == emptySet()
         *
         * 也需要从 Pending -> Active。
         */
        pendingRefresh +=
            fiber

        var failure: Throwable? =
            null

        try {
            reconcile()
        } catch (error: Throwable) {
            failure =
                error
        }

        return InstallResult(
            fiber = fiber,
            failure = failure,
        )
    }

    /**
     * 安装 SimplePlugin。
     */
    suspend fun install(
        plugin: SimplePlugin,
        parent: Context = context,
    ): Fiber<Unit> {
        return install(
            plugin = plugin,
            config = Unit,
            parent = parent,
        )
    }

    suspend fun installCatching(
        plugin: SimplePlugin,
        parent: Context = context,
    ): InstallResult<Unit> {
        return installCatching(
            plugin = plugin,
            config = Unit,
            parent = parent,
        )
    }

    /**
     * 永久卸载 Fiber。
     */
    suspend fun uninstall(
        fiber: Fiber<*>,
    ) {
        core.mutate {
            uninstallNow(fiber)
        }
    }

    private suspend fun uninstallNow(
        fiber: Fiber<*>,
    ) {

        check(fiber.context.root === context) {
            "Plugin does not belong to this Runtime"
        }

        if (fiber !in mutableFibers) {
            check(
                fiber.state == FiberState.Disposed &&
                    fiber.context.isDisposed
            ) {
                "Fiber is not managed by this Runtime"
            }

            return
        }

        /**
         * Fiber Context dispose 会通过 RuntimeCore 回调：
         *
         * - 从 Runtime 移除当前 Plugin
         * - 递归卸载它下面的 descendant Plugin
         * - 停止 lifecycle
         * - 清理 child Context 和 Context-owned effects
         */
        fiber.context.dispose()

        /**
         * 如果 stop() 过程中产生了新的刷新任务，
         * 确保它们被处理。
         */
        reconcile()
    }

    /**
     * 重试当前 Runtime 管理的失败 Fiber。
     *
     * 重试失败时 Fiber 会继续停留在 Failed 并抛出本次错误；
     * required Service 缺失时回到 Pending 并返回 false。
     */
    suspend fun retry(
        fiber: Fiber<*>,
    ): Boolean {
        return core.mutate {
            retryNow(fiber)
        }
    }

    private suspend fun retryNow(
        fiber: Fiber<*>,
    ): Boolean {
        check(fiber.context.root === context) {
            "Plugin does not belong to this Runtime"
        }

        check(fiber in mutableFibers) {
            "Fiber is not managed by this Runtime"
        }

        return fiber.retry()
    }

    /**
     * Context 子树销毁前，先把其中的 Plugin 全部从 Runtime 管理结构移除，
     * 再按 Context 深度从叶子到根停止。
     *
     * 先统一 unregister 很重要：Plugin stop 导致 Service 消失时，
     * 即将销毁的 sibling / descendant 不会重新进入 refresh queue。
     */
    private suspend fun disposePluginsUnder(
        disposingContext: Context,
    ) {
        val targets =
            mutableFibers
                .filter { fiber ->
                    fiber.context.isSameOrDescendantOf(
                        disposingContext
                    )
                }

        if (targets.isEmpty()) {
            return
        }

        for (fiber in targets) {
            unregisterDependencies(
                fiber
            )
        }

        mutableFibers.removeAll(
            targets.toSet()
        )

        val stopOrder =
            targets.sortedWith(
                compareByDescending<Fiber<*>> { fiber ->
                    fiber.context.depth
                }.thenByDescending { fiber ->
                    fiber.id
                }
            )

        var failure: Throwable? =
            null

        for (fiber in stopOrder) {
            try {
                fiber.stop()
            } catch (error: Throwable) {
                if (failure == null) {
                    failure =
                        error
                } else if (error !== failure) {
                    failure.addSuppressed(error)
                }
            }
        }

        try {
            reconcile()
        } catch (error: Throwable) {
            if (failure == null) {
                failure =
                    error
            } else if (error !== failure) {
                failure.addSuppressed(error)
            }
        }

        failure?.let { error ->
            throw error
        }
    }

    /**
     * 从 RootContext 提供 Service。
     */
    suspend fun <T : Any> provide(
        key: ServiceKey<T>,
        service: T,
    ): Disposable {
        return context.provide(
            key = key,
            service = service,
        )
    }

    /**
     * 替换 RootContext Service。
     */
    suspend fun <T : Any> replace(
        key: ServiceKey<T>,
        service: T,
    ): Disposable {

        return core.mutate {
            replaceNow(
                key = key,
                service = service,
            )
        }
    }

    private suspend fun <T : Any> replaceNow(
        key: ServiceKey<T>,
        service: T,
    ): Disposable {

        val current =
            context.binding(key)
                ?: error(
                    "Service '${key.name}' is not provided"
                )

        /**
         * Runtime Root 不应该强行替换
         * Plugin 自己拥有的 Service。
         */
        check(current.ownerId == null) {
            "Service '${key.name}' is owned by plugin ${current.ownerId}"
        }

        return context.replace(
            key = key,
            service = service,
        )
    }

    /**
     * 注册 Plugin 的所有 dependency。
     *
     *
     * 注意：
     *
     * required
     * optional
     *
     * 全部都必须进入 dependencyIndex。
     *
     *
     * optional 虽然不影响 canStart，
     * 但是它发生变化时需要触发 reload。
     */
    private fun registerDependencies(
        fiber: Fiber<*>,
    ) {

        for (
        dependency in fiber.plugin.inject
        ) {

            /**
             * InjectSpec 中真正的 ServiceKey。
             */
            val key =
                dependency.key

            /**
             * 必须使用当前 Fiber Context
             * 解析实际 ServiceSlot。
             *
             * isolate 后同一个 ServiceKey
             * 可能对应完全不同的 Slot。
             */
            val slot =
                fiber.context.serviceSlot(
                    key
                )

            dependencyIndex
                .getOrPut(slot) {
                    linkedSetOf()
                }
                .add(fiber)
        }
    }

    /**
     * Plugin 永久卸载时，
     * 删除它的 dependency index。
     */
    private fun unregisterDependencies(
        fiber: Fiber<*>,
    ) {

        for (
        dependency in fiber.plugin.inject
        ) {

            val key =
                dependency.key

            val slot =
                fiber.context.serviceSlot(
                    key
                )

            val dependents =
                dependencyIndex[slot]
                    ?: continue

            dependents.remove(
                fiber
            )

            if (dependents.isEmpty()) {
                dependencyIndex.remove(
                    slot
                )
            }
        }

        /**
         * 同时取消未处理的 refresh。
         */
        pendingRefresh.remove(
            fiber
        )
    }

    /**
     * 处理定向刷新队列。
     *
     * 不再扫描全部 fibers。
     */
    private suspend fun reconcile() {

        /**
         * 已经有人在处理 queue。
         *
         * 新的受影响 Plugin
         * 已经被加入 pendingRefresh，
         * 当前 while 后续自然会处理。
         */
        if (isReconciling) {
            return
        }

        isReconciling =
            true

        try {

            while (pendingRefresh.isNotEmpty()) {

                /**
                 * 取最早进入队列的 Plugin。
                 */
                val fiber =
                    pendingRefresh.first()

                pendingRefresh.remove(
                    fiber
                )

                /**
                 * 等待期间可能已经被 uninstall。
                 */
                if (fiber !in mutableFibers) {
                    continue
                }

                refresh(
                    fiber
                )
            }
        } finally {

            /**
             * 无论 Plugin 生命周期是否抛异常，
             * 都必须恢复状态。
             */
            isReconciling =
                false
        }
    }

    /**
     * 定向重新检查一个 Fiber。
     *
     *
     * required / optional dependency
     * 的核心区别在这里体现。
     */
    private suspend fun refresh(
        fiber: Fiber<*>,
    ) {

        when (fiber.state) {

            FiberState.Pending -> {

                /**
                 * canStart 只检查 required dependencies。
                 *
                 * 所以：
                 *
                 * required 缺失：
                 *
                 *     false
                 *
                 * optional 缺失：
                 *
                 *     仍然可能 true
                 */
                if (fiber.canStart) {
                    fiber.start()
                }
            }

            FiberState.Active -> {

                when {

                    /**
                     * required dependency 消失。
                     *
                     *
                     * Active
                     *    ↓
                     * Pending
                     *
                     *
                     * optional dependency 消失
                     * 不会进入这里，
                     * 因为 optional 不影响 canStart。
                     */
                    !fiber.canStart -> {

                        fiber.deactivate()
                    }

                    /**
                     * dependency environment 改变。
                     *
                     *
                     * 包括：
                     *
                     * required implementation 替换
                     *
                     * optional：
                     *
                     * null -> Binding
                     * Binding -> null
                     * Binding A -> Binding B
                     *
                     *
                     * Plugin 都需要重新 apply。
                     */
                    fiber.dependenciesChanged -> {

                        fiber.deactivate()

                        /**
                         * 如果只是 optional 发生变化：
                         *
                         * canStart 仍然为 true。
                         *
                         * 所以 Plugin 会立即重新启动。
                         */
                        if (fiber.canStart) {
                            fiber.start()
                        }
                    }
                }
            }

            FiberState.Loading,
            FiberState.Failed,
            FiberState.Unloading,
            FiberState.Disposed -> {
                // 当前不自动处理。
            }
        }
    }
}
