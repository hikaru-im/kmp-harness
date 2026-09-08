package im.hikaru.ruoyi.module.system.dal.mysql

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

class JsonStringListColumnType(private val length: Int = 255) : ColumnType<List<String>>() {
    override fun sqlType() = "VARCHAR($length)"
    override fun valueFromDB(value: Any): List<String> = when (value) { is String -> JsonUtils.parseArray(value, String::class.java); is Collection<*> -> value.filterIsInstance<String>(); else -> emptyList() }
    override fun notNullValueToDB(value: List<String>): Any = JsonUtils.toJsonString(value)
    override fun nonNullValueToString(value: List<String>) = "'${JsonUtils.toJsonString(value).replace("'", "''")}'"
}

class JsonStringMapColumnType(private val length: Int = 512) : ColumnType<Map<String, String>>() {
    override fun sqlType() = "VARCHAR($length)"
    override fun valueFromDB(value: Any): Map<String, String> = when (value) { is String -> JsonUtils.parseMap(value)?.mapValues { it.value?.toString().orEmpty() }.orEmpty(); is Map<*, *> -> value.entries.associate { it.key.toString() to it.value.toString() }; else -> emptyMap() }
    override fun notNullValueToDB(value: Map<String, String>): Any = JsonUtils.toJsonString(value)
    override fun nonNullValueToString(value: Map<String, String>) = "'${JsonUtils.toJsonString(value).replace("'", "''")}'"
}

class JsonAnyMapColumnType(private val length: Int = 512) : ColumnType<Map<String, Any?>>() {
    override fun sqlType() = "VARCHAR($length)"
    override fun valueFromDB(value: Any): Map<String, Any?> = when (value) {
        is String -> JsonUtils.parseMap(value).orEmpty()
        is Map<*, *> -> value.entries.associate { it.key.toString() to it.value }
        else -> emptyMap()
    }
    override fun notNullValueToDB(value: Map<String, Any?>): Any = JsonUtils.toJsonString(value)
    override fun nonNullValueToString(value: Map<String, Any?>) = "'${JsonUtils.toJsonString(value).replace("'", "''")}'"
}

fun Table.jsonStringList(name: String, length: Int = 255): Column<List<String>> = registerColumn(name, JsonStringListColumnType(length))
fun Table.jsonStringMap(name: String, length: Int = 512): Column<Map<String, String>> = registerColumn(name, JsonStringMapColumnType(length))
fun Table.jsonAnyMap(name: String, length: Int = 512): Column<Map<String, Any?>> = registerColumn(name, JsonAnyMapColumnType(length))
