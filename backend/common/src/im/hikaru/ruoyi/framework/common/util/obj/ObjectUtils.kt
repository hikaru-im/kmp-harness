package im.hikaru.ruoyi.framework.common.util.obj

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import java.util.function.Consumer

/**
 * Object 工具类 (迁移自 Java, 去 Hutool ObjectUtil/ReflectUtil)
 *
 * 迁移说明：
 *  - Hutool ObjectUtil.clone (深拷贝) → Jackson 序列化/反序列化实现 clone
 *  - Hutool ReflectUtil.getField/setFieldValue → JDK 反射
 *  - Hutool ObjectUtil.isAllEmpty → 手写判空
 *
 * @author 芋道源码
 */
object ObjectUtils {

    /**
     * 复制对象，并忽略 Id 编号
     *
     * 使用 Jackson 序列化/反序列化实现深拷贝 (替代 Hutool ObjectUtil.clone)
     *
     * @param object 被复制对象
     * @param consumer 消费者，可以二次编辑被复制对象
     * @return 复制后的对象
     */
    @JvmStatic
    fun <T : Any> cloneIgnoreId(obj: T, consumer: Consumer<in T>): T? {
        // 通过 Jackson 深拷贝
        val json = JsonUtils.toJsonString(obj)
        val result = JsonUtils.parseObject(json, obj.javaClass) ?: return null
        @Suppress("UNCHECKED_CAST")
        val typedResult = result as T
        // 忽略 id 编号 (反射)
        val field = findField(typedResult.javaClass, "id")
        if (field != null) {
            field.isAccessible = true
            field.set(typedResult, null)
        }
        // 二次编辑
        consumer.accept(typedResult)
        return typedResult
    }

    @JvmStatic
    fun <T : Comparable<T>> max(obj1: T?, obj2: T?): T? {
        if (obj1 == null) return obj2
        if (obj2 == null) return obj1
        return if (obj1 > obj2) obj1 else obj2
    }

    @SafeVarargs
    @JvmStatic
    fun <T> defaultIfNull(vararg array: T): T? {
        for (item in array) {
            if (item != null) {
                return item
            }
        }
        return null
    }

    @SafeVarargs
    @JvmStatic
    fun <T> equalsAny(obj: T, vararg array: T): Boolean =
        array.asList().contains(obj)

    @SafeVarargs
    @JvmStatic
    fun <T> notEqualsAny(obj: T, vararg array: T): Boolean =
        !array.asList().contains(obj)

    @JvmStatic
    fun isNotAllEmpty(vararg objs: Any?): Boolean {
        // Hutool ObjectUtil.isAllEmpty: 全部为空时返回 true; 这里取反即"至少有一个非空"
        return objs.any { !isEmpty(it) }
    }

    /**
     * 判断单个对象是否为空 (替代 Hutool ObjectUtil.isEmpty 的常用场景)
     */
    private fun isEmpty(obj: Any?): Boolean = when (obj) {
        null -> true
        is CharSequence -> obj.toString().isEmpty()
        is Collection<*> -> obj.isEmpty()
        is Array<*> -> obj.isEmpty()
        is Map<*, *> -> obj.isEmpty()
        else -> false
    }

    /**
     * 递归查找字段 (包括父类), 替代 Hutool ReflectUtil.getField
     */
    private fun findField(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
