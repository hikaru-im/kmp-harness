package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.effect.runDisposablesInReverse
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.CompletableDeferred

/**
 * 一个 Plugin 实际安装到 Runtime 中以后，
 * 对应的运行实例。
 *
 *
 * 可以大致对应 Cordis 中的 Fiber。
 *
 *
 * Plugin：
 *
 * 定义“怎么运行”。
 *
 *
 * Fiber：
 *
 * 表示：
 *
 * - 这一次安装
 * - 当前 Config
 * - 当前 Context
 * - 当前生命周期状态
 * - 当前 dependency snapshot
 * - 当前 EffectScope
 */
class Fiber<C : Any>(

    /**
     * 当前 Fiber 唯一 ID。
     *
     * 同一个 Plugin 安装两次：
     *
     * Plugin
     * ├── Fiber #1
     * └── Fiber #2
     */
    val id: Long = 0L,

    /**
     * Plugin 定义。
     */
    val plugin: Plugin<C>,

    /**
     * 当前实例自己的配置。
     */
    val config: C,

    /**
     * 当前 Fiber 自己的 Context。
     *
     * Context 决定：
     *
     * - parent
     * - isolate
     * - ServiceSlot Resolution
     */
    val context: Context,
) {

    /**
     * 当前生命周期产生的所有副作用。
     *
     * deactivate() 以后会 dispose，
     * 然后创建一个新的 EffectScope。
     */
    private var scope =
        EffectScope()

    /**
     * 当前 Fiber 生命周期状态。
     */
    var state: FiberState =
        FiberState.Pending
        private set

    /**
     * 最近一次生命周期失败。
     *
     * Failed 状态下保存启动或卸载失败；成功重试或永久停止后清空。
     */
    var failure: Throwable? =
        null
        private set

    /**
     * Plugin 上一次成功启动时，
     * 每一个 dependency 实际对应的 Binding ID。
     *
     *
     * 注意现在 value 是：
     *
     * Long?
     *
     * 而不是以前的 Long。
     *
     *
     * 为什么？
     *
     * 因为 optional dependency
     * 启动时允许不存在。
     *
     *
     * 例如：
     *
     * dependencies:
     *
     * LlmKey
     *     required
     *
     * ToolsKey
     *     optional
     *
     *
     * 启动时：
     *
     * LlmKey
     *     -> Binding #10
     *
     * ToolsKey
     *     -> null
     *
     *
     * snapshot：
     *
     * {
     *     LlmKey   -> 10
     *     ToolsKey -> null
     * }
     *
     *
     * 后来 Tools 出现：
     *
     * ToolsKey -> Binding #20
     *
     * snapshot：
     *
     * null != 20
     *
     * Runtime 就知道 Plugin 需要 reload。
     */
    private var dependencySnapshot:
            Map<ServiceKey<*>, Long?> = emptyMap()

    init {

        /**
         * 同一个 ServiceKey
         * 不应该同时声明两次 dependency。
         *
         *
         * 例如这是错误的：
         *
         * required(LlmKey)
         * optional(LlmKey)
         *
         *
         * 因为 Runtime 无法判断：
         *
         * LlmKey 到底是 required 还是 optional？
         */
        val duplicatedKeys =
            plugin.inject
                .groupBy { dependency ->
                    dependency.key
                }
                .filterValues { dependencies ->
                    dependencies.size > 1
                }
                .keys

        check(duplicatedKeys.isEmpty()) {
            val names =
                duplicatedKeys
                    .joinToString {
                        it.name
                    }

            "Plugin contains duplicated dependencies: $names"
        }
    }

    /**
     * 当前缺少的“必需依赖”。
     *
     *
     * optional dependency
     * 不会出现在这里。
     *
     *
     * 例如：
     *
     * required LlmKey  不存在
     * optional ToolsKey 不存在
     *
     * missingDependencies：
     *
     * [LlmKey]
     */
    val missingDependencies:
            List<ServiceKey<*>>
        get() =
            plugin.inject
                .asSequence()
                .filter { dependency ->
                    dependency.required
                }
                .map { dependency ->
                    dependency.key
                }
                .filterNot { key ->
                    context.has(key)
                }
                .toList()

    /**
     * 当前 Plugin 是否满足启动条件。
     *
     *
     * 只有 required dependencies
     * 决定 canStart。
     *
     *
     * optional dependency 不存在：
     *
     * canStart 仍然可以是 true。
     */
    val canStart: Boolean
        get() =
            missingDependencies.isEmpty()

    /**
     * dependency 是否发生变化。
     *
     *
     * 这里会检查：
     *
     * required
     * +
     * optional
     *
     * 所有 dependency。
     *
     *
     * required：
     *
     * Binding #10 -> Binding #20
     *     => changed
     *
     *
     * optional：
     *
     * null -> Binding #20
     *     => changed
     *
     * Binding #20 -> null
     *     => changed
     *
     * Binding #20 -> Binding #30
     *     => changed
     *
     *
     * 注意：
     *
     * required dependency 消失时，
     * Runtime 会优先通过 canStart == false
     * 处理成 Pending。
     *
     * dependenciesChanged 主要处理：
     *
     * “Plugin 仍然能运行，
     *  但 dependency environment 变化了。”
     */
    internal val dependenciesChanged: Boolean
        get() {

            /**
             * required dependency 已经缺失，
             * 这种情况直接由 canStart 处理。
             */
            if (!canStart) {
                return false
            }

            return plugin.inject.any { dependency ->

                val key =
                    dependency.key

                val previousId =
                    dependencySnapshot[key]

                val currentId =
                    context.bindingId(key)

                previousId != currentId
            }
        }

    /**
     * 捕获当前完整 dependency environment。
     *
     *
     * required dependency：
     *
     * 启动前一定存在。
     *
     *
     * optional dependency：
     *
     * 可以得到 null。
     */
    private fun currentDependencySnapshot():
            Map<ServiceKey<*>, Long?> {

        return plugin.inject
            .associate { dependency ->

                val key =
                    dependency.key

                key to context.bindingId(key)
            }
    }

    /**
     * 当前正在进行的可挂起生命周期操作。
     *
     * 只有锁内的同步提交会设置/清除它，因此 Loading/Unloading 时必非空。
     * 并发的 stop 会等待它，避免与进行中的 start/deactivate 竞争。
     */
    private var inFlight:
            CompletableDeferred<Unit>? = null

    private sealed interface StopDecision {

        data object Done : StopDecision

        data object Wait : StopDecision

        data class Drive(
            val pending: List<Disposable>,
        ) : StopDecision
    }

    /**
     * 尝试启动 Plugin。
     *
     * 状态变化：
     *
     * Pending → Loading → Active
     *
     * apply() 抛异常：
     *
     * Loading → Failed
     *
     * apply 与 rollback cleanup 都在 Runtime 状态锁之外执行。
     */
    suspend fun start(): Boolean {

        val proceed =
            context.mutate {
                check(state == FiberState.Pending) {
                    "Plugin cannot start from state $state"
                }

                if (!canStart) {
                    false
                } else {
                    failure =
                        null

                    dependencySnapshot =
                        currentDependencySnapshot()

                    state =
                        FiberState.Loading

                    inFlight =
                        CompletableDeferred()

                    true
                }
            }

        if (!proceed) {
            return false
        }

        runStart()

        return true
    }

    private suspend fun runStart() {

        var startupError: Throwable? =
            null

        try {
            plugin.apply(
                context = context,
                config = config,
                scope = scope,
            )
        } catch (error: Throwable) {
            startupError =
                error
        }

        if (startupError == null) {
            completeInFlight {
                state =
                    FiberState.Active
            }

            return
        }

        /**
         * 启动到一半失败。
         *
         * 先在锁内封存当前 scope，再在锁外执行 rollback cleanup。
         */
        val pending =
            context.mutate {
                val pendingScope =
                    scope.seal()

                scope =
                    EffectScope()

                dependencySnapshot =
                    emptyMap()

                pendingScope
            }

        var rollbackFailure: Throwable? =
            null

        try {
            runDisposablesInReverse(
                pending
            )
        } catch (cleanupError: Throwable) {
            rollbackFailure =
                cleanupError
        }

        context.mutate {
            failure =
                startupError

            state =
                FiberState.Failed
        }

        completeInFlight()

        if (
            rollbackFailure != null &&
            rollbackFailure !== startupError
        ) {
            startupError.addSuppressed(
                rollbackFailure
            )
        }

        throw startupError
    }

    /**
     * 显式重试一个启动失败的 Fiber。
     *
     * required Service 仍缺失时会回到 Pending 并返回 false；
     * apply 再次失败时仍进入 Failed 并抛出本次异常。
     */
    suspend fun retry(): Boolean {

        context.mutate {
            check(state == FiberState.Failed) {
                "Plugin cannot retry from state $state"
            }

            failure =
                null

            state =
                FiberState.Pending
        }

        return start()
    }

    /**
     * 暂时停用 Plugin。
     *
     * Active → Unloading → Pending
     *
     * cleanup 失败时：
     *
     * Unloading → Failed
     */
    suspend fun deactivate() {

        val pending: List<Disposable>? =
            context.mutate {
                if (state != FiberState.Active) {
                    return@mutate null
                }

                state =
                    FiberState.Unloading

                val pendingScope =
                    scope.seal()

                scope =
                    EffectScope()

                dependencySnapshot =
                    emptyMap()

                inFlight =
                    CompletableDeferred()

                pendingScope
            }

        if (pending == null) {
            return
        }

        var cleanupFailure: Throwable? =
            null

        try {
            runDisposablesInReverse(
                pending
            )
        } catch (error: Throwable) {
            cleanupFailure =
                error
        }

        completeInFlight {
            if (cleanupFailure == null) {
                failure =
                    null

                state =
                    FiberState.Pending
            } else {
                failure =
                    cleanupFailure

                state =
                    FiberState.Failed
            }
        }

        cleanupFailure?.let { error ->
            throw error
        }
    }

    /**
     * 永久停止 Fiber。
     *
     * stop 之后 state == Disposed。
     *
     * 若存在正在进行的 start/deactivate，先等待它提交完成，
     * 再执行销毁；已经 Disposed 的重复 stop 是无操作。
     */
    suspend fun stop() {

        while (true) {

            val decision =
                context.mutate {
                    when (state) {

                        FiberState.Disposed ->
                            StopDecision.Done

                        FiberState.Loading,
                        FiberState.Unloading ->
                            StopDecision.Wait

                        else -> {
                            if (state == FiberState.Active) {
                                state =
                                    FiberState.Unloading
                            }

                            val pendingScope =
                                scope.seal()

                            scope =
                                EffectScope()

                            dependencySnapshot =
                                emptyMap()

                            inFlight =
                                CompletableDeferred()

                            StopDecision.Drive(
                                pendingScope
                            )
                        }
                    }
                }

            when (decision) {

                StopDecision.Done ->
                    return

                StopDecision.Wait -> {
                    val wait =
                        context.mutate {
                            inFlight
                        }

                    wait?.await()
                }

                is StopDecision.Drive -> {

                    var cleanupFailure: Throwable? =
                        null

                    try {
                        runDisposablesInReverse(
                            decision.pending
                        )
                    } catch (error: Throwable) {
                        cleanupFailure =
                            error
                    }

                    completeInFlight {
                        failure =
                            cleanupFailure

                        state =
                            FiberState.Disposed
                    }

                    cleanupFailure?.let { error ->
                        throw error
                    }

                    return
                }
            }
        }
    }

    /**
     * 在锁内提交最终状态并唤醒等待者。
     */
    private suspend fun completeInFlight(
        commit: (() -> Unit)? = null,
    ) {
        val signal =
            context.mutate {
                commit?.invoke()

                val current =
                    inFlight

                inFlight =
                    null

                current
            }

        signal?.complete(Unit)
    }
}
