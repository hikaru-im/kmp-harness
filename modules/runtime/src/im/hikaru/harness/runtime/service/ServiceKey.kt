package im.hikaru.harness.runtime.service

/**
 * 一个 ServiceKey 表示某种运行时能力。
 *
 * 例如：
 *
 * LlmKey   -> LlmService
 * ToolsKey -> ToolsService
 *
 * T 决定这个 Key 对应的 Service 类型。
 */
open class ServiceKey<T : Any>(
    val name: String,
) {
    /**
     * 每个 ServiceKey 都拥有一个默认 Slot。
     *
     * 没有执行 isolate 的 Context，
     * 最终都会解析到这个默认 Slot。
     *
     * 所以默认情况下：
     *
     * RootContext
     * Plugin A Context
     * Plugin B Context
     *
     * 对 LlmKey 都会使用：
     *
     * LlmKey.defaultSlot
     *
     * 因此大家看到的是同一个 Service。
     */
    internal val defaultSlot =
        ServiceSlot(this)

    override fun toString(): String {
        return "ServiceKey($name)"
    }
}