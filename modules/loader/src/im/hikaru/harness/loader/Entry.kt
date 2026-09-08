package im.hikaru.harness.loader

/**
 * Loader 的声明式 Plugin 配置项。
 *
 * name 对应 Registry 中的注册名；id 标识同一 Plugin 的一次安装。
 * disabled Entry 会保留在快照中，但不会创建 Fiber。
 */
data class Entry(
    val id: String,
    val name: String,
    val config: Any? = null,
    val disabled: Boolean = false,
)
