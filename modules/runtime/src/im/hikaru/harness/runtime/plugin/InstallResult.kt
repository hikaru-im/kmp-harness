package im.hikaru.harness.runtime.plugin

/**
 * 一次 Runtime install 的完整结果。
 *
 * 即使安装过程失败，调用方仍能取得本次创建的 Fiber，
 * 从而执行 retry、uninstall 或管理层 rollback。
 *
 * failure 通常来自 Plugin.apply，也可能来自安装触发的依赖协调。
 */
class InstallResult<C : Any>(
    val fiber: Fiber<C>,
    val failure: Throwable?,
) {

    val isSuccess: Boolean
        get() =
            failure == null

    fun getOrThrow(): Fiber<C> {
        failure?.let { error ->
            throw error
        }

        return fiber
    }
}
