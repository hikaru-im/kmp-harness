package im.hikaru.ruoyi.module.infra.service.db

import im.hikaru.ruoyi.module.infra.dal.dataobject.db.DataSourceConfigDO
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

@Service
class DatabaseTableServiceImpl(
    private val dataSource: DataSource,
    private val dataSourceConfigService: DataSourceConfigService,
) : DatabaseTableService {

    override fun getTableList(
        dataSourceConfigId: Long,
        nameLike: String?,
        commentLike: String?,
    ): List<DatabaseTableInfo> = withConnection(dataSourceConfigId) { connection ->
        loadTables(connection, null)
            .asSequence()
            .filter { nameLike.isNullOrEmpty() || it.name.contains(nameLike, ignoreCase = true) }
            .filter { commentLike.isNullOrEmpty() || it.comment.contains(commentLike, ignoreCase = true) }
            .sortedBy(DatabaseTableInfo::name)
            .toList()
    }

    override fun getTable(dataSourceConfigId: Long, tableName: String): DatabaseTableInfo? =
        withConnection(dataSourceConfigId) { connection -> loadTables(connection, tableName).firstOrNull() }

    private fun loadTables(connection: Connection, tableName: String?): List<DatabaseTableInfo> {
        val metadata = connection.metaData
        val catalog = connection.catalog
        val schemas = buildList<String?> {
            runCatching { connection.schema }.getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            add(null)
        }.distinct()
        val tables = linkedMapOf<String, DatabaseTableInfo>()
        schemas.forEach { schema ->
            metadata.getTables(catalog, schema, tableName, arrayOf("TABLE")).use { result ->
                while (result.next()) {
                    val name = result.getString("TABLE_NAME") ?: continue
                    if (isExcluded(name)) continue
                    tables.putIfAbsent(
                        name,
                        DatabaseTableInfo(
                            name = name,
                            comment = result.getString("REMARKS").orEmpty(),
                            columns = loadColumns(connection, catalog, schema, name),
                        ),
                    )
                }
            }
        }
        return tables.values.toList()
    }

    private fun loadColumns(
        connection: Connection,
        catalog: String?,
        schema: String?,
        tableName: String,
    ): List<DatabaseColumnInfo> {
        val metadata = connection.metaData
        val primaryKeys = mutableSetOf<String>()
        metadata.getPrimaryKeys(catalog, schema, tableName).use { result ->
            while (result.next()) result.getString("COLUMN_NAME")?.let(primaryKeys::add)
        }
        val columns = mutableListOf<DatabaseColumnInfo>()
        metadata.getColumns(catalog, schema, tableName, null).use { result ->
            while (result.next()) columns += result.toColumnInfo(primaryKeys)
        }
        return columns.sortedBy(DatabaseColumnInfo::ordinalPosition)
    }

    private fun ResultSet.toColumnInfo(primaryKeys: Set<String>): DatabaseColumnInfo {
        val name = getString("COLUMN_NAME")
        val jdbcType = getInt("DATA_TYPE")
        return DatabaseColumnInfo(
            name = name,
            dataType = getString("TYPE_NAME").orEmpty(),
            jdbcType = jdbcType,
            comment = getString("REMARKS").orEmpty(),
            nullable = getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls,
            primaryKey = name in primaryKeys,
            ordinalPosition = getInt("ORDINAL_POSITION"),
            propertyName = name.toCamelCase(),
            kotlinType = kotlinType(jdbcType),
        )
    }

    private fun <T> withConnection(dataSourceConfigId: Long, block: (Connection) -> T): T {
        val connection = if (dataSourceConfigId == DataSourceConfigDO.ID_MASTER) {
            dataSource.connection
        } else {
            val config = requireNotNull(dataSourceConfigService.getDataSourceConfig(dataSourceConfigId)) {
                "Data source $dataSourceConfigId does not exist"
            }
            DriverManager.getConnection(requireNotNull(config.url), config.username, config.password)
        }
        return connection.use(block)
    }

    private fun isExcluded(tableName: String): Boolean = EXCLUDED_TABLE_PATTERNS.any { it.matches(tableName) }

    private fun String.toCamelCase(): String = lowercase().split('_').let { parts ->
        parts.firstOrNull().orEmpty() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun kotlinType(jdbcType: Int): String = when (jdbcType) {
        Types.BIGINT -> "Long"
        Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Int"
        Types.BOOLEAN, Types.BIT -> "Boolean"
        Types.DECIMAL, Types.NUMERIC -> "BigDecimal"
        Types.FLOAT, Types.REAL -> "Float"
        Types.DOUBLE -> "Double"
        Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE,
        Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "LocalDateTime"
        Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "ByteArray"
        else -> "String"
    }

    companion object {
        private val EXCLUDED_TABLE_PATTERNS = listOf(
            Regex("(?i)(ACT|QRTZ|FLW)_.+"),
            Regex("(?i)(IMPDP|ALL|HS)_.+"),
            Regex(".+\\$.*"),
        )
    }
}
