package im.hikaru.harness.runtime.service

/**
 * ServiceSlot 表示：
 *
 * “某个 Context 实际应该使用哪一份 Service 绑定。”
 *
 *
 * ServiceKey 和 ServiceSlot 的区别：
 *
 * ServiceKey
 *     =
 *     能力是什么？
 *
 *   例如：
 *     LlmKey
 *
 *
 * ServiceSlot
 *     =
 *     这个 Context 使用哪一份 LLM？
 *
 *
 * 例如：
 *
 * LlmKey
 *    │
 *    ├── Root Slot
 *    │      └── DeepSeek
 *    │
 *    └── Isolated Slot
 *           └── OpenAI
 *
 *
 * ServiceSlot 本身使用对象身份区分。
 *
 * 即使两个 Slot 的 key 都是 LlmKey，
 * 它们也是两个不同的 Service 空间。
 */
class ServiceSlot<T : Any> internal constructor(

    /**
     * 当前 Slot 属于哪一种 Service。
     */
    val key: ServiceKey<T>,
) {

    override fun toString(): String {
        return "ServiceSlot(${key.name})"
    }
}