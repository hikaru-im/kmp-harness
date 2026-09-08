package im.hikaru.ruoyi.framework.common.util.collection

import im.hikaru.ruoyi.framework.common.core.KeyValue
import java.math.BigDecimal
import java.util.function.Consumer

object MapUtils {

    @JvmStatic
    fun <K, V> getList(multimap: Map<K, out Collection<V>>, keys: Collection<K>): List<V> =
        keys.flatMap { multimap[it].orEmpty() }

    @JvmStatic
    fun <K, V> findAndThen(map: Map<K, V>?, key: K?, consumer: Consumer<in V>) {
        if (map.isNullOrEmpty() || key == null) return
        map[key]?.let(consumer::accept)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <K, V> convertMap(keyValues: List<KeyValue<K, V>>): Map<K, V> = buildMap {
        keyValues.forEach { put(it.key as K, it.value as V) }
    }

    @JvmStatic
    fun getBigDecimal(map: Map<String, *>?, key: String): BigDecimal? = getBigDecimal(map, key, null)

    @JvmStatic
    fun getBigDecimal(map: Map<String, *>?, key: String, defaultValue: BigDecimal?): BigDecimal? {
        val value = map?.get(key) ?: return defaultValue
        return when (value) {
            is BigDecimal -> value
            is Number -> value.toString().toBigDecimalOrNull() ?: defaultValue
            is String -> value.toBigDecimalOrNull() ?: defaultValue
            else -> defaultValue
        }
    }
}
