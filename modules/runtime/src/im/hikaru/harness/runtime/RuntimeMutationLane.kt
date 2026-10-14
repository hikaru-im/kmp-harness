package im.hikaru.harness.runtime

import kotlinx.coroutines.sync.Mutex

/**
 * Runtime 的单写入者状态通道。
 *
 * 与旧实现的区别：
 *
 * - 临界区只允许同步状态提交；block 不是 suspend，编译期保证内部没有挂起点；
 * - 因此其它协程不可能在临界区内运行，也就不需要任何协程上下文元素来识别“同一条
 *   调用链的重入”；
 * - 锁不可重入：block 不得再次调用 mutate。若需要嵌套，调用方必须先把外层
 *   临界区提交完，再在锁外发起新的 mutate。
 *
 * 所有可能挂起的用户回调、生命周期 cleanup、join 等待与 io，都必须移到锁外执行。
 */
internal class RuntimeMutationLane {

    private val mutex =
        Mutex()

    suspend fun <T> run(
        block: () -> T,
    ): T {
        mutex.lock()

        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
