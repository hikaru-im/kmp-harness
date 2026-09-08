package im.hikaru.ruoyi.framework.mybatis.core.util

import im.hikaru.ruoyi.framework.mybatis.core.enums.DbTypeEnum
import org.springframework.context.ApplicationContext
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * JDBC 工具类 (Exposed 版)
 *
 * 迁移说明：脱离 MyBatis-Plus DbType / dynamic-datasource，
 * 改为基于 JDBC URL 与产品名判断数据库类型。
 *
 * @author 芋道源码
 */
object JdbcUtils {

    private val URL_DB_TYPE_MAP: List<Pair<String, DbTypeEnum>> = listOf(
        // 注意：顺序敏感，更具体的前置
        "sqlserver" to DbTypeEnum.SQL_SERVER,
        "postgresql" to DbTypeEnum.POSTGRE_SQL,
        "kingbase" to DbTypeEnum.KINGBASE_ES,
        "opengauss" to DbTypeEnum.POSTGRE_SQL,
        "dm" to DbTypeEnum.DM,
        "oceanbase" to DbTypeEnum.OCEAN_BASE,
        "oracle" to DbTypeEnum.ORACLE,
        "h2" to DbTypeEnum.H2,
        "mysql" to DbTypeEnum.MY_SQL,
    )

    /**
     * 判断连接是否正确
     *
     * @param url 数据源连接
     * @param username 账号
     * @param password 密码
     * @return 是否正确
     */
    @JvmStatic
    fun isConnectionOK(url: String, username: String, password: String): Boolean = try {
        DriverManager.getConnection(url, username, password).use { true }
    } catch (e: Exception) {
        false
    }

    /**
     * 获得 URL 对应的 DB 类型
     *
     * @param url JDBC URL
     * @return DB 类型
     */
    @JvmStatic
    fun getDbType(url: String?): DbTypeEnum? {
        if (url.isNullOrBlank()) return null
        return URL_DB_TYPE_MAP.firstOrNull { (k, _) -> url.contains(k, ignoreCase = true) }?.second
    }

    /**
     * 通过当前数据库连接获得对应的 DB 类型
     *
     * @param applicationContext Spring 上下文 (用于获取 DataSource)
     * @return DB 类型
     */
    @JvmStatic
    fun getDbType(applicationContext: ApplicationContext): DbTypeEnum? {
        val dataSource = applicationContext.getBean(DataSource::class.java)
        return dataSource.connection.use { conn: Connection -> getDbTypeFromConnection(conn) }
    }

    private fun getDbTypeFromConnection(conn: Connection): DbTypeEnum? =
        DbTypeEnum.find(conn.metaData.databaseProductName)

    /**
     * 判断 JDBC 连接是否为 SQLServer 数据库
     *
     * @param url JDBC 连接
     * @return 是否为 SQLServer 数据库
     */
    @JvmStatic
    fun isSQLServer(url: String?): Boolean =
        getDbType(url) == DbTypeEnum.SQL_SERVER
}
