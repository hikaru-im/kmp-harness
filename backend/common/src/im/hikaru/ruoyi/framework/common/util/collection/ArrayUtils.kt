package im.hikaru.ruoyi.framework.common.util.collection

import java.lang.reflect.Array as ReflectArray
import java.util.function.Consumer
import java.util.function.Function

object ArrayUtils {

    @JvmStatic
    fun <T> append(element: Consumer<T>?, vararg newElements: Consumer<T>): Array<Consumer<T>> =
        if (element == null) arrayOf(*newElements) else arrayOf(element, *newElements)

    @JvmStatic
    fun <T, V> toArray(from: Collection<T>?, mapper: Function<in T, out V>): Array<V> =
        toArray(from.orEmpty().map(mapper::apply))

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> toArray(from: Collection<T>?): Array<T> {
        if (from.isNullOrEmpty()) return arrayOfNulls<Any>(0) as Array<T>
        val componentType = from.firstOrNull { it != null }?.let { (it as Any).javaClass } ?: Any::class.java
        val result = ReflectArray.newInstance(componentType, from.size) as Array<T>
        from.forEachIndexed { index, value -> result[index] = value }
        return result
    }

    @JvmStatic
    fun <T> get(array: Array<T>?, index: Int): T? = array?.getOrNull(index)
}
