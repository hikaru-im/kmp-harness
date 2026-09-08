@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
)

package im.hikaru.harness.runtime.service

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * ServiceRegistry 底层行为测试。
 */
class ServiceRegistryTest {

    private interface TestService

    /**
     * replace() 后，
     * 即使 ServiceKey 相同，
     * Binding ID 也必须改变。
     */
    @Test
    fun replacingServiceShouldCreateNewBindingIdentity() {
        val registry =
            ServiceRegistry()

        val key =
            ServiceKey<TestService>("test")

        val firstBinding =
            registry.provide(
                key,
                object : TestService {},
            )

        val secondBinding =
            registry.replace(
                key,
                object : TestService {},
            )

        assertNotEquals(
            firstBinding.id,
            secondBinding.id,
        )
    }

    /**
     * 同一个 ServiceKey 的不同 ServiceSlot
     * 应该能够同时保存不同 ServiceBinding。
     *
     * 这是 isolate 能够存在的底层基础。
     */
    @Test
    fun sameServiceKeyShouldSupportMultipleSlots() {
        val registry =
            ServiceRegistry()

        val key =
            ServiceKey<TestService>("test")

        /**
         * key.defaultSlot 只能 Runtime 内部访问，
         * 这个测试和生产代码同 module，
         * 所以可以直接验证。
         */
        val rootSlot =
            key.defaultSlot

        val isolatedSlot =
            ServiceSlot(key)

        val rootService =
            object : TestService {}

        val isolatedService =
            object : TestService {}

        registry.provide(
            rootSlot,
            rootService,
        )

        registry.provide(
            isolatedSlot,
            isolatedService,
        )

        assertSame(
            rootService,
            registry.get(rootSlot),
        )

        assertSame(
            isolatedService,
            registry.get(isolatedSlot),
        )
    }
}
