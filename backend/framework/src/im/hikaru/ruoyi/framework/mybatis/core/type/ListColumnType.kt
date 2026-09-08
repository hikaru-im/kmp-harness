package im.hikaru.ruoyi.framework.mybatis.core.type

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/**
 * List<String> 的 Exposed 列类型 (迁移自 MyBatis-Plus StringListTypeHandler)
 *
 * 数据库存储为 varchar (逗号分隔)，Kotlin 侧为 List<String>。
 *
 * @author 永不言败
 */
class StringListColumnType(
    private val colLength: Int = 255,
) : ColumnType<List<String>>() {

    override fun sqlType(): String = if (colLength > 0) "VARCHAR($colLength)" else "TEXT"

    override fun valueFromDB(value: Any): List<String> = when (value) {
        is List<*> -> value.filterIsInstance<String>()
        is String -> value.split(",").filter { it.isNotEmpty() }
        is java.sql.Array -> valueFromDB(value.array as Any)
        else -> error("$value (${value::class}) is not a valid value for StringListColumnType")
    }

    override fun notNullValueToDB(value: List<String>): Any =
        value.filterIsInstance<String>().joinToString(",")

    override fun nonNullValueToString(value: List<String>): String = "'${value.joinToString(",")}'"
}

/**
 * 创建 List<String> 列 (扩展函数)。
 *
 * 用法：`val tags = stringList("tags")`
 */
fun Table.stringList(name: String, colLength: Int = 255): Column<List<String>> =
    registerColumn(name, StringListColumnType(colLength))
