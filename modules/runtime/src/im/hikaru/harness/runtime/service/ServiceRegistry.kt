package im.hikaru.harness.runtime.service

/**
 * Runtime 中真正保存 ServiceBinding 的地方。
 *
 * 以前按照 ServiceKey 存储：
 *
 * LlmKey -> Binding
 *
 *
 * 现在按照 ServiceSlot 存储：
 *
 * RootLlmSlot
 *     -> DeepSeek Binding
 *
 * AgentBLlmSlot
 *     -> OpenAI Binding
 *
 *
 * 因此：
 *
 * 同一个 ServiceKey
 * 可以同时拥有多份不同的 ServiceBinding。
 */
class ServiceRegistry {

    private val services =
        mutableMapOf<
                ServiceSlot<*>,
                ServiceBinding<*>,
                >()

    /**
     * 生成 Binding 唯一 ID。
     */
    private var nextBindingId =
        1L

    /**
     * 向指定 Slot 注册 Service。
     *
     * Context 会负责：
     *
     * ServiceKey
     *     ↓
     * resolveSlot()
     *     ↓
     * ServiceSlot
     *
     * Registry 本身只负责 Slot。
     */
    fun <T : Any> provide(
        slot: ServiceSlot<T>,
        service: T,
        ownerId: Long? = null,
    ): ServiceBinding<T> {

        check(slot !in services) {
            "Service '${slot.key.name}' is already provided in this slot"
        }

        val binding =
            ServiceBinding(
                id = nextBindingId++,
                key = slot.key,
                slot = slot,
                value = service,
                ownerId = ownerId,
            )

        services[slot] = binding

        return binding
    }

    /**
     * 兼容默认 Service 空间的便利方法。
     *
     * 直接通过 ServiceKey 调用时，
     * 使用这个 Key 的 defaultSlot。
     *
     * ServiceRegistry 的单元测试，
     * 以及某些底层代码仍然可以使用：
     *
     * registry.provide(key, service)
     */
    fun <T : Any> provide(
        key: ServiceKey<T>,
        service: T,
        ownerId: Long? = null,
    ): ServiceBinding<T> {
        return provide(
            slot = key.defaultSlot,
            service = service,
            ownerId = ownerId,
        )
    }

    /**
     * 获取指定 Slot 的 Service。
     */
    fun <T : Any> get(
        slot: ServiceSlot<T>,
    ): T? {
        return binding(slot)?.value
    }

    /**
     * 默认 Slot 的便利版本。
     */
    fun <T : Any> get(
        key: ServiceKey<T>,
    ): T? {
        return get(key.defaultSlot)
    }

    /**
     * 获取指定 Slot 对应的 Binding。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> binding(
        slot: ServiceSlot<T>,
    ): ServiceBinding<T>? {
        return services[slot]
                as? ServiceBinding<T>
    }

    /**
     * 默认 Slot 的便利版本。
     */
    fun <T : Any> binding(
        key: ServiceKey<T>,
    ): ServiceBinding<T>? {
        return binding(key.defaultSlot)
    }

    /**
     * 获取 Slot 当前 Binding ID。
     */
    fun bindingId(
        slot: ServiceSlot<*>,
    ): Long? {
        return services[slot]?.id
    }

    /**
     * 默认 Slot 的便利版本。
     */
    fun bindingId(
        key: ServiceKey<*>,
    ): Long? {
        return bindingId(key.defaultSlot)
    }

    /**
     * 判断指定 Slot 有没有 Service。
     */
    fun contains(
        slot: ServiceSlot<*>,
    ): Boolean {
        return slot in services
    }

    /**
     * 默认 Slot 的便利版本。
     */
    fun contains(
        key: ServiceKey<*>,
    ): Boolean {
        return contains(key.defaultSlot)
    }

    /**
     * 替换指定 Slot 的 Service implementation。
     *
     * ServiceKey 和 Slot 都不变，
     * 但是 Binding ID 会改变。
     */
    fun <T : Any> replace(
        slot: ServiceSlot<T>,
        service: T,
        ownerId: Long? = null,
    ): ServiceBinding<T> {

        check(slot in services) {
            "Service '${slot.key.name}' is not provided in this slot"
        }

        val binding =
            ServiceBinding(
                id = nextBindingId++,
                key = slot.key,
                slot = slot,
                value = service,
                ownerId = ownerId,
            )

        services[slot] = binding

        return binding
    }

    /**
     * 默认 Slot 的便利版本。
     */
    fun <T : Any> replace(
        key: ServiceKey<T>,
        service: T,
        ownerId: Long? = null,
    ): ServiceBinding<T> {
        return replace(
            slot = key.defaultSlot,
            service = service,
            ownerId = ownerId,
        )
    }

    /**
     * 精确删除某一个 ServiceBinding。
     *
     * 只有 Registry 当前仍然指向这个 Binding
     * 才允许删除。
     *
     * 这样旧 Disposable 就不会误删新 Binding。
     */
    fun remove(
        binding: ServiceBinding<*>,
    ): Boolean {

        val current =
            services[binding.slot]
                ?: return false

        if (current.id != binding.id) {
            return false
        }

        services.remove(binding.slot)

        return true
    }

    /**
     * 删除指定 Slot。
     */
    fun remove(
        slot: ServiceSlot<*>,
    ): Boolean {
        return services.remove(slot) != null
    }

    /**
     * 默认 Slot 的便利版本。
     */
    fun remove(
        key: ServiceKey<*>,
    ): Boolean {
        return remove(key.defaultSlot)
    }
}