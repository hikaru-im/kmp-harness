package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.event.BailEventKey
import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.event.EventOptions
import im.hikaru.harness.runtime.event.EventsService
import im.hikaru.harness.runtime.event.ParallelEventKey
import im.hikaru.harness.runtime.event.PipelineEventKey
import im.hikaru.harness.runtime.event.SequentialEventKey
import im.hikaru.harness.runtime.event.SerialEventKey
import im.hikaru.harness.runtime.event.SuspendWaterfallEventKey
import im.hikaru.harness.runtime.event.WaterfallEventKey
import im.hikaru.harness.runtime.intercept.InterceptKey
import im.hikaru.harness.runtime.service.ServiceBinding
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.runtime.service.ServiceSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin

/**
 * Runtime 中的上下文。
 *
 * Context 可以理解成：
 *
 * “当前 Plugin / Scope 看待整个 Runtime 的视角。”
 *
 *
 * Context 自己并不真正保存 Service。
 *
 * 真正的共享基础设施保存在 RuntimeCore 中：
 *
 * RuntimeCore
 * ├── ServiceRegistry
 * ├── EventsService
 * └── Service Change Notification
 *
 *
 * Context 主要负责：
 *
 * - Context parent / root 关系
 * - Fiber Context identity
 * - Service Slot Resolution
 * - Service isolate
 * - Service provide / get / require
 *
 *
 * 整体结构例如：
 *
 * RootContext
 * │
 * ├── AgentAContext
 * │
 * └── ResearchScope
 *      │
 *      │ isolate(LlmKey)
 *      │
 *      └── AgentBContext
 *
 *
 * 所有 Context：
 *
 * - 共享同一个 RuntimeCore
 * - 共享同一个 ServiceRegistry
 * - 共享同一个 EventsService
 *
 * 但是可以通过 ServiceSlot
 * 对某个 ServiceKey 使用不同的 Service。
 */
