package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.service.ServiceKey

/**
 * 描述 Plugin 对一个 Service 的依赖关系。
 *
 * 以前我们的 Plugin 只有：
 *
 * inject = setOf(
 *     LlmKey,
 *     ToolsKey,
 * )
 *
 * 所有 dependency 都被认为是“必需”的。
 *
 *
 * 现在升级为：
 *
 * InjectSpec.required(LlmKey)
 * InjectSpec.optional(ToolsKey)
 *
 *
 * required：
 *
 * Service 不存在时，
 * Plugin 不能启动。
 *
 *
 * optional：
 *
 * Service 不存在时，
 * Plugin 仍然可以启动。
 *
 * 但是 optional Service：
 *
 * - 出现
 * - 消失
 * - implementation 改变
 *
 * 仍然会触发 Plugin reload。
 *
 *
 * 例如：
 *
 * AgentPlugin
 *
 * required:
 *     LlmKey
 *
 * optional:
 *     ToolsKey
 *     MemoryKey
 *
 *
 * 没有 LLM：
 *
 * Agent -> Pending
 *
 *
 * 没有 Tools：
 *
 * Agent -> Active
 *
 *
 * Tools 后来出现：
 *
 * Agent -> reload
 */
data class InjectSpec(

    /**
     * 依赖哪一种 Service。
     */
    val key: ServiceKey<*>,

    /**
     * 是否属于必需依赖。
     *
     * true:
     *     Service 不存在时不能启动。
     *
     * false:
     *     Service 不存在时仍然可以启动。
     */
    val required: Boolean,
) {

    companion object {

        /**
         * 创建必需依赖。
         */
        fun required(
            key: ServiceKey<*>,
        ): InjectSpec {
            return InjectSpec(
                key = key,
                required = true,
            )
        }

        /**
         * 创建可选依赖。
         */
        fun optional(
            key: ServiceKey<*>,
        ): InjectSpec {
            return InjectSpec(
                key = key,
                required = false,
            )
        }
    }
}