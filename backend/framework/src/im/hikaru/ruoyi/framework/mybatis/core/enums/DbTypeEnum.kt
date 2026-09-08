package im.hikaru.ruoyi.framework.mybatis.core.enums

/**
 * 数据库类型枚举 (Exposed 版，脱离 MyBatis-Plus DbType)
 *
 * 迁移说明：原实现封装 MyBatis-Plus 的 DbType + 各数据库 FIND_IN_SET 模板。
 * 本枚举独立定义数据库类型，跨数据库的 FIND_IN_SET SQL 模板保留供业务使用。
 *
 * @author 芋道源码
 */
enum class DbTypeEnum(
    /** 数据库产品名 (JDBC metadata product name) */
    val productName: String,
    /** SQL FIND_IN_SET 模板，占位符 {column} / {value} */
    val findInSetTemplate: String,
) {
    H2("H2", "POSITION(',' || CAST({value} AS VARCHAR) || ',' IN ',' || {column} || ',') > 0"),

    MY_SQL(
        "MySQL",
        "FIND_IN_SET({value}, {column}) <> 0",
    ),

    ORACLE("Oracle", "INSTR(',' || {column} || ',', ',' || {value} || ',') > 0"),

    /**
     * PostgreSQL
     *
     * 华为 openGauss 使用 ProductName 与 PostgreSQL 相同
     */
    POSTGRE_SQL(
        "PostgreSQL",
        "POSITION(',' || CAST({value} AS VARCHAR) || ',' IN ',' || {column} || ',') > 0",
    ),

    SQL_SERVER(
        "Microsoft SQL Server",
        "CHARINDEX(',' + CAST({value} AS varchar(255)) + ',', ',' + {column} + ',') > 0",
    ),

    /** 达梦 */
    DM("DM DBMS", "FIND_IN_SET({value}, {column}) <> 0"),

    /** 人大金仓 */
    KINGBASE_ES(
        "KingbaseES",
        "POSITION(',' || CAST({value} AS VARCHAR) || ',' IN ',' || {column} || ',') > 0",
    ),

    /** OceanBase */
    OCEAN_BASE("OceanBase", "FIND_IN_SET({value}, {column}) <> 0"),
    ;

    companion object {
        val MAP_BY_NAME: Map<String, DbTypeEnum> = entries.associateBy { it.productName }

        /**
         * 根据数据库产品名查找枚举
         */
        fun find(databaseProductName: String?): DbTypeEnum? {
            if (databaseProductName.isNullOrBlank()) return null
            return MAP_BY_NAME[databaseProductName]
        }

        /**
         * 获取指定数据库类型的 FIND_IN_SET 模板
         */
        fun getFindInSetTemplate(dbType: DbTypeEnum?): String =
            dbType?.findInSetTemplate?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("FIND_IN_SET not supported")
    }
}