class Context internal constructor(

    /**
     * 当前 Context 的父 Context。
     *
     * RootContext：
     *
     * parent == null
     *
     * 普通 Fiber Context：
     *
     * parent == Runtime RootContext
     *
     * isolate 后：
     *
     * RootContext
     *     ↓
     * IsolatedContext
     *     ↓
     * Fiber Context
     */
    val parent: Context?,

    /**
     * 当前 Context 所属 Fiber 的 ID。
     *
     * RootContext：
     *
     * ownerId == null
     *
     * Fiber Context：
     *
     * ownerId == Fiber.id
     *
     *
     * 一个 Fiber Context 再创建普通 child 时，
     * 默认继续继承 ownerId。
     */
    internal val ownerId: Long?,

    /**
     * 整棵 Context Tree 共享的内部 Runtime 状态。
     *
     * 所有 child / isolate Context
     * 都必须共享同一个 RuntimeCore。
     */
    internal val core: RuntimeCore,

    /**
     * 当前 Context 自己定义的 Service isolation。
     *
     * 普通 Context：
     *
     * emptyMap()
     *
     *
     * isolate(LlmKey) 后：
     *
     * {
     *     LlmKey -> 独立的 ServiceSlot
     * }
     *
     *
     * 注意：
     *
     * isolate 并不是创建新的 ServiceRegistry，
     * 而只是改变这个 Context 对某个 ServiceKey
     * 应该解析到哪个 ServiceSlot。
     */
    private val isolatedSlots:
    Map<ServiceKey<*>, ServiceSlot<*>>,

    /** 当前 Context 这一层新增的 intercept 配置。 */
    private val localIntercepts:
    Map<InterceptKey<*>, Any>,
) {

    /**
     * 当前 Context 直接拥有的 child Context。
     *
     * child / isolate / Fiber Context 都会进入这棵树，
     * 从而允许一个 Scope 被整体递归销毁。
     */
    private val children =
        mutableListOf<Context>()

    /**
     * 生命周期与当前 Context 一致的副作用。
     */
    private val effects =
        EffectScope()

    private var disposing =
        false

    /**
     * Context 是否已经永久销毁。
     */
    var isDisposed: Boolean =
        false
        private set

    internal val isDisposing: Boolean
        get() = disposing

    init {
        parent?.attachChild(this)
    }

    /**
     * 给普通代码 / 测试使用的便利构造器。
     *
     *
     * Context()
     *
     * 会创建一棵新的独立 Context Tree。
     *
     *
     * Context(parent = root)
     *
     * 会自动加入 root 所在的 Context Tree，
     * 因此共享：
     *
     * - RuntimeCore
     * - ServiceRegistry
     * - EventsService
     */
    constructor(
        parent: Context? = null,
    ) : this(
        parent = parent,

        /**
         * 普通 child 默认继承 parent 的 owner。
         */
        ownerId = parent?.ownerId,

        /**
         * 有 parent：
         *
         * 继承 parent.core。
         *
         * 没有 parent：
         *
         * 创建新的 RuntimeCore。
         */
        core =
            parent?.core
                ?: RuntimeCore(),

        /**
         * 普通 child 默认没有新的 isolation。
         */
        isolatedSlots =
            emptyMap(),

        localIntercepts =
            emptyMap(),
    )

    /**
     * 返回当前 Context Tree 最顶部的 RootContext。
     *
     * 例如：
     *
     * Root
     *   ↓
     * Scope
     *   ↓
     * Fiber Context
     *
     * 无论在哪一层：
     *
     * context.root
     *
     * 最终都是 Root。
     */
    val root: Context
        get() = parent?.root ?: this

    /**
     * 整个 Runtime 共享的 EventsService。
     *
     * EventsService 实际保存在 RuntimeCore 中。
     *
     * 因此：
     *
     * root.events === child.events
     */
    val events: EventsService
        get() {
            checkActive()
            return core.events
        }

    /**
     * 让 Fiber 与 Runtime 复用当前 Context Tree 的串行 mutation lane。
     */
    internal suspend fun <T> mutate(
        block: suspend () -> T,
    ): T {
        return core.mutate(block)
    }

    /**
     * 把一个副作用交给当前 Context 管理。
     *
     * Context dispose 时会按照注册顺序的反方向统一释放。
     */
    fun effect(
        disposable: Disposable,
    ): Disposable {
        checkActive()
        effects.add(disposable)
        return disposable
    }

    /**
     * lambda 形式的 Context-owned effect。
     */
    fun effect(
        dispose: suspend () -> Unit,
    ): Disposable {
        return effect(
            Disposable {
                dispose()
            }
        )
    }

    /**
     * Creates a SupervisorJob owned by this Context.
     *
     * The scope is cancelled and joined when the Context is disposed, which
     * keeps background work from outliving the Context that created it.
     */
    fun managedScope(): CoroutineScope {
        checkActive()
        val scope = CoroutineScope(SupervisorJob())
        try {
            effect {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        } catch (error: Throwable) {
            scope.coroutineContext[Job]?.cancel()
            throw error
        }
        return scope
    }

    /**
     * 注册一个生命周期属于当前 Context 的 Event listener。
     */
    fun <T : Any> on(
        key: EventKey<T>,
        options: EventOptions = EventOptions(),
        listener: (T) -> Unit,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> once(
        key: EventKey<T>,
        options: EventOptions = EventOptions(),
        listener: (T) -> Unit,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> on(
        key: ParallelEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> Unit,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> once(
        key: ParallelEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> Unit,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> on(
        key: SequentialEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> Unit,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> once(
        key: SequentialEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> Unit,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> on(
        key: SerialEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> R?,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> once(
        key: SerialEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> R?,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> on(
        key: BailEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: (T) -> R?,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> once(
        key: BailEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: (T) -> R?,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> on(
        key: WaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: (T, next: () -> R) -> R,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> once(
        key: WaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: (T, next: () -> R) -> R,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> on(
        key: SuspendWaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: suspend (T, next: suspend () -> R) -> R,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any, R : Any> once(
        key: SuspendWaterfallEventKey<T, R>,
        options: EventOptions = EventOptions(),
        listener: suspend (T, next: suspend () -> R) -> R,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> on(
        key: PipelineEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> T,
    ): Disposable {
        checkActive()

        return effect(
            core.events.on(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> once(
        key: PipelineEventKey<T>,
        options: EventOptions = EventOptions(),
        listener: suspend (T) -> T,
    ): Disposable {
        checkActive()

        return effect(
            core.events.once(
                key = key,
                options = options,
                listener = listener,
            )
        )
    }

    fun <T : Any> emit(
        key: EventKey<T>,
        event: T,
    ) {
        checkActive()
        core.events.emit(key, event)
    }

    /** Broadcasts to every listener while containing individual observer failures. */
    fun <T : Any> emitContained(
        key: EventKey<T>,
        event: T,
        onFailure: (Throwable) -> Unit = {},
    ) {
        checkActive()
        core.events.emitContained(
            key = key,
            event = event,
            onFailure = onFailure,
        )
    }

    suspend fun <T : Any> parallel(
        key: ParallelEventKey<T>,
        event: T,
    ) {
        checkActive()
        core.events.parallel(key, event)
    }

    suspend fun <T : Any> sequential(
        key: SequentialEventKey<T>,
        event: T,
    ) {
        checkActive()
        core.events.sequential(key, event)
    }

    suspend fun <T : Any, R : Any> serial(
        key: SerialEventKey<T, R>,
        event: T,
    ): R? {
        checkActive()
        return core.events.serial(key, event)
    }

    fun <T : Any, R : Any> bail(
        key: BailEventKey<T, R>,
        event: T,
    ): R? {
        checkActive()
        return core.events.bail(key, event)
    }

    fun <T : Any, R : Any> waterfall(
        key: WaterfallEventKey<T, R>,
        event: T,
        terminal: (T) -> R,
    ): R {
        checkActive()
        return core.events.waterfall(key, event, terminal)
    }

    suspend fun <T : Any, R : Any> waterfall(
        key: SuspendWaterfallEventKey<T, R>,
        event: T,
        terminal: suspend (T) -> R,
    ): R {
        checkActive()
        return core.events.waterfall(key, event, terminal)
    }

    suspend fun <T : Any> pipeline(
        key: PipelineEventKey<T>,
        initial: T,
    ): T {
        checkActive()
        return core.events.pipeline(key, initial)
    }

    /**
     * 创建普通 child Context。
     *
     * child：
     *
     * - 有自己的 Context identity
     * - 共享 RuntimeCore
     * - 共享 EventsService
     * - 共享 ServiceRegistry
     * - 不创建新的 Service isolation
     *
     *
     * Service Resolution 会继续沿着 parent 查找。
     */
    fun child(
        ownerId: Long? = this.ownerId,
    ): Context {
        checkActive()

        return Context(
            parent = this,
            ownerId = ownerId,
            core = core,
            isolatedSlots = emptyMap(),
            localIntercepts = emptyMap(),
        )
    }

    /**
     * 创建一个带局部 intercept 配置的 child Context。
     *
     * 多层同 key 配置按 root -> child 顺序交给 InterceptKey.merge。
     */
    fun <T : Any> intercept(
        key: InterceptKey<T>,
        config: T,
    ): Context {
        checkActive()

        return Context(
            parent = this,
            ownerId = ownerId,
            core = core,
            isolatedSlots = emptyMap(),
            localIntercepts = mapOf(key to config),
        )
    }

    /** 解析当前 Context 能看到的合并 intercept 配置。 */
    fun <T : Any> intercept(
        key: InterceptKey<T>,
    ): T? {
        checkActive()
        return key.resolve(intercepts(key))
    }

    /** 返回 root -> current 顺序的原始 intercept 配置链。 */
    fun <T : Any> intercepts(
        key: InterceptKey<T>,
    ): List<T> {
        checkActive()

        val result =
            parent?.intercepts(key)
                ?.toMutableList()
                ?: mutableListOf()

        val local =
            localIntercepts[key]

        if (local != null) {
            @Suppress("UNCHECKED_CAST")
            result +=
                local as T
        }

        return result
    }

    /**
     * 创建一个针对指定 ServiceKey
     * 使用独立 ServiceSlot 的 child Context。
     *
     *
     * 例如：
     *
     * RootContext:
     *
     * LlmKey -> DefaultSlot -> DeepSeek
     *
     *
     * val isolated =
     *     root.isolate(LlmKey)
     *
     *
     * isolated:
     *
     * LlmKey -> IsolatedSlot
     *
     *
     * 此时：
     *
     * isolated.get(LlmKey)
     *
     * 不会继续看到 Root 的 DeepSeek。
     *
     * isolated 可以自己：
     *
     * isolated.provide(
     *     LlmKey,
     *     OpenAi,
     * )
     *
     *
     * 最终：
     *
     * root.require(LlmKey)
     *     -> DeepSeek
     *
     * isolated.require(LlmKey)
     *     -> OpenAI
     */
    fun <T : Any> isolate(
        key: ServiceKey<T>,
    ): Context {

        checkActive()

        /**
         * 每一次 isolate 都创建一个新的 Slot。
         *
         * 即使两个 Slot 属于同一个 LlmKey，
         * 它们仍然是不同的 Service 空间。
         */
        val isolatedSlot =
            ServiceSlot(key)

        return Context(
            parent = this,
            ownerId = ownerId,
            core = core,

            /**
             * 当前 Context 只覆盖指定的 key。
             *
             * 其他 ServiceKey
             * 仍然继续沿 parent 解析。
             */
            isolatedSlots =
                mapOf(
                    key to isolatedSlot,
                ),
            localIntercepts = emptyMap(),
        )
    }

    /**
     * 找到当前 Context 对某个 ServiceKey
     * 实际应该使用哪个 ServiceSlot。
     *
     *
     * 查找顺序：
     *
     * 当前 Context
     *     ↓
     * 当前 isolate 过这个 Key 吗？
     *
     * Yes
     *     ↓
     * 使用当前 isolated Slot
     *
     * No
     *     ↓
     * 继续询问 parent
     *
     * 一直没有 isolation
     *     ↓
     * 使用 ServiceKey.defaultSlot
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolveSlot(
        key: ServiceKey<T>,
    ): ServiceSlot<T> {

        val localSlot =
            isolatedSlots[key]

        if (localSlot != null) {
            return localSlot as ServiceSlot<T>
        }

        return parent?.resolveSlot(key)
            ?: key.defaultSlot
    }

    /**
     * ServiceKey<*> 版本的 Slot Resolution。
     *
     * dependency / has() 等逻辑
     * 有时只知道：
     *
     * ServiceKey<*>
     *
     * 并不知道具体的 T。
     *
     * 所以内部统一通过这个方法处理。
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveAnySlot(
        key: ServiceKey<*>,
    ): ServiceSlot<*> {
        return resolveSlot(
            key as ServiceKey<Any>,
        )
    }

    /**
     * 获取当前 Context 对某个 ServiceKey
     * 实际解析到的 ServiceSlot。
     *
     * 这个方法主要供 Runtime 建立 dependencyIndex 使用。
     *
     *
     * 例如：
     *
     * AgentAContext:
     *
     * LlmKey -> DefaultSlot
     *
     *
     * ResearchAgentContext:
     *
     * LlmKey -> ResearchIsolatedSlot
     *
     *
     * 虽然两个 Plugin.inject
     * 都写的是 LlmKey，
     *
     * Runtime 最终会按照不同 ServiceSlot
     * 建立 dependency index。
     */
    internal fun serviceSlot(
        key: ServiceKey<*>,
    ): ServiceSlot<*> {
        return resolveAnySlot(key)
    }

    /**
     * 在当前 Context 对应的 ServiceSlot
     * 中提供一个 Service。
     *
     *
     * 为什么是 suspend？
     *
     * Service 注册本身并不需要 suspend。
     *
     * 但是注册成功以后：
     *
     * Service 出现
     *     ↓
     * notify Runtime
     *     ↓
     * reconcile
     *     ↓
     * 可能启动 / 停止其他 Plugin
     *
     * Plugin 生命周期本身是 suspend，
     * 所以 provide 也需要 suspend。
     *
     *
     * provide 返回 Disposable。
     *
     * 因为：
     *
     * “注册 Service”
     *
     * 本身是一种副作用，
     * 必须可以撤销。
     */
    suspend fun <T : Any> provide(
        key: ServiceKey<T>,
        service: T,
    ): Disposable {
        return mutate {
            provideNow(
                key = key,
                service = service,
            )
        }
    }

    private suspend fun <T : Any> provideNow(
        key: ServiceKey<T>,
        service: T,
    ): Disposable {

        checkActive()

        /**
         * 根据当前 Context
         * 找到 Service 应该注册到哪个 Slot。
         */
        val slot =
            resolveSlot(key)

        val binding =
            core.services.provide(
                slot = slot,
                service = service,
                ownerId = ownerId,
            )

        /**
         * 防止 Disposable 重复执行。
         */
        var disposed = false

        val disposable =
            Disposable {
                mutate {
                    if (!disposed) {
                        disposed = true

                        /**
                         * 精确删除当前 Binding。
                         *
                         * 使用 Binding Identity 删除，
                         * 而不是直接 remove(key)。
                         *
                         * 这样旧 Disposable
                         * 就不会误删后来 replace 的新 Service。
                         */
                        val removed =
                            core.services.remove(
                                binding
                            )

                        /**
                         * Service 真正消失后，
                         * 通知 Runtime 刷新 dependency。
                         */
                        if (removed) {
                            core.notifyServiceChanged(slot)
                        }
                    }
                }
            }

        /**
         * provide 本身就是当前 Context 创建的 effect。
         * 调用方仍然可以提前 dispose；Context 最终销毁时再次调用是幂等的。
         */
        effect(disposable)

        /**
         * Service 已经出现。
         *
         * 通知 Runtime：
         *
         * 某些 Pending Plugin
         * 可能已经可以启动。
         */
        core.notifyServiceChanged(slot)

        return disposable
    }

    /**
     * 获取当前 Context 能看到的 Service。
     *
     * Plugin 通常通过：
     *
     * context.get(LlmKey)
     *
     * 或：
     *
     * context.require(LlmKey)
     *
     * 使用 Service。
     */
    fun <T : Any> get(
        key: ServiceKey<T>,
    ): T? {

        checkActive()

        val slot =
            resolveSlot(key)

        return core.services.get(slot)
    }

    /**
     * 获取一个必须存在的 Service。
     *
     * 不存在时直接抛异常。
     */
    fun <T : Any> require(
        key: ServiceKey<T>,
    ): T {
        return get(key)
            ?: error(
                "Service '${key.name}' is not available"
            )
    }

    /**
     * 判断当前 Context
     * 是否能够看到指定 Service。
     *
     * Plugin.inject
     * 会使用这个方法判断 dependency 是否满足。
     */
    fun has(
        key: ServiceKey<*>,
    ): Boolean {
        checkActive()

        return core.services.contains(
            resolveAnySlot(key)
        )
    }

    /**
     * 获取当前 Context 对某个 ServiceKey
     * 实际看到的 ServiceBinding。
     *
     * 主要供 Runtime 内部使用。
     *
     * 普通 Plugin 一般只应该关心 Service value，
     * 不应该直接操作 Binding。
     */
    internal fun <T : Any> binding(
        key: ServiceKey<T>,
    ): ServiceBinding<T>? {
        return core.services.binding(
            resolveSlot(key)
        )
    }

    /**
     * 获取当前 Service implementation 的 Binding ID。
     *
     * Fiber 的 dependencySnapshot
     * 就依赖这个 ID。
     *
     *
     * 例如：
     *
     * 启动时：
     *
     * LlmKey -> Binding #10
     *
     * 后来：
     *
     * LlmKey -> Binding #20
     *
     * Fiber 就知道：
     *
     * “虽然 LlmKey 一直存在，
     *  但 Service implementation 已经变化。”
     */
    internal fun bindingId(
        key: ServiceKey<*>,
    ): Long? {
        return core.services.bindingId(
            resolveAnySlot(key)
        )
    }

    /**
     * 替换当前 Context 对应 Slot 中的
     * Service implementation。
     *
     *
     * replace 前：
     *
     * LlmKey
     * -> Binding #10
     * -> DeepSeek
     *
     *
     * replace 后：
     *
     * LlmKey
     * -> Binding #20
     * -> OpenAI
     *
     *
     * ServiceKey / ServiceSlot 没变，
     * 但是 Binding Identity 改变。
     *
     * Runtime 会通知依赖这个 Binding 的 Plugin reload。
     *
     *
     * 目前作为 Runtime 内部能力，
     * 所以使用 internal。
     */
    internal suspend fun <T : Any> replace(
        key: ServiceKey<T>,
        service: T,
    ): Disposable {
        return mutate {
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

        checkActive()

        val slot =
            resolveSlot(key)

        val binding =
            core.services.replace(
                slot = slot,
                service = service,
                ownerId = ownerId,
            )

        var disposed = false

        val disposable =
            Disposable {
                mutate {
                    if (!disposed) {
                        disposed = true

                        val removed =
                            core.services.remove(
                                binding
                            )

                        if (removed) {
                            core.notifyServiceChanged(slot)
                        }
                    }
                }
            }

        effect(disposable)

        /**
         * Binding Identity 已经改变，
         * 立即通知 Runtime。
         */
        core.notifyServiceChanged(slot)

        return disposable
    }

    /**
     * 永久销毁当前 Context Scope。
     *
     * 顺序：
     *
     * 1. 通知 Runtime 先注销并停止整个子树中的 Plugin
     * 2. 反向递归销毁 child Context
     * 3. 反向释放当前 Context 自己的 effects
     * 4. 从 parent 的 children 中移除
     *
     * 重复 dispose 是安全的。
     */
    suspend fun dispose() {
        mutate {
            disposeNow()
        }
    }

    private suspend fun disposeNow() {
        if (disposing || isDisposed) {
            return
        }

        disposing =
            true

        var failure: Throwable? =
            null

        try {
            core.notifyContextDisposing(this)
        } catch (error: Throwable) {
            failure =
                error
        }

        val pendingChildren =
            children.toList().asReversed()

        for (child in pendingChildren) {
            try {
                child.dispose()
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
            effects.dispose()
        } catch (error: Throwable) {
            if (failure == null) {
                failure =
                    error
            } else if (error !== failure) {
                failure.addSuppressed(error)
            }
        }

        children.clear()
        parent?.children?.remove(this)

        disposing =
            false

        isDisposed =
            true

        failure?.let { error ->
            throw error
        }
    }

    /**
     * Runtime 用来判断 Fiber Context 是否位于某个待销毁 Scope 下。
     */
    internal fun isSameOrDescendantOf(
        ancestor: Context,
    ): Boolean {
        var current: Context? =
            this

        while (current != null) {
            if (current === ancestor) {
                return true
            }

            current =
                current.parent
        }

        return false
    }

    /**
     * Context 在树中的深度；用于按叶子到根停止 Plugin。
     */
    internal val depth: Int
        get() {
            var result = 0
            var current = parent

            while (current != null) {
                result += 1
                current = current.parent
            }

            return result
        }

    private fun attachChild(
        child: Context,
    ) {
        checkActive()
        children += child
    }

    private fun checkActive() {
        check(!disposing && !isDisposed) {
            "Context is disposed"
        }
    }

    companion object {

        /**
         * 给 Runtime 创建真正的 RootContext。
         *
         * RuntimeCore 由 Runtime 创建，
         * RootContext 绑定到这个 State。
         */
        internal fun root(
            core: RuntimeCore,
        ): Context {
            return Context(
                parent = null,
                ownerId = null,
                core = core,
                isolatedSlots = emptyMap(),
                localIntercepts = emptyMap(),
            )
        }
    }
}
