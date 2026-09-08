package im.hikaru.ruoyi.module.infra.service.db

data class DatabaseTableInfo(
    val name: String,
    val comment: String,
    val columns: List<DatabaseColumnInfo>,
)

data class DatabaseColumnInfo(
    val name: String,
    val dataType: String,
    val jdbcType: Int,
    val comment: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val ordinalPosition: Int,
    val propertyName: String,
    val kotlinType: String,
)
