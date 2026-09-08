package im.hikaru.harness.runtime.service

/**
 * Runtime 中一次具体的 Service 注册记录。
 *
 * 以前 ServiceRegistry 只保存：
 *
 * LlmKey -> LlmService
 *
 * 现在改成：
 *
 * LlmKey -> ServiceBinding
 *              ├── id
 *              ├── value
 *              └── ownerId
 *
 *
 * 为什么需要 Binding？
 *
 * 因为 Runtime 后面不仅要知道：
 *
 * “LlmService 存不存在？”
 *
 * 还需要知道：
 *
 * “现在这个 LlmService
 *  还是不是 Plugin 原来依赖的那个实现？”
 */
data class ServiceBinding<T : Any>(

    /**
     * 当前 ServiceBinding 的唯一身份。
     *
     * 每次重新 provide，
     * 都会得到一个新的 id。
     *
     * 例如：
     *
     * 第一次：
     *
     * LlmKey -> Binding #10 -> DeepSeek
     *
     * 后来切换：
     *
     * LlmKey -> Binding #15 -> OpenAI
     *
     * 即使 ServiceKey 仍然是 LlmKey，
     * Runtime 也可以通过 Binding ID
     * 判断 Service implementation 已经发生变化。
     */
    val id: Long,

    /**
     * 当前 Binding 对应哪个 ServiceKey。
     */
    val key: ServiceKey<T>,

    /**
     * 当前 Service 实际注册在哪个 Slot。
     *
     * isolate 的核心就在这里。
     *
     * 同一个 LlmKey：
     *
     * Root Slot     -> DeepSeek
     * Isolated Slot -> OpenAI
     */
    val slot: ServiceSlot<T>,

    /**
     * 真正的 Service 实例。
     */
    val value: T,

    /**
     * 哪个 Fiber 提供了这个 Service。
     *
     * null 表示：
     *
     * 这个 Service 不是某个 Plugin 提供的，
     * 而是由 Runtime Root 直接提供。
     *
     * 例如：
     *
     * runtime.provide(PlatformKey, ...)
     *
     * ownerId = null
     */
    val ownerId: Long?,
)