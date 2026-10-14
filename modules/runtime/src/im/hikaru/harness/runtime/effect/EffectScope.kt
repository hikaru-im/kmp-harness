package im.hikaru.harness.runtime.effect

/**
 * 管理一个插件运行期间创建的所有 Disposable。
 *
 * Fiber 会拥有一个 EffectScope。
 *
 * 插件启动过程中创建的资源，
 * 都把对应的清理操作放进这个 Scope。
 *
 * 为了不把 Runtime 的同步状态提交锁跨用户 cleanup 持有，
 * 释放被拆成两步：
 *
 * 1. seal()：在锁内同步封存，标记已释放并取出反序待释放清单；
 * 2. runDisposablesInReverse()：在锁外执行真正的 cleanup。
 *
 * 这样 cleanup 内部即使等待其他协程，也不会和锁形成等待环。
 */
class EffectScope {

    /**
     * 当前已经注册的资源清理操作。
     */
    private val disposables =
        mutableListOf<Disposable>()

    /**
     * EffectScope 一旦开始释放就永久关闭。
     *
     * Fiber 每次重新启动都会创建新的 EffectScope，
     * 因此旧生命周期不能在结束后继续注册副作用。
     */
    var isDisposed: Boolean =
        false
        private set

    /**
     * 注册一个需要在插件停止时执行的清理操作。
     */
    fun add(
        disposable: Disposable,
    ) {
        check(!isDisposed) {
            "EffectScope is disposed"
        }

        disposables += disposable
    }

    /**
     * lambda 形式的注册。
     */
    fun add(
        dispose: suspend () -> Unit,
    ) {
        add(
            Disposable {
                dispose()
            }
        )
    }

    /**
     * 同步封存：标记关闭并按注册反序取出待释放清单。
     *
     * 重复封存返回空清单，因此释放是幂等的。
     */
    internal fun seal(): List<Disposable> {

        if (isDisposed) {
            return emptyList()
        }

        isDisposed =
            true

        val pending =
            disposables.toList().asReversed()

        disposables.clear()

        return pending
    }

    /**
     * 释放当前 Scope 管理的全部资源。
     */
    suspend fun dispose() {
        runDisposablesInReverse(
            seal()
        )
    }
}

/**
 * 在锁外按给定顺序释放 Disposable。
 *
 * 一个 cleanup 失败不能阻止其他资源继续释放，
 * 全部尝试完成后把第一个失败交还给调用方，
 * 其余不同实例的错误作为 suppressed 追加一次。
 */
internal suspend fun runDisposablesInReverse(
    pending: List<Disposable>,
) {

    var failure: Throwable? =
        null

    for (disposable in pending) {
        try {
            disposable.dispose()
        } catch (error: Throwable) {
            if (failure == null) {
                failure =
                    error
            } else if (error !== failure) {
                failure.addSuppressed(error)
            }
        }
    }

    failure?.let { error ->
        throw error
    }
}
