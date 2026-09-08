package im.hikaru.harness.runtime.intercept

/**
 * 类型安全的 Context intercept 配置键。
 *
 * merge 决定父级配置与更近一层配置如何组合；默认由最近一层完全覆盖。
 */
open class InterceptKey<T : Any>(
    val name: String,
    private val merge: (parent: T, child: T) -> T = { _, child -> child },
) {

    internal fun resolve(
        values: List<T>,
    ): T? {
        var result: T? =
            null

        for (value in values) {
            result =
                result?.let { parent ->
                    merge(parent, value)
                } ?: value
        }

        return result
    }

    override fun toString(): String {
        return "InterceptKey($name)"
    }
}
