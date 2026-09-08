package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.service.ServiceKey

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
     * 尝试启动 Plugin。
     *
     *
     * 返回：
     *
     * true
     *     =
     *     成功启动
     *
     * false
     *     =
     *     required dependency 尚未满足
     *
     *
     * 状态变化：
     *
     * Pending
     *    ↓
     * Loading
     *    ↓
     * Active
     *
     *
     * apply() 抛异常：
     *
     * Loading
     *    ↓
     * Failed
     */
    suspend fun start(): Boolean {
        return context.mutate {
            startNow()
        }
    }

    private suspend fun startNow(): Boolean {

        check(state == FiberState.Pending) {
            "Plugin cannot start from state $state"
        }

        /**
         * required dependency 不满足，
         * 保持 Pending。
         */
        if (!canStart) {
            return false
        }

        failure =
            null

        /**
         * 在真正 apply() 之前，
         * 记录当前 dependency environment。
         *
         * Plugin 生命周期就是在这个环境下启动的。
         */
        dependencySnapshot =
            currentDependencySnapshot()

        state =
            FiberState.Loading

        try {

            plugin.apply(
                context = context,
                config = config,
                scope = scope,
            )

            state =
                FiberState.Active

            return true
        } catch (error: Throwable) {

            /**
             * Plugin 启动到一半失败，
             * 之前产生的副作用必须全部撤销。
             */
            var rollbackFailure: Throwable? =
                null

            try {
                scope.dispose()
            } catch (cleanupError: Throwable) {
                rollbackFailure =
                    cleanupError
            }

            scope =
                EffectScope()

            dependencySnapshot =
                emptyMap()

            failure =
                error

            state =
                FiberState.Failed

            if (
                rollbackFailure != null &&
                rollbackFailure !== error
            ) {
                error.addSuppressed(
                    rollbackFailure
                )
            }

            throw error
        }
    }

    /**
     * 显式重试一个启动失败的 Fiber。
     *
     * required Service 仍缺失时会回到 Pending 并返回 false；
     * apply 再次失败时仍进入 Failed 并抛出本次异常。
     */
    suspend fun retry(): Boolean {
        return context.mutate {
            retryNow()
        }
    }

    private suspend fun retryNow(): Boolean {
        check(state == FiberState.Failed) {
            "Plugin cannot retry from state $state"
        }

        failure =
            null

        state =
            FiberState.Pending

        return startNow()
    }

    /**
     * 暂时停用 Plugin。
     *
     *
     * 和 stop() 最大区别：
     *
     * deactivate：
     *
     * Active
     *    ↓
     * Pending
     *
     * 未来 dependency 恢复后
     * 还可以重新启动。
     *
     *
     * stop：
     *
     * 最终进入 Disposed，
     * 永久结束。
     */
    suspend fun deactivate() {
        context.mutate {
            deactivateNow()
        }
    }

    private suspend fun deactivateNow() {

        if (state != FiberState.Active) {
            return
        }

        state =
            FiberState.Unloading

        /**
         * 撤销 Plugin 当前生命周期
         * 产生的所有 side effects。
         */
        var cleanupFailure: Throwable? =
            null

        try {
            scope.dispose()
        } catch (error: Throwable) {
            cleanupFailure =
                error
        }

        /**
         * 下一次启动需要全新的 scope。
         */
        scope =
            EffectScope()

        /**
         * 上一次 dependency environment
         * 已经失效。
         */
        dependencySnapshot =
            emptyMap()

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

            throw cleanupFailure
        }
    }

    /**
     * 永久停止 Fiber。
     *
     * stop 之后：
     *
     * state == Disposed
     *
     * Runtime 不应该再重新启动这个 Instance。
     */
    suspend fun stop() {
        context.mutate {
            stopNow()
        }
    }

    private suspend fun stopNow() {

        when (state) {

            FiberState.Active -> {

                state =
                    FiberState.Unloading
            }

            FiberState.Pending,
            FiberState.Failed -> {
                // 统一在 when 后完成清理。
            }

            FiberState.Loading,
            FiberState.Unloading -> {

                error(
                    "Plugin cannot be stopped while state is $state"
                )
            }

            FiberState.Disposed -> {
                // 已经永久停止，不需要重复处理。
                return
            }
        }

        var cleanupFailure: Throwable? =
            null

        try {
            scope.dispose()
        } catch (error: Throwable) {
            cleanupFailure =
                error
        }

        scope =
            EffectScope()

        dependencySnapshot =
            emptyMap()

        failure =
            cleanupFailure

        state =
            FiberState.Disposed

        cleanupFailure?.let { error ->
            throw error
        }
    }
}
