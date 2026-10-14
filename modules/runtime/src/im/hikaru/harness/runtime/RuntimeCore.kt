package im.hikaru.harness.runtime

import im.hikaru.harness.runtime.event.EventsService
import im.hikaru.harness.runtime.service.ServiceRegistry
import im.hikaru.harness.runtime.service.ServiceSlot
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 整棵 Context Tree 共享的内部运行状态。
 *
 * RuntimeCore 负责：
 *
 * - ServiceRegistry
 * - EventsService
 * - ServiceSlot 变化通知
 * - Context 销毁前的同步认领与锁外收尾动作
 * - 同步状态提交后的锁外 flush 钩子
 */
internal class RuntimeCore {

    /**
     * 只做同步状态提交的单写入者通道。
     */
    private val mutations =
        RuntimeMutationLane()

    /**
     * 同步状态提交边界。block 不可挂起，也不得重入。
     */
    suspend fun <T> mutate(
        block: () -> T,
    ): T {
        return mutations.run(block)
    }

    /**
     * 整个 Runtime 共用的 ServiceRegistry。
     */
    val services =
        ServiceRegistry()

    /**
     * 整个 Runtime 共用的 EventsService。
     */
    val events =
        EventsService()

    /**
     * Service 变化后的同步 Handler：只登记待刷新 Fiber，不做锁外工作。
     */
    private var serviceChangedHandler:
            ((ServiceSlot<*>) -> Unit)? = null

    /**
     * Context 开始销毁时的同步 Handler：先完成登记/索引的同步认领，
     * 并返回必须在锁外执行的收尾动作。
     */
    private var contextDisposingHandler:
            ((Context) -> List<suspend () -> Unit>)? = null

    /**
     * 同步提交结束后在锁外触发的刷新钩子。
     */
    private var postMutationHandler:
            (suspend () -> Unit)? = null

    fun bindServiceChangedHandler(
        handler: (ServiceSlot<*>) -> Unit,
    ) {
        check(serviceChangedHandler == null) {
            "Service change handler is already bound"
        }

        serviceChangedHandler =
            handler
    }

    fun bindContextDisposingHandler(
        handler: (Context) -> List<suspend () -> Unit>,
    ) {
        check(contextDisposingHandler == null) {
            "Context disposing handler is already bound"
        }

        contextDisposingHandler =
            handler
    }

    fun bindPostMutationHandler(
        handler: suspend () -> Unit,
    ) {
        check(postMutationHandler == null) {
            "Post mutation handler is already bound"
        }

        postMutationHandler =
            handler
    }

    /**
     * 某个 ServiceSlot 已经变化。只登记受影响 Fiber。
     */
    fun notifyServiceChanged(
        slot: ServiceSlot<*>,
    ) {
        serviceChangedHandler?.invoke(slot)
    }

    /**
     * 通知 Runtime 某个 Context 子树即将被销毁，返回锁外收尾动作。
     */
    fun notifyContextDisposing(
        context: Context,
    ): List<suspend () -> Unit> {
        return contextDisposingHandler?.invoke(context).orEmpty()
    }

    /**
     * 当前调用是否正处在 Runtime 的某次生命周期操作内部。
     *
     * 只用于决定“是否需要等待刷新完成”，不用于任何加锁判定；
     * 因此即使被 withContext / launch 子协程继承，也不会绕过锁。
     */
    suspend fun isInsideOperation(): Boolean {
        return currentCoroutineContext()[RuntimeOperationMarker]
            ?.core === this
    }

    /**
     * 同步提交完成后触发一次锁外刷新。
     *
     * 嵌套在生命周期操作内部时不等待：当前操作结束后由外层循环继续处理。
     */
    suspend fun flush() {
        if (isInsideOperation()) {
            return
        }

        postMutationHandler?.invoke()
    }
}

/**
 * 标记当前协程正处于某个 RuntimeCore 的生命周期操作内部。
 */
internal class RuntimeOperationMarker(
    val core: RuntimeCore,
) : AbstractCoroutineContextElement(Key) {

    companion object Key :
        CoroutineContext.Key<RuntimeOperationMarker>
}
