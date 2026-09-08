package im.hikaru.harness.runtime.effect

/**
 * 管理一个插件运行期间创建的所有 Disposable。
 *
 * Fiber 会拥有一个 EffectScope。
 *
 * 插件启动过程中创建的资源，
 * 都把对应的清理操作放进这个 Scope。
 *
 * 例如：
 *
 * Fiber
 *      │
 *      └── EffectScope
 *             ├── close database
 *             ├── unregister listener
 *             └── stop coroutine
 *
 * 插件停止时：
 *
 * EffectScope.dispose()
 *
 * 会统一释放这些资源。
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
     * 为了使用起来更自然，
     * 再提供一个接收 lambda 的版本。
     *
     * 以后插件可以直接写：
     *
     * scope.add {
     *     server.close()
     * }
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
     * 释放当前 Scope 管理的全部资源。
     *
     * 注意这里按照“注册顺序的反方向”释放。
     *
     * 例如创建顺序：
     *
     * Database
     *    ↓
     * Repository
     *    ↓
     * Agent
     *
     * 销毁顺序应该是：
     *
     * Agent
     *    ↓
     * Repository
     *    ↓
     * Database
     *
     * 也就是：
     *
     * A → B → C 创建
     * C → B → A 销毁
     *
     * Cordis 的 Effect 也是这个思想。
     */
    suspend fun dispose() {

        if (isDisposed) {
            return
        }

        isDisposed =
            true

        // 先复制出来，避免 dispose 过程中修改原列表。
        val pending =
            disposables.toList().asReversed()

        // 提前清空，避免重复 dispose。
        disposables.clear()

        var failure: Throwable? =
            null

        for (disposable in pending) {
            try {
                disposable.dispose()
            } catch (error: Throwable) {
                /**
                 * 一个 cleanup 失败不能阻止其他资源继续释放。
                 * 全部尝试完成后，再把第一个失败交还给调用方。
                 */
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
}
