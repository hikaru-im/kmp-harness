package im.hikaru.harness.runtime.plugin

/**
 * Fiber 当前所处的生命周期状态。
 *
 * 一个插件大致经历：
 *
 * Pending
 *    ↓
 * Loading
 *    ↓
 * Active
 *    ↓
 * Unloading
 *    ↓
 * Disposed
 *
 * 如果启动失败：
 *
 * Loading
 *    ↓
 * Failed
 */
enum class FiberState {

    /**
     * 插件已经创建，但当前还没有运行。
     *
     * 后面实现 dependency / inject 后，
     * 如果插件缺少依赖，也会停留在 Pending。
     */
    Pending,

    /**
     * 正在执行 Plugin.apply()。
     */
    Loading,

    /**
     * 插件已经成功启动，可以正常工作。
     */
    Active,

    /**
     * Plugin.apply() 执行失败。
     */
    Failed,

    /**
     * 正在释放插件创建的资源。
     */
    Unloading,

    /**
     * 插件实例已经彻底销毁。
     *
     * 当前第一版中，Disposed 后不能再次 start。
     *
     * 后面实现 Cordis 风格的依赖动态启停时，
     * 我们会进一步区分：
     *
     * “暂时停止”
     *
     * 和
     *
     * “永久销毁”
     *
     * 这两个概念。
     */
    Disposed,
}