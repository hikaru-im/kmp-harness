package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.event.EventsService
import im.hikaru.harness.runtime.service.ServiceRegistry
import im.hikaru.harness.runtime.service.ServiceSlot

/**
 * 整棵 Context Tree 共享的内部运行状态。
 *
 * 可以这样理解：
 *
 * Context
 *     =
 *     “当前站在哪个 Context / Scope 看 Runtime”
 *
 * RuntimeCore
 *     =
 *     “这棵 Context Tree 背后真正共享的基础设施”
 *
 *
 * 一棵 Context Tree：
 *
 * RootContext
 * ├── Fiber Context
 * ├── IsolatedContext
 * │    └── Fiber Context
 * └── ...
 *
 * 所有 Context 都共享同一个 RuntimeCore。
 *
 *
 * RuntimeCore 当前负责：
 *
 * - ServiceRegistry
 * - EventsService
 * - ServiceSlot 变化通知
 *
 *
 * RuntimeCore 不负责：
 *
 * - Plugin 生命周期
 * - dependency 判断
 * - Plugin refresh
 *
 * 这些仍然由 Runtime 负责。
 */
internal class RuntimeCore {

    /**
     * install / retry / provide / replace / dispose 的统一串行化边界。
     */
    private val mutations =
        RuntimeMutationLane()

    suspend fun <T> mutate(
        block: suspend () -> T,
    ): T {
        return mutations.run(block)
    }

    /**
     * 整个 Runtime 共用的 ServiceRegistry。
     *
     * isolate 并不会创建新的 Registry。
     *
     * isolate 的实现方式是：
     *
     * 同一个 ServiceKey
     *     ↓
     * 不同 ServiceSlot
     *     ↓
     * 不同 ServiceBinding
     *
     *
     * 例如：
     *
     * DefaultLlmSlot
     *     -> DeepSeek
     *
     * ResearchLlmSlot
     *     -> OpenAI
     *
     * 两份 Service 都存在于同一个 Registry 中。
     */
    val services =
        ServiceRegistry()

    /**
     * 整个 Runtime 共用的 EventsService。
     *
     * 所以：
     *
     * root.events === child.events
     */
    val events =
        EventsService()

    /**
     * Service 变化后的通知 Handler。
     *
     * 和之前的：
     *
     * suspend () -> Unit
     *
     * 不一样。
     *
     * 现在明确携带：
     *
     * ServiceSlot<*>
     *
     * 也就是说 Runtime 不只是知道：
     *
     * “有 Service 变化了”
     *
     * 而是知道：
     *
     * “具体是哪个 ServiceSlot 变化了”
     *
     *
     * 这样 Runtime 才能够通过 dependencyIndex
     * 定向找到真正受影响的 Fiber。
     */
    private var serviceChangedHandler:
            (suspend (ServiceSlot<*>) -> Unit)? = null

    /**
     * Context 开始销毁时的 Runtime 回调。
     *
     * Runtime 会在这里先把该 Context 子树中的 Fiber
     * 从 dependency index 和管理列表中移除，再按从叶子到根的顺序停止。
     *
     * 独立创建的 Context Tree 可以没有这个回调；
     * 此时 Context 仍然会正常释放 children 和自有 effects。
     */
    private var contextDisposingHandler:
            (suspend (Context) -> Unit)? = null

    /**
     * Runtime 创建后，
     * 会把自己的 Service Change 处理逻辑绑定进来。
     *
     * 一棵 RuntimeCore 只允许绑定一个 Runtime。
     *
     * 如果重复绑定，
     * 通常意味着 Runtime ownership 出现了问题。
     */
    fun bindServiceChangedHandler(
        handler: suspend (ServiceSlot<*>) -> Unit,
    ) {
        check(serviceChangedHandler == null) {
            "Service change handler is already bound"
        }

        serviceChangedHandler =
            handler
    }

    /**
     * 把 Context 生命周期接入唯一拥有这棵 Context Tree 的 Runtime。
     */
    fun bindContextDisposingHandler(
        handler: suspend (Context) -> Unit,
    ) {
        check(contextDisposingHandler == null) {
            "Context disposing handler is already bound"
        }

        contextDisposingHandler =
            handler
    }

    /**
     * 通知 Runtime：
     *
     * 某个 ServiceSlot 已经发生变化。
     *
     *
     * 变化可能包括：
     *
     * 1. Service 出现
     *
     * null
     *   ↓
     * Binding #1
     *
     *
     * 2. Service 消失
     *
     * Binding #1
     *   ↓
     * null
     *
     *
     * 3. Service implementation 被替换
     *
     * Binding #1
     *   ↓
     * Binding #2
     *
     *
     * Runtime 收到 Slot 后，
     * 会通过 dependencyIndex
     * 找到真正依赖这个 Slot 的 Plugin。
     */
    suspend fun notifyServiceChanged(
        slot: ServiceSlot<*>,
    ) {
        serviceChangedHandler?.invoke(slot)
    }

    /**
     * 通知 Runtime 某个 Context 子树即将被销毁。
     */
    suspend fun notifyContextDisposing(
        context: Context,
    ) {
        contextDisposingHandler?.invoke(context)
    }
}
