package im.hikaru.ruoyi.framework.common.util.beans

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.collection.CollectionUtils
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import org.springframework.beans.BeanUtils as SpringBeanUtils
import java.util.function.Consumer

/**
 * Bean 工具类
 *
 * 迁移说明：
 *  - 包名由 Java 版 `util.object` 改为 `util.beans`（避免与 Kotlin `object` 关键字冲突）
 *  - 原实现基于 Hutool BeanUtil，现统一改为通过 Jackson 转换（避免引入 Hutool）
 *  - 针对复杂的对象转换，可以参考 AuthConvert 实现，通过手写 Converter 配合实现
 *
 * @author 芋道源码
 */
object BeanUtils {

    @JvmStatic
    fun <T> toBean(source: Any?, targetClass: Class<T>): T? {
        if (source == null) return null
        return JsonUtils.convertObject(source, targetClass)
    }

    @JvmStatic
    fun <T> toBean(source: Any?, targetClass: Class<T>, peek: Consumer<in T>): T? {
        val target = toBean(source, targetClass)
        target?.let { peek.accept(it) }
        return target
    }

    @JvmStatic
    fun <S, T> toBean(source: List<S>?, targetType: Class<T>): List<T>? {
        if (source == null) return null
        return CollectionUtils.convertList(source) { toBean(it, targetType)!! }
    }

    @JvmStatic
    fun <S, T> toBean(source: List<S>?, targetType: Class<T>, peek: Consumer<in T>): List<T>? {
        val list = toBean(source, targetType)
        list?.forEach(peek)
        return list
    }

    @JvmStatic
    fun <S, T> toBean(source: PageResult<S>?, targetType: Class<T>): PageResult<T>? =
        toBean(source, targetType, null)

    @JvmStatic
    fun <S, T> toBean(
        source: PageResult<S>?,
        targetType: Class<T>,
        peek: Consumer<in T>?,
    ): PageResult<T>? {
        if (source == null) return null
        val list = toBean(source.list, targetType)
        if (list != null && peek != null) {
            list.forEach(peek)
        }
        return PageResult(total = source.total ?: 0L, list = list ?: ArrayList())
    }

    @JvmStatic
    fun copyProperties(source: Any?, target: Any?) {
        if (source == null || target == null) {
            return
        }
        // 通过 Jackson 转换后用 Spring BeanUtils 拷贝到 target
        val copied = JsonUtils.convertObject(source, target.javaClass) ?: return
        SpringBeanUtils.copyProperties(copied, target)
    }
}
