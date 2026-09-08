package im.hikaru.ruoyi.framework.mybatis.core.type

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/** JSON-backed column for string maps stored in a VARCHAR/TEXT compatible column. */
class JsonStringMapColumnType(
    private val length: Int = 1024,
) : ColumnType<Map<String, String>>() {

    override fun sqlType(): String = if (length > 0) "VARCHAR($length)" else "TEXT"

    override fun valueFromDB(value: Any): Map<String, String> = when (value) {
        is String -> JsonUtils.parseMap(value)?.mapValues { it.value?.toString().orEmpty() }.orEmpty()
        is Map<*, *> -> value.entries.associate { it.key.toString() to it.value?.toString().orEmpty() }
        else -> error("$value (${value::class}) is not a valid JSON string map")
    }

    override fun notNullValueToDB(value: Map<String, String>): Any = JsonUtils.toJsonString(value)

    override fun nonNullValueToString(value: Map<String, String>): String =
        "'${JsonUtils.toJsonString(value).replace("'", "''")}'"
}

/** JSON-backed column for a concrete or polymorphic object. */
class JsonObjectColumnType<T : Any>(
    private val javaType: Class<T>,
    private val length: Int = 4096,
) : ColumnType<T>() {

    override fun sqlType(): String = if (length > 0) "VARCHAR($length)" else "TEXT"

    override fun valueFromDB(value: Any): T = when {
        javaType.isInstance(value) -> javaType.cast(value)
        value is String -> requireNotNull(JsonUtils.parseObject(value, javaType))
        else -> error("$value (${value::class}) is not a valid ${javaType.simpleName}")
    }

    override fun notNullValueToDB(value: T): Any = JsonUtils.toJsonString(value)

    override fun nonNullValueToString(value: T): String =
        "'${JsonUtils.toJsonString(value).replace("'", "''")}'"
}

/** JSON-backed list column with an explicit element type for Jackson. */
class JsonListColumnType<T : Any>(
    private val elementType: Class<T>,
    private val length: Int = 4096,
) : ColumnType<List<T>>() {

    override fun sqlType(): String = if (length > 0) "VARCHAR($length)" else "TEXT"

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): List<T> = when (value) {
        is String -> JsonUtils.objectMapper.readValue(
            value,
            JsonUtils.objectMapper.typeFactory.constructCollectionType(List::class.java, elementType),
        )
        is Collection<*> -> value.filter { elementType.isInstance(it) }.map { elementType.cast(it) }
        else -> error("$value (${value::class}) is not a valid JSON list")
    }

    override fun notNullValueToDB(value: List<T>): Any = JsonUtils.toJsonString(value)

    override fun nonNullValueToString(value: List<T>): String =
        "'${JsonUtils.toJsonString(value).replace("'", "''")}'"
}

fun Table.jsonStringMap(name: String, length: Int = 1024): Column<Map<String, String>> =
    registerColumn(name, JsonStringMapColumnType(length))

fun <T : Any> Table.jsonObject(name: String, javaType: Class<T>, length: Int = 4096): Column<T> =
    registerColumn(name, JsonObjectColumnType(javaType, length))

fun <T : Any> Table.jsonList(name: String, elementType: Class<T>, length: Int = 4096): Column<List<T>> =
    registerColumn(name, JsonListColumnType(elementType, length))
