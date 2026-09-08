package im.hikaru.ruoyi.framework.mybatis.core.type

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/** Maps Kotlin Boolean values to the SMALLINT 0/1 columns used by the reference schema. */
class SmallIntBooleanColumnType : ColumnType<Boolean>() {

    override fun sqlType(): String = "SMALLINT"

    override fun valueFromDB(value: Any): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value == "1" || value.equals("true", ignoreCase = true)
        else -> error("$value (${value::class}) is not a valid SMALLINT boolean")
    }

    override fun notNullValueToDB(value: Boolean): Any = if (value) 1.toShort() else 0.toShort()

    override fun nonNullValueToString(value: Boolean): String = if (value) "1" else "0"
}

fun Table.smallIntBoolean(name: String): Column<Boolean> =
    registerColumn(name, SmallIntBooleanColumnType())
