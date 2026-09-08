package im.hikaru.ruoyi.framework.mybatis.core.util

import im.hikaru.ruoyi.framework.common.pojo.SortingField
import org.jetbrains.exposed.v1.core.SortOrder

/**
 * Exposed 查询工具类 (迁移自 MyBatisUtils 的排序/命名相关逻辑)
 *
 * 迁移说明：
 *  - MyBatis-Plus 的 Page.addOrder / Wrapper.orderBy → Exposed 的 `Query.orderBy(Expression, SortOrder)`
 *  - Hutool StrUtil.toUnderlineCase → Kotlin 手写 camelToUnderline
 *  - jsqlparser 的 Table/Column 解析 → Exposed 类型安全的 Column<T>，无需字符串解析
 *
 * 排序基于类型安全的 Expression，详见 [im.hikaru.ruoyi.framework.mybatis.core.mapper.toSortPairs]。
 */
private val SAFE_COLUMN_NAME_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*$")

/**
 * 将驼峰命名转换为下划线命名
 */
fun camelToUnderline(fieldName: String): String =
    fieldName.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        .lowercase()

/**
 * 构建安全的排序字段名 (驼峰→下划线，校验合法性)
 *
 * 用于动态 SQL / find_in_set 等仍需字符串字段名的场景。
 *
 * @return 合法的下划线字段名；非法返回 null
 */
fun buildSafeOrderColumn(field: String?): String? {
    if (field.isNullOrBlank()) return null
    val columnName = camelToUnderline(field)
    if (!SAFE_COLUMN_NAME_REGEX.matches(columnName)) return null
    return columnName
}

/**
 * 判断是否升序
 */
fun isAscOrder(order: String?): Boolean = SortingField.ORDER_ASC == order

/**
 * 获取排序方向 (Exposed 版)
 */
fun getOrderDirection(order: String?): SortOrder =
    if (isAscOrder(order)) SortOrder.ASC else SortOrder.DESC

/**
 * 跨数据库的 find_in_set SQL 构建
 *
 * 迁移自 MyBatisUtils.findInSet，用于 Exposed 的 `Query.adjustWhere` / 原生 SQL 片段中。
 *
 * @param dbType 数据库类型
 * @param columnName 字段名 (下划线命名)
 * @return 跨数据库的 find_in_set SQL 模板字符串 (含 {column}/{value} 占位符)
 */
fun findInSetTemplate(dbType: im.hikaru.ruoyi.framework.mybatis.core.enums.DbTypeEnum?): String =
    im.hikaru.ruoyi.framework.mybatis.core.enums.DbTypeEnum.getFindInSetTemplate(dbType)
