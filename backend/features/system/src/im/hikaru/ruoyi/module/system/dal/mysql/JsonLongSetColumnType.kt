package im.hikaru.ruoyi.module.system.dal.mysql

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

class JsonLongSetColumnType(private val length: Int = 500) : ColumnType<Set<Long>>() {
    override fun sqlType(): String = "VARCHAR($length)"
    override fun valueFromDB(value: Any): Set<Long> = when (value) {
        is String -> value.trim().removePrefix("[").removeSuffix("]").split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
        is Collection<*> -> value.mapNotNull { (it as? Number)?.toLong() }.toSet()
        else -> emptySet()
    }
    override fun notNullValueToDB(value: Set<Long>): Any = value.joinToString(prefix = "[", postfix = "]")
    override fun nonNullValueToString(value: Set<Long>): String = "'${notNullValueToDB(value)}'"
}

fun Table.jsonLongSet(name: String, length: Int = 500): Column<Set<Long>> = registerColumn(name, JsonLongSetColumnType(length))
