package im.hikaru.ruoyi.framework.common.util.collection

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import java.util.Collections
import java.util.LinkedList
import java.util.Objects
import java.util.function.BiFunction
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Collection 工具类
 *
 * 迁移自 Java 版：Hutool (CollUtil/ArrayUtil) → Kotlin stdlib，Guava ImmutableMap → Collections.unmodifiableMap
 *
 * @author 芋道源码
 */
object CollectionUtils {

    @JvmStatic
    fun containsAny(source: Any, vararg targets: Any): Boolean = listOf(*targets).contains(source)

    @JvmStatic
    fun isAnyEmpty(vararg collections: Collection<*>): Boolean = collections.any { it.isEmpty() }

    @JvmStatic
    fun <T> anyMatch(from: Collection<T>, predicate: Predicate<in T>): Boolean =
        from.stream().anyMatch(predicate)

    @JvmStatic
    fun <T> filterList(from: Collection<T>?, predicate: Predicate<in T>): List<T> {
        if (from.isNullOrEmpty()) return ArrayList()
        return from.stream().filter(predicate).collect(Collectors.toList())
    }

    @JvmStatic
    fun <T, R> distinct(from: Collection<T>?, keyMapper: Function<in T, out R>): List<T> {
        if (from.isNullOrEmpty()) return ArrayList()
        return distinct(from, keyMapper) { t1, _ -> t1 }
    }

    @JvmStatic
    fun <T, R> distinct(
        from: Collection<T>?,
        keyMapper: Function<in T, out R>,
        cover: BinaryOperator<T>,
    ): List<T> {
        if (from.isNullOrEmpty()) return ArrayList()
        return ArrayList(convertMap(from, keyMapper, Function.identity(), cover).values)
    }

    @JvmStatic
    fun <T, U> convertList(from: Array<T>?, func: Function<in T, out U>): List<U> {
        if (from == null || from.isEmpty()) return ArrayList()
        return convertList(from.asList(), func)
    }

    @JvmStatic
    fun <T, U> convertList(from: Collection<T>?, func: Function<in T, out U>): List<U> {
        if (from.isNullOrEmpty()) return ArrayList()
        return from.stream().map(func).filter { Objects.nonNull(it) }.collect(Collectors.toList())
    }

    @JvmStatic
    fun <T, U> convertList(
        from: Collection<T>?,
        func: Function<in T, out U>,
        filter: Predicate<in T>,
    ): List<U> {
        if (from.isNullOrEmpty()) return ArrayList()
        return from.stream().filter(filter).map(func).filter { Objects.nonNull(it) }
            .collect(Collectors.toList())
    }

    @JvmStatic
    fun <T, U> convertPage(from: PageResult<T>?, func: Function<in T, out U>): PageResult<U> {
        if (from == null || from.list.isNullOrEmpty()) return PageResult(from?.total ?: 0L)
        return PageResult(total = from.total ?: 0L, list = convertList(from.list, func))
    }

    @JvmStatic
    fun <T, U> convertListByFlatMap(
        from: Collection<T>?,
        func: Function<in T, out Stream<out U>>,
    ): List<U> {
        if (from.isNullOrEmpty()) return ArrayList()
        return from.stream().filter { Objects.nonNull(it) }.flatMap(func).filter { Objects.nonNull(it) }
            .collect(Collectors.toList())
    }

    @JvmStatic
    fun <T, U, R> convertListByFlatMap(
        from: Collection<T>?,
        mapper: Function<in T, out U>,
        func: Function<in U, out Stream<out R>>,
    ): List<R> {
        if (from.isNullOrEmpty()) return ArrayList()
        return from.stream().map(mapper).filter { Objects.nonNull(it) }.flatMap(func)
            .filter { Objects.nonNull(it) }.collect(Collectors.toList())
    }

    @JvmStatic
    fun <K, V> mergeValuesFromMap(map: Map<K, List<V>>): List<V> =
        map.values.stream().flatMap { it.stream() }.collect(Collectors.toList())

    @JvmStatic
    fun <T> convertSet(from: Collection<T>?): Set<T> = convertSet(from) { it }

    @JvmStatic
    fun <T, U> convertSet(from: Collection<T>?, func: Function<in T, out U>): Set<U> {
        if (from.isNullOrEmpty()) return HashSet()
        return from.stream().map(func).filter { Objects.nonNull(it) }.collect(Collectors.toSet())
    }

    @JvmStatic
    fun <T, U> convertSet(
        from: Collection<T>?,
        func: Function<in T, out U>,
        filter: Predicate<in T>,
    ): Set<U> {
        if (from.isNullOrEmpty()) return HashSet()
        return from.stream().filter(filter).map(func).filter { Objects.nonNull(it) }
            .collect(Collectors.toSet())
    }

    @JvmStatic
    fun <T, U> convertLinkedSet(from: Collection<T>?, func: Function<in T, out U>): Set<U> {
        if (from.isNullOrEmpty()) return java.util.LinkedHashSet()
        return from.stream().map(func).filter { Objects.nonNull(it) }
            .collect(Collectors.toCollection { java.util.LinkedHashSet() })
    }

    @JvmStatic
    fun <T, U> convertLinkedSet(
        from: Collection<T>?,
        func: Function<in T, out U>,
        filter: Predicate<in T>,
    ): Set<U> {
        if (from.isNullOrEmpty()) return java.util.LinkedHashSet()
        return from.stream().filter(filter).map(func).filter { Objects.nonNull(it) }
            .collect(Collectors.toCollection { java.util.LinkedHashSet() })
    }

    @JvmStatic
    fun <T, K> convertMapByFilter(
        from: Collection<T>?,
        filter: Predicate<in T>,
        keyFunc: Function<in T, out K>,
    ): Map<K, T> {
        if (from.isNullOrEmpty()) return HashMap()
        return from.stream().filter(filter).collect(Collectors.toMap(keyFunc) { it })
    }

    @JvmStatic
    fun <T, U> convertSetByFlatMap(
        from: Collection<T>?,
        func: Function<in T, out Stream<out U>>,
    ): Set<U> {
        if (from.isNullOrEmpty()) return HashSet()
        return from.stream().filter { Objects.nonNull(it) }.flatMap(func).filter { Objects.nonNull(it) }
            .collect(Collectors.toSet())
    }

    @JvmStatic
    fun <T, U, R> convertSetByFlatMap(
        from: Collection<T>?,
        mapper: Function<in T, out U>,
        func: Function<in U, out Stream<out R>>,
    ): Set<R> {
        if (from.isNullOrEmpty()) return HashSet()
        return from.stream().map(mapper).filter { Objects.nonNull(it) }.flatMap(func)
            .filter { Objects.nonNull(it) }.collect(Collectors.toSet())
    }

    @JvmStatic
    fun <T, K> convertMap(from: Collection<T>?, keyFunc: Function<in T, out K>): Map<K, T> =
        convertMap(from, keyFunc, Function.identity())

    @JvmStatic
    fun <T, K> convertMap(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        supplier: Supplier<MutableMap<K, T>>,
    ): MutableMap<K, T> = convertMap(from, keyFunc, Function.identity(), supplier)

    @JvmStatic
    fun <T, K, V> convertMap(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        valueFunc: Function<in T, out V>,
    ): Map<K, V> = convertMap(from, keyFunc, valueFunc) { v1, _ -> v1 }

    @JvmStatic
    fun <T, K, V> convertMap(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        valueFunc: Function<in T, out V>,
        mergeFunction: BinaryOperator<V>,
    ): Map<K, V> = convertMap(from, keyFunc, valueFunc, mergeFunction) { HashMap() }

    @JvmStatic
    fun <T, K, V> convertMap(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        valueFunc: Function<in T, out V>,
        supplier: Supplier<MutableMap<K, V>>,
    ): MutableMap<K, V> = convertMap(from, keyFunc, valueFunc, { v1, _ -> v1 }, supplier)

    @JvmStatic
    fun <T, K, V> convertMap(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        valueFunc: Function<in T, out V>,
        mergeFunction: BinaryOperator<V>,
        supplier: Supplier<MutableMap<K, V>>,
    ): MutableMap<K, V> {
        if (from.isNullOrEmpty()) return supplier.get()
        return from.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunction, supplier))
    }

    @JvmStatic
    fun <T, K> convertMultiMap(from: Collection<T>?, keyFunc: Function<in T, out K>): Map<K, List<T>> {
        if (from.isNullOrEmpty()) return HashMap()
        return from.stream().collect(
            Collectors.groupingBy(keyFunc, Collectors.mapping({ it }, Collectors.toList())),
        )
    }

    @JvmStatic
    fun <T, K, V> convertMultiMap(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        valueFunc: Function<in T, out V>,
    ): Map<K, List<V>> {
        if (from.isNullOrEmpty()) return HashMap()
        return from.stream()
            .collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toList())))
    }

    // 暂时没想好名字，先以 2 结尾噶
    @JvmStatic
    fun <T, K, V> convertMultiMap2(
        from: Collection<T>?,
        keyFunc: Function<in T, out K>,
        valueFunc: Function<in T, out V>,
    ): Map<K, Set<V>> {
        if (from.isNullOrEmpty()) return HashMap()
        return from.stream()
            .collect(Collectors.groupingBy(keyFunc, Collectors.mapping(valueFunc, Collectors.toSet())))
    }

    @JvmStatic
    fun <T, K> convertImmutableMap(from: Collection<T>?, keyFunc: Function<in T, out K>): Map<K, T> {
        if (from.isNullOrEmpty()) return emptyMap()
        // Guava ImmutableMap → Collections.unmodifiableMap
        val map = LinkedHashMap<K, T>()
        from.forEach { map[keyFunc.apply(it)] = it }
        return Collections.unmodifiableMap(map)
    }

    /**
     * 对比老、新两个列表，找出新增、修改、删除的数据
     *
     * @param oldList 老列表
     * @param newList 新列表
     * @param sameFunc 对比函数，返回 true 表示相同，返回 false 表示不同
     *                 注意，same 是通过每个元素的"标识"，判断它们是不是同一个数据
     * @return [新增列表、修改列表、删除列表]
     */
    @JvmStatic
    fun <T> diffList(
        oldList: Collection<T>?,
        newList: Collection<T>?,
        sameFunc: BiFunction<in T, in T, Boolean>,
    ): List<List<T>> {
        val createList = LinkedList(newList ?: emptyList()) // 默认都认为是新增的，后续会进行移除
        val updateList = ArrayList<T>()
        val deleteList = ArrayList<T>()

        // 通过以 oldList 为主遍历，找出 updateList 和 deleteList
        outer@ for (oldObj in (oldList ?: emptyList())) {
            // 1. 寻找是否有匹配的
            var foundObj: T? = null
            val iterator = createList.iterator()
            while (iterator.hasNext()) {
                val newObj = iterator.next()
                // 1.1 不匹配，则直接跳过
                if (!sameFunc.apply(oldObj, newObj)) {
                    continue
                }
                // 1.2 匹配，则移除，并结束寻找
                iterator.remove()
                foundObj = newObj
                break
            }
            // 2. 匹配添加到 updateList；不匹配则添加到 deleteList 中
            if (foundObj != null) {
                updateList.add(foundObj)
            } else {
                deleteList.add(oldObj)
            }
        }
        return listOf(createList, updateList, deleteList)
    }

    @JvmStatic
    fun containsAny(source: Collection<*>?, candidates: Collection<*>?): Boolean {
        if (source == null || candidates == null) return false
        return org.springframework.util.CollectionUtils.containsAny(source, candidates)
    }

    @JvmStatic
    fun <T> getFirst(from: List<T>?): T? = if (!from.isNullOrEmpty()) from[0] else null

    @JvmStatic
    fun <T> findFirst(from: Collection<T>?, predicate: Predicate<in T>): T? =
        findFirst(from, predicate) { it }

    @JvmStatic
    fun <T, U : Any> findFirst(
        from: Collection<T>?,
        predicate: Predicate<in T>,
        func: Function<in T, out U?>,
    ): U? {
        if (from.isNullOrEmpty()) return null
        return from.stream().filter(predicate).findFirst().map { func.apply(it) }.orElse(null)
    }

    @JvmStatic
    fun <T, V : Comparable<in V>> getMaxValue(from: Collection<T>?, valueFunc: Function<in T, out V>): V? {
        if (from.isNullOrEmpty()) return null
        assert(from.isNotEmpty()) // 断言，避免告警
        val t = from.stream().max(Comparator.comparing(valueFunc)).get()
        return valueFunc.apply(t)
    }

    @JvmStatic
    fun <T, V : Comparable<in V>> getMinValue(from: List<T>?, valueFunc: Function<in T, out V>): V? {
        if (from.isNullOrEmpty()) return null
        assert(from.isNotEmpty()) // 断言，避免告警
        val t = from.stream().min(Comparator.comparing(valueFunc)).get()
        return valueFunc.apply(t)
    }

    @JvmStatic
    fun <T, V : Comparable<in V>> getMinObject(from: List<T>?, valueFunc: Function<in T, out V>): T? {
        if (from.isNullOrEmpty()) return null
        assert(from.isNotEmpty()) // 断言，避免告警
        return from.stream().min(Comparator.comparing(valueFunc)).get()
    }

    @JvmStatic
    fun <T, V : Any> getSumValue(
        from: Collection<T>?,
        valueFunc: Function<in T, out V?>,
        accumulator: BinaryOperator<V>,
    ): V? = getSumValue(from, valueFunc, accumulator, null)

    @JvmStatic
    fun <T, V : Any> getSumValue(
        from: Collection<T>?,
        valueFunc: Function<in T, out V?>,
        accumulator: BinaryOperator<V>,
        defaultValue: V?,
    ): V? {
        if (from.isNullOrEmpty()) return defaultValue
        assert(from.isNotEmpty()) // 断言，避免告警
        return from.stream().map(valueFunc).filter { Objects.nonNull(it) }.reduce(accumulator)
            .orElse(defaultValue)
    }

    @JvmStatic
    fun <T> addIfNotNull(coll: MutableCollection<in T>, item: T?) {
        if (item == null) return
        coll.add(item)
    }

    @JvmStatic
    fun <T> singleton(obj: T?): Collection<T> = if (obj == null) emptyList() else Collections.singleton(obj)

    @JvmStatic
    fun <T> newArrayList(list: List<List<T>?>?): List<T> =
        (list ?: emptyList()).stream().filter { Objects.nonNull(it) }.flatMap { obj -> obj!!.stream() }
            .collect(Collectors.toList())

    /**
     * 把单元素 head 与集合 tail 合并成新 List（head 在前，tail 顺序保留）
     */
    @JvmStatic
    fun <T> of(head: T, tail: Collection<T>?): List<T> {
        val list = ArrayList<T>(1 + (tail?.size ?: 0))
        list.add(head)
        if (tail != null) list.addAll(tail)
        return list
    }
}
