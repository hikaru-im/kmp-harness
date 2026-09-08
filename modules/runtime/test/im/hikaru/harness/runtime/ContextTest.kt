@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
)

package im.hikaru.harness.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.runtime.service.ServiceSlot
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNotSame

class ContextTest {

    interface TestService {
        val value: String
    }

    object TestServiceKey :
        ServiceKey<TestService>("test")

    class TestServiceImpl(
        override val value: String,
    ) : TestService

    @Test
    fun shouldResolveLocalService() = runTest {
        val ctx = Context()

        val service =
            TestServiceImpl("hello")

        ctx.provide(
            TestServiceKey,
            service,
        )

        assertSame(
            service,
            ctx.require(TestServiceKey),
        )
    }

    @Test
    fun shouldResolveServiceFromParent() = runTest {
        val root = Context()

        val service =
            TestServiceImpl("root")

        root.provide(
            TestServiceKey,
            service,
        )

        val child =
            Context(parent = root)

        assertSame(
            service,
            child.require(TestServiceKey),
        )
    }

    /**
     * 普通 child 默认继承 parent 的 Service Slot。
     *
     * 如果 child 希望对同一个 ServiceKey
     * 使用自己的 Service implementation，
     * 必须显式 isolate 这个 ServiceKey。
     */
    @Test
    fun isolatedChildCanHaveItsOwnService() = runTest {
        val root =
            Context()

        root.provide(
            TestServiceKey,
            TestServiceImpl("root"),
        )

        /**
         * 不再使用普通：
         *
         * Context(parent = root)
         *
         * 而是明确告诉 Context：
         *
         * “TestServiceKey 在这里需要独立。”
         */
        val child =
            root.isolate(TestServiceKey)

        child.provide(
            TestServiceKey,
            TestServiceImpl("child"),
        )

        /**
         * Root 仍然使用 defaultSlot。
         */
        assertEquals(
            "root",
            root.require(TestServiceKey).value,
        )

        /**
         * child 使用自己的 isolatedSlot。
         */
        assertEquals(
            "child",
            child.require(TestServiceKey).value,
        )
    }

    @Test
    fun providedServiceShouldBeRemovedAfterDispose() = runTest {
        val context =
            Context()

        val serviceKey =
            ServiceKey<TestService>("test")

        val service =
            TestServiceImpl("hello")

        /**
         * 注册 Service。
         */
        val disposable =
            context.provide(
                serviceKey,
                service,
            )

        /**
         * 注册之后应该能够获取。
         */
        assertSame(
            service,
            context.require(serviceKey),
        )

        /**
         * 撤销 provide。
         */
        disposable.dispose()

        /**
         * Service 应该已经不存在。
         */
        assertEquals(
            false,
            context.has(serviceKey),
        )

        assertEquals(
            null,
            context.get(serviceKey),
        )
    }

    /**
     * child() 应该创建一个全新的 Context，
     * 并建立正确的 parent / root 关系。
     */
    @Test
    fun shouldCreateChildContext() {
        val root =
            Context()

        val child =
            root.child()

        val grandchild =
            child.child()

        assertNotSame(
            root,
            child,
        )

        assertNotSame(
            child,
            grandchild,
        )

        assertSame(
            root,
            child.parent,
        )

        assertSame(
            child,
            grandchild.parent,
        )

        /**
         * 无论多少层，
         * root 始终应该指向最顶层 Context。
         */
        assertSame(
            root,
            child.root,
        )

        assertSame(
            root,
            grandchild.root,
        )
    }

    /**
     * isolate(ServiceKey) 后，
     * 新 Context 不应该再看到 parent
     * 在默认 Slot 中提供的 Service。
     */
    @Test
    fun isolatedContextShouldNotSeeParentServiceForIsolatedKey() = runTest {
        val root =
            Context()

        val serviceKey =
            ServiceKey<TestService>("test")

        val rootService =
            TestServiceImpl("root")

        root.provide(
            serviceKey,
            rootService,
        )

        /**
         * 普通 child 可以看到 Root Service。
         */
        val normalChild =
            root.child()

        assertSame(
            rootService,
            normalChild.require(serviceKey),
        )

        /**
         * isolate 后，
         * LlmKey / TestServiceKey
         * 改用了一个独立 Slot。
         */
        val isolated =
            root.isolate(serviceKey)

        /**
         * 新 Slot 目前还没有 Service。
         *
         * 所以看不到 Root 原来的 Binding。
         */
        assertEquals(
            null,
            isolated.get(serviceKey),
        )
    }

