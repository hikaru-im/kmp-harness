package im.hikaru.harness.loader

import kotlinx.serialization.json.JsonObject

/**
 * 把 Loader Entry 中的无类型配置转换成 Plugin 的强类型配置。
 *
 * Loader 不猜测序列化格式；YAML、JSON 或数据库记录应先由上层解析，
 * 再通过每个注册项自己的 ConfigAdapter 完成边界校验。
 */
fun interface ConfigAdapter<C : Any> {

    fun decode(value: Any?): C

    companion object {

        /** 无配置 Plugin 的严格适配器。 */
        fun unit(): ConfigAdapter<Unit> =
            ConfigAdapter { value ->
                check(value == null || value === Unit) {
                    "A SimplePlugin does not accept config"
                }

                Unit
            }
    }
}

/**
 * 为已经反序列化成目标 Kotlin 类型的配置建立适配器。
 */
inline fun <reified C : Any> typedConfig(): ConfigAdapter<C> =
    ConfigAdapter { value ->
        value as? C
            ?: error(
                "Expected config '${C::class.simpleName}', " +
                    "but received '${value?.let { it::class.simpleName } ?: "null"}'"
            )
    }

/**
 * Decodes a JSON-shaped configuration object produced by a profile codec.
 * Missing config may be treated as an empty object for plugins with defaults.
 */
public fun <C : Any> jsonObjectConfig(
    allowMissing: Boolean = false,
    decode: (JsonObject) -> C,
): ConfigAdapter<C> =
    ConfigAdapter { value ->
        val objectValue =
            when {
                value is JsonObject -> value
                value == null && allowMissing -> JsonObject(emptyMap())
                else ->
                    error(
                        "Expected plugin config object, but received " +
                            "'${value?.let { it::class.simpleName } ?: "null"}'"
                    )
            }
        decode(objectValue)
    }
