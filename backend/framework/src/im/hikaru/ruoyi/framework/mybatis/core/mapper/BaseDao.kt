package im.hikaru.ruoyi.framework.mybatis.core.mapper

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.pojo.SortingField
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Query

/**
 * 分页查询扩展函数 (Exposed 版，替代 MyBatis-Plus BaseMapperX.selectPage)
 *
 * 迁移说明：
 *  - MyBatis-Plus 的 `selectPage(PageParam, Wrapper)` → Exposed 的 `Query.paginate()`
 *  - `LambdaQueryWrapper` 条件构造 → Exposed 类型安全的 `Op<Boolean>` DSL
 *  - 分页用 Exposed `Query.limit().offset()` + `Query.count()`
 *
 * @author 芋道源码
 */

/**
 * 根据分页参数，对 Exposed [Query] 进行分页。
 *
 * 特殊：[PageParam.PAGE_SIZE_NONE] 表示不分页，查询全部数据。
 *
 * 示例：
 * ```
 * UserTable.selectAll()
 *     .where { UserTable.deleted eq false }
 *     .paginate(pageParam) { UserTable.id }
 *     .map { UserEntity(it) }
 * ```
 *
 * @param pageParam 分页参数
 * @param countExpr 计算总数的表达式 (一般用表的主键，或直接用 Query.count())
 * @return Pair<总数, 当前页数据>，供业务层封装为 PageResult
 */
fun Query.paginate(pageParam: PageParam): Pair<Long, Query> {
    val total = this.count()
    // 特殊：不分页，直接查询全部
    if (PageParam.PAGE_SIZE_NONE == pageParam.pageSize) {
        return total to this
    }
    val paged = this.offset(pageParam.offset()).limit(pageParam.pageSize)
    return total to paged
}

/**
 * 将 Exposed 分页查询结果转为 [PageResult]。
 *
 * @param pageParam 分页参数
 * @param mapper 将 [Query] 的每一行映射为目标类型
 */
fun <T> Query.toPageResult(pageParam: PageParam, mapper: (org.jetbrains.exposed.v1.core.ResultRow) -> T): PageResult<T> {
    val (total, paged) = this.paginate(pageParam)
    val list = paged.map(mapper)
    return PageResult(total = total, list = list)
}

/**
 * 根据分页参数计算 offset (0-based)。
 */
fun PageParam.offset(): Long = (pageNo.toLong() - 1) * pageSize.toLong()

/**
 * 将 [SortingField] 列表转为 Exposed 的排序对 (Expression, SortOrder)。
 *
 * @param columnResolver 字段名 → Expression<*> 解析器 (业务层根据 [BaseTable] 的列定义提供)
 */
fun Collection<SortingField>?.toSortPairs(
    columnResolver: (String) -> Expression<*>?,
): List<Pair<Expression<*>, SortOrder>> {
    if (this.isNullOrEmpty()) return emptyList()
    val pairs = ArrayList<Pair<Expression<*>, SortOrder>>()
    for (field in this) {
        val fieldName = field.field ?: continue
        val column = columnResolver(fieldName) ?: continue
        val order = if (SortingField.ORDER_ASC == field.order) SortOrder.ASC else SortOrder.DESC
        pairs.add(column to order)
    }
    return pairs
}
