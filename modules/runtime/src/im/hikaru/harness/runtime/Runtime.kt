package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.Fiber
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.runtime.plugin.InstallResult
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.runtime.service.ServiceSlot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

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
    private var reconcileSignal:
            CompletableDeferred<Unit>? = null

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
        }

        /**
         * Context Scope dispose 时，Runtime 先在锁内同步认领并移除
         * 这个 Context 子树中管理的全部 Fiber，再把停止动作交给锁外执行。
         */
        core.bindContextDisposingHandler { disposingContext ->
            claimFibersUnder(
                disposingContext
            )
        }

        /**
         * 每次同步状态提交结束后，在锁外处理待刷新队列。
         */
        core.bindPostMutationHandler {
            reconcileIfIdle()
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

        val prepared =
            core.mutate {
                prepareInstall(
                    plugin = plugin,
                    config = config,
                    parent = parent,
                )
            }

        val fiber =
            prepared.fiber

        if (fiber == null) {
            val error =
                prepared.error!!

            try {
                prepared.orphan?.dispose()
            } catch (rollbackError: Throwable) {
                if (rollbackError !== error) {
                    error.addSuppressed(
                        rollbackError
                    )
                }
            }

            throw error
        }

        var failure: Throwable? =
            null

        if (!core.isInsideOperation()) {
            try {
                reconcile()
            } catch (error: Throwable) {
                failure =
                    error
            }
        }

        return InstallResult(
            fiber = fiber,
            failure = failure,
        )
    }

    /**
     * 安装准备的结果。
     */
    private class InstallPreparation<C : Any>(
        val fiber: Fiber<C>?,
        val orphan: Context?,
        val error: Throwable?,
    )

    /**
     * 在锁内同步完成 Fiber 的创建、登记与依赖索引。
     *
     * 真正的 apply 由随后的 reconcile 在锁外执行。
     */
    private fun <C : Any> prepareInstall(
        plugin: Plugin<C>,
        config: C,
        parent: Context,
    ): InstallPreparation<C> {

        check(parent.root === context) {
            "Parent context does not belong to this Runtime"
        }

        check(!parent.isDisposing && !parent.isDisposed) {
            "Parent context is disposed"
        }

        val instanceId =
            nextFiberId++

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
                return InstallPreparation(
                    fiber = null,
                    orphan = pluginContext,
                    error = error,
                )
            }

        mutableFibers +=
            fiber

        registerDependencies(
            fiber
        )

        pendingRefresh +=
            fiber

        return InstallPreparation(
            fiber = fiber,
            orphan = null,
            error = null,
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

        val managed =
            core.mutate {
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

                    false
                } else {
                    true
                }
            }

        if (!managed) {
            return
        }

        val nested =
            core.isInsideOperation()

        withContext(
            RuntimeOperationMarker(core)
        ) {
            fiber.context.dispose()

            if (nested) {
                reconcileIfIdle()
            } else {
                reconcile()
            }
        }
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

        core.mutate {
            check(fiber.context.root === context) {
                "Plugin does not belong to this Runtime"
            }

            check(fiber in mutableFibers) {
                "Fiber is not managed by this Runtime"
            }
        }

        return withContext(
            RuntimeOperationMarker(core)
        ) {
            fiber.retry()
        }
    }

    /**
     * Context 子树销毁前，先把其中的 Plugin 全部从 Runtime 管理结构移除，
     * 再按 Context 深度从叶子到根停止。
     *
     * 先统一 unregister 很重要：Plugin stop 导致 Service 消失时，
     * 即将销毁的 sibling / descendant 不会重新进入 refresh queue。
     */
    private fun claimFibersUnder(
        disposingContext: Context,
    ): List<suspend () -> Unit> {

        val targets =
            mutableFibers
                .filter { fiber ->
                    fiber.context.isSameOrDescendantOf(
                        disposingContext
                    )
                }

        if (targets.isEmpty()) {
            return emptyList()
        }

        for (fiber in targets) {
            unregisterDependencies(
                fiber
            )
        }

        mutableFibers.removeAll(
            targets.toSet()
        )

        return targets
            .sortedWith(
                compareByDescending<Fiber<*>> { fiber ->
                    fiber.context.depth
                }.thenByDescending { fiber ->
                    fiber.id
                }
            )
            .map { fiber ->
                suspend { fiber.stop() }
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

        val current =
            context.binding(key)
                ?: error(
                    "Service '${key.name}' is not provided"
                )

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
     * 在锁外处理定向刷新队列。
     *
     * 同一 Runtime 同时只有一个 drain 循环；其它调用者在锁内登记后等待该轮结束。
     */
    private suspend fun reconcile() {

        while (true) {

            var signal: CompletableDeferred<Unit>? =
                null

            val isRunner =
                core.mutate {
                    if (reconcileSignal == null) {
                        val created =
                            CompletableDeferred<Unit>()

                        reconcileSignal =
                            created

                        signal =
                            created

                        true
                    } else {
                        false
                    }
                }

            if (isRunner) {
                drain(
                    signal!!
                )

                return
            }

            val active =
                core.mutate {
                    reconcileSignal
                }

            active?.await()

            val remaining =
                core.mutate {
                    pendingRefresh.isNotEmpty()
                }

            if (!remaining) {
                return
            }
        }
    }

    /**
     * 只在当前没有 drain 循环时立刻接管队列。
     *
     * 与 reconcile 不同，这里不等待正在进行的 drain，
     * 因此不会和“正在进行、且等待本次提交”的生命周期操作形成等待环。
     */
    private suspend fun reconcileIfIdle() {

        var signal: CompletableDeferred<Unit>? =
            null

        val isRunner =
            core.mutate {
                if (reconcileSignal == null) {
                    val created =
                        CompletableDeferred<Unit>()

                    reconcileSignal =
                        created

                    signal =
                        created

                    true
                } else {
                    false
                }
            }

        if (isRunner) {
            drain(
                signal!!
            )
        }
    }

    /**
     * 锁外逐个执行刷新；每个 refresh 携带操作标记，
     * 使其内部触发的同步提交不再递归等待刷新。
     */
    private suspend fun drain(
        signal: CompletableDeferred<Unit>,
    ) {
        try {
            while (true) {

                val next =
                    core.mutate {
                        var chosen: Fiber<*>? =
                            null

                        while (pendingRefresh.isNotEmpty()) {
                            val fiber =
                                pendingRefresh.first()

                            pendingRefresh.remove(
                                fiber
                            )

                            if (fiber in mutableFibers) {
                                chosen =
                                    fiber

                                break
                            }
                        }

                        chosen
                    }

                if (next == null) {
                    break
                }

                withContext(
                    RuntimeOperationMarker(core)
                ) {
                    refresh(
                        next
                    )
                }
            }
        } finally {

            core.mutate {
                reconcileSignal =
                    null
            }

            signal.complete(
                Unit
            )
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
