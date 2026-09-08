package im.hikaru.ruoyi.framework.mybatis.core.type

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/**
 * List<Long> 的 Exposed 列类型 (迁移自 MyBatis-Plus LongListTypeHandler)
 *
 * 数据库存储为 varchar (逗号分隔)，Kotlin 侧为 List<Long>。
 *
 * @author 芋道源码
 */
class LongListColumnType(
    private val colLength: Int = 255,
) : ColumnType<List<Long>>() {

    override fun sqlType(): String = if (colLength > 0) "VARCHAR($colLength)" else "TEXT"

    override fun valueFromDB(value: Any): List<Long> = when (value) {
        is List<*> -> value.mapNotNull { (it as? Number)?.toLong() }
        is String -> value.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
        is java.sql.Array -> valueFromDB(value.array as Any)
        else -> error("$value (${value::class}) is not a valid value for LongListColumnType")
    }

    override fun notNullValueToDB(value: List<Long>): Any =
        value.filterIsInstance<Number>().joinToString(",") { it.toString() }

    override fun nonNullValueToString(value: List<Long>): String =
        "'${value.joinToString(",")}'"
}

/**
 * 创建 List<Long> 列 (扩展函数)。
 */
fun Table.longList(name: String, colLength: Int = 255): Column<List<Long>> =
    registerColumn(name, LongListColumnType(colLength))
