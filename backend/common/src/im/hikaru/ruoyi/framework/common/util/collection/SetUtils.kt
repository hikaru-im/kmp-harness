package im.hikaru.ruoyi.framework.common.util.collection

/**
 * Set 工具类 (迁移自 Java, 去 Hutool CollUtil → Kotlin stdlib)
 *
 * @author 芋道源码
 */
object SetUtils {

    @JvmStatic
    @SafeVarargs
    fun <T> asSet(vararg objs: T): Set<T> = HashSet(objs.asList())
}