    /**
     * isolated Context 可以为相同 ServiceKey
     * 提供自己的 Service implementation。
     *
     * Root 和 isolated Context
     * 最终应该看到不同 Service。
     */
    @Test
    fun isolatedContextShouldUseItsOwnService() = runTest {
        val root =
            Context()

        val serviceKey =
            ServiceKey<TestService>("test")

        val rootService =
            TestServiceImpl("root")

        root.provide(
            serviceKey,
            rootService,
        )

        val isolated =
            root.isolate(serviceKey)

        val isolatedService =
            TestServiceImpl("isolated")

        isolated.provide(
            serviceKey,
            isolatedService,
        )

        /**
         * Root 仍然看到原来的 Service。
         */
        assertSame(
            rootService,
            root.require(serviceKey),
        )

        /**
         * isolated Context 看到自己的 Service。
         */
        assertSame(
            isolatedService,
            isolated.require(serviceKey),
        )
    }

    /**
     * isolate 只应该影响指定 ServiceKey。
     *
     * 其他 Service 仍然正常从 parent Context 继承。
     */
    @Test
    fun isolateShouldOnlyAffectSpecifiedService() = runTest {
        val root =
            Context()

        val llmKey =
            ServiceKey<TestService>("llm")

        val toolsKey =
            ServiceKey<TestService>("tools")

        val llm =
            TestServiceImpl("root-llm")

        val tools =
            TestServiceImpl("root-tools")

        root.provide(
            llmKey,
            llm,
        )

        root.provide(
            toolsKey,
            tools,
        )

        /**
         * 只 isolate LLM。
         */
        val isolated =
            root.isolate(llmKey)

        /**
         * LLM 已经被隔离，
         * 所以看不到 root 的 LLM。
         */
        assertEquals(
            null,
            isolated.get(llmKey),
        )

        /**
         * Tools 没有 isolate，
         * 所以仍然应该看到 Root Tools。
         */
        assertSame(
            tools,
            isolated.require(toolsKey),
        )
    }

    /**
     * 一个 Context isolate 某个 Service 后，
     * 它的普通 child 应该继续继承这个 isolation。
     */
    @Test
    fun childShouldInheritParentIsolation() = runTest {
        val root =
            Context()

        val serviceKey =
            ServiceKey<TestService>("test")

        root.provide(
            serviceKey,
            TestServiceImpl("root"),
        )

        val isolated =
            root.isolate(serviceKey)

        val isolatedService =
            TestServiceImpl("isolated")

        isolated.provide(
            serviceKey,
            isolatedService,
        )

        /**
         * isolated 的普通 child
         * 不创建新 Slot，
         * 所以应该继续使用 parent 的 isolated Slot。
         */
        val child =
            isolated.child()

        assertSame(
            isolatedService,
            child.require(serviceKey),
        )
    }

    /**
     * 手动通过 Context(parent = root)
     * 创建 child 时，
     * 应该和 root 共享同一个 EventsService。
     *
     * Context 层级可以不同，
     * 但 EventsService 属于整个 Runtime 的共享基础设施。
     */
    @Test
    fun childShouldShareEventsServiceWithParent() {
        val root =
            Context()

        val child =
            Context(
                parent = root,
            )

        assertSame(
            root.events,
            child.events,
        )
    }

    /**
     * 通过构造器创建的 child
     * 应该真正加入 parent 所在的 Service 空间。
     *
     * 这不是单纯测试 parent 指针，
     * 而是验证它们共享同一个 Registry。
     */
    @Test
    fun manuallyCreatedChildShouldShareServiceRegistryWithParent() = runTest {
        val root =
            Context()

        val child =
            Context(
                parent = root,
            )

        val service =
            TestServiceImpl("shared")

        root.provide(
            TestServiceKey,
            service,
        )

        assertSame(
            service,
            child.require(TestServiceKey),
        )
    }

