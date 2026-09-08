package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope

/**
 * Runtime 中一个 Plugin 的“定义”。
 *
 *
 * Plugin 和 Fiber 要区分：
 *
 * Plugin
 *     =
 *     插件定义
 *
 * Fiber
 *     =
 *     Plugin 实际安装运行的一次实例
 *
 *
 * 同一个 Plugin 可以：
 *
 * runtime.install(plugin, configA)
 * runtime.install(plugin, configB)
 *
 * 得到两个不同的 Fiber。
 *
 *
 * C：
 *
 * 当前 Plugin 使用的配置类型。
 *
 * 例如：
 *
 * Plugin<OpenAiConfig>
 *
 * 那么 apply() 的 config
 * 在编译期就一定是 OpenAiConfig。
 */
interface Plugin<C : Any> {

    /**
     * 当前 Plugin 声明的 Service 注入要求。
     *
     *
     * 现在 dependency 分为两种：
     *
     * required：
     *
     * Service 不存在
     *     ↓
     * Plugin 不能启动
     *
     *
     * optional：
     *
     * Service 不存在
     *     ↓
     * Plugin 仍然可以启动
     *
     * 但是 optional Service 后续出现 / 消失 / 替换，
     * 仍然会触发 Plugin reload。
     *
     *
     * 默认：
     *
     * Plugin 没有任何 Service dependency。
     */
    val inject: Set<InjectSpec>
        get() = emptySet()

    /**
     * Fiber 真正启动时调用。
     *
     *
     * context：
     *
     * 当前 Fiber 自己的 Context。
     *
     *
     * config：
     *
     * 当前 Fiber 自己的配置。
     *
     *
     * scope：
     *
     * 当前 Plugin 生命周期产生的副作用
     * 都应该注册到 EffectScope。
     *
     * 例如：
     *
     * scope.add(
     *     context.provide(...)
     * )
     *
     * scope.add(
     *     context.events.on(...)
     * )
     *
     *
     * Plugin deactivate / stop 时，
     * Runtime 会统一 dispose scope。
     */
    suspend fun apply(
        context: Context,
        config: C,
        scope: EffectScope,
    )
}

/**
 * 不需要配置的 Plugin。
 *
 * SimplePlugin 本质就是：
 *
 * Plugin<Unit>
 *
 * 只是把 Unit 对调用者隐藏掉，
 * 让简单 Plugin 写起来更自然。
 */
interface SimplePlugin : Plugin<Unit> {

    /**
     * SimplePlugin 只需要实现这个版本。
     */
    suspend fun apply(
        context: Context,
        scope: EffectScope,
    )

    /**
     * Plugin<Unit> 的真正实现。
     *
     * config == Unit，
     * 所以直接转发到简单版本。
     */
    override suspend fun apply(
        context: Context,
        config: Unit,
        scope: EffectScope,
    ) {
        apply(
            context = context,
            scope = scope,
        )
    }
}
