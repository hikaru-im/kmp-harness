package im.hikaru.harness.runtime.effect

/**
 * 表示一个“可以被释放的资源”。
 *
 * 可以把它理解成：
 *
 * “插件停止的时候，需要执行的一段清理代码。”
 *
 * 例如：
 *
 * - 关闭网络连接
 * - 注销事件监听
 * - 停止协程
 * - 关闭文件
 * - 移除注册的 Service
 *
 * 使用 fun interface 后，
 * 可以直接使用 lambda 创建 Disposable：
 *
 * Disposable {
 *     server.close()
 * }
 */
fun interface Disposable {

    /**
     * 释放资源。
     *
     * 使用 suspend 是因为有些资源清理可能是异步的，
     * 例如关闭网络连接、等待后台任务退出等。
     */
    suspend fun dispose()
}