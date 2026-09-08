package im.hikaru.ruoyi.framework.common.util.`object`

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.pojo.SortablePageParam
import im.hikaru.ruoyi.framework.common.pojo.SortingField
import java.util.function.Consumer
import kotlin.reflect.KProperty1

object BeanUtils {
    @JvmStatic fun <T> toBean(source: Any?, targetClass: Class<T>): T? =
        im.hikaru.ruoyi.framework.common.util.beans.BeanUtils.toBean(source, targetClass)
    @JvmStatic fun <T> toBean(source: Any?, targetClass: Class<T>, peek: Consumer<in T>): T? =
        im.hikaru.ruoyi.framework.common.util.beans.BeanUtils.toBean(source, targetClass, peek)
    @JvmStatic fun <S, T> toBean(source: List<S>?, targetClass: Class<T>): List<T>? =
        im.hikaru.ruoyi.framework.common.util.beans.BeanUtils.toBean(source, targetClass)
    @JvmStatic fun <S, T> toBean(source: PageResult<S>?, targetClass: Class<T>): PageResult<T>? =
        im.hikaru.ruoyi.framework.common.util.beans.BeanUtils.toBean(source, targetClass)
    @JvmStatic fun copyProperties(source: Any?, target: Any?) =
        im.hikaru.ruoyi.framework.common.util.beans.BeanUtils.copyProperties(source, target)
}

object ObjectUtils {
    @JvmStatic fun <T : Any> cloneIgnoreId(value: T, consumer: Consumer<in T>): T? =
        im.hikaru.ruoyi.framework.common.util.obj.ObjectUtils.cloneIgnoreId(value, consumer)
    @JvmStatic fun <T : Comparable<T>> max(first: T?, second: T?): T? =
        im.hikaru.ruoyi.framework.common.util.obj.ObjectUtils.max(first, second)
    @JvmStatic fun <T> defaultIfNull(vararg values: T): T? =
        im.hikaru.ruoyi.framework.common.util.obj.ObjectUtils.defaultIfNull(*values)
    @JvmStatic fun <T> equalsAny(value: T, vararg candidates: T): Boolean = value in candidates
    @JvmStatic fun <T> notEqualsAny(value: T, vararg candidates: T): Boolean = value !in candidates
    @JvmStatic fun isNotAllEmpty(vararg values: Any?): Boolean =
        im.hikaru.ruoyi.framework.common.util.obj.ObjectUtils.isNotAllEmpty(*values)
}

object PageUtils {
    @JvmStatic fun getStart(pageParam: PageParam): Int = (pageParam.pageNo - 1) * pageParam.pageSize
    @JvmStatic @JvmOverloads
    fun buildSortingField(field: String, order: String = SortingField.ORDER_DESC): SortingField {
        require(order == SortingField.ORDER_ASC || order == SortingField.ORDER_DESC) { "order must be asc or desc" }
        return SortingField(field, order)
    }
    fun <T> buildSortingField(property: KProperty1<T, *>, order: String = SortingField.ORDER_DESC): SortingField =
        buildSortingField(property.name, order)
    @JvmStatic fun buildDefaultSortingField(pageParam: SortablePageParam?, field: String) {
        if (pageParam != null && pageParam.sortingFields.isNullOrEmpty()) pageParam.sortingFields = listOf(buildSortingField(field))
    }
    fun <T> buildDefaultSortingField(pageParam: SortablePageParam?, property: KProperty1<T, *>) =
        buildDefaultSortingField(pageParam, property.name)
}