    /**
     * isolated Context 中 Service 变化时，
     * RuntimeCore 应该收到对应 isolated ServiceSlot，
     * 而不是 DefaultSlot。
     *
     * 这保证后面的 dependencyIndex
     * 能够准确定位 isolate 作用域。
     */
    @Test
    fun isolatedServiceChangeShouldNotifyItsOwnSlot() = runTest {
        val core =
            RuntimeCore()

        val root =
            Context.root(core)

        val serviceKey =
            ServiceKey<TestService>("test")

        val isolated =
            root.isolate(
                serviceKey
            )

        var changedSlot:
                ServiceSlot<*>? = null

        core.bindServiceChangedHandler { slot ->
            changedSlot = slot
        }

        isolated.provide(
            serviceKey,
            TestServiceImpl("isolated"),
        )

        assertSame(
            isolated.serviceSlot(serviceKey),
            changedSlot,
        )

        /**
         * isolated Slot
         * 绝对不能是默认 Slot。
         */
        assertNotSame(
            serviceKey.defaultSlot,
            changedSlot,
        )
    }

    @Test
    fun disposingContextShouldDisposeChildrenAndEffectsInReverseOrder() = runTest {
        val root =
            Context()

        val order =
            mutableListOf<String>()

        root.effect {
            order += "root-first"
        }

        val child =
            root.child()

        child.effect {
            order += "child-first"
        }

        child.effect {
            order += "child-second"
        }

        root.effect {
            order += "root-second"
        }

        root.dispose()

        assertEquals(
            listOf(
                "child-second",
                "child-first",
                "root-second",
                "root-first",
            ),
            order,
        )

        assertEquals(
            true,
            child.isDisposed,
        )

        assertEquals(
            true,
            root.isDisposed,
        )

        /**
         * Context dispose 必须幂等。
         */
        root.dispose()

        assertEquals(
            4,
            order.size,
        )
    }

    @Test
    fun contextOwnedListenerShouldBeRemovedAfterDispose() = runTest {
        val context =
            Context()

        val eventKey =
            EventKey<String>("owned-event")

        val events =
            context.events

        var eventCount = 0

        context.on(eventKey) {
            eventCount += 1
        }

        events.emit(
            eventKey,
            "before-dispose",
        )

        assertEquals(
            1,
            eventCount,
        )

        context.dispose()

        events.emit(
            eventKey,
            "after-dispose",
        )

        assertEquals(
            1,
            eventCount,
        )
    }

    @Test
    fun disposedContextShouldRejectFurtherUse() = runTest {
        val root =
            Context()

        val context =
            root.child()

        val serviceKey =
            ServiceKey<TestService>("disposed")

        context.dispose()

        assertFailsWith<IllegalStateException> {
            context.child()
        }

        assertFailsWith<IllegalStateException> {
            context.isolate(serviceKey)
        }

        assertFailsWith<IllegalStateException> {
            context.get(serviceKey)
        }

        assertFailsWith<IllegalStateException> {
            context.events
        }

        assertFailsWith<IllegalStateException> {
            context.effect {
                // no-op
            }
        }

        var provideFailed = false

        try {
            context.provide(
                serviceKey,
                TestServiceImpl("late"),
            )
        } catch (_: IllegalStateException) {
            provideFailed = true
        }

        assertEquals(
            true,
            provideFailed,
        )

        /**
         * 只销毁 child 不应影响仍然存活的 parent。
         */
        assertEquals(
            false,
            root.isDisposed,
        )

        root.child()
    }

    @Test
    fun contextDisposeShouldContinueCleanupAfterAnEffectFails() = runTest {
        val context =
            Context()

        val order =
            mutableListOf<String>()

        context.effect {
            order += "first"
        }

        context.effect {
            order += "failing"
            error("cleanup failed")
        }

        context.effect {
            order += "last"
        }

        var failure: Throwable? =
            null

        try {
            context.dispose()
        } catch (error: Throwable) {
            failure =
                error
        }

        assertEquals(
            "cleanup failed",
            failure?.message,
        )

        assertEquals(
            listOf(
                "last",
                "failing",
                "first",
            ),
            order,
        )

        assertEquals(
            true,
            context.isDisposed,
        )
    }
}
