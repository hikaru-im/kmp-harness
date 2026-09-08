package im.hikaru.ruoyi.framework.mybatis

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager

class PostgreSqlHikariConnectionTest {

    @Test
    fun `postgresql driver is provided by the data access starter`() {
        val url = "jdbc:postgresql://127.0.0.1:5432/agent-kmp"
        val driver = DriverManager.getDriver(url)

        assertEquals("org.postgresql.Driver", driver.javaClass.name)
        assertTrue(driver.acceptsURL(url))
    }

    @Test
    fun `reference database drivers are provided by the data access starter`() {
        val classLoader = Thread.currentThread().contextClassLoader
        val driverClasses = listOf(
            "com.mysql.cj.jdbc.Driver",
            "oracle.jdbc.OracleDriver",
            "org.postgresql.Driver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "dm.jdbc.driver.DmDriver",
            "com.kingbase8.Driver",
            "org.opengauss.Driver",
            "com.taosdata.jdbc.ws.WebSocketDriver",
        )

        driverClasses.forEach { driverClass ->
            val resourceName = driverClass.replace('.', '/') + ".class"
            assertNotNull(classLoader.getResource(resourceName), "$driverClass should be available")
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRESQL_INTEGRATION_URL", matches = ".+")
    fun `hikari accepts the postgresql jdbc url`() {
        val url = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_URL"))
        val username = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_USERNAME"))
        val password = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_PASSWORD"))

        val config = HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
        }

        HikariDataSource(config).use { dataSource ->
            dataSource.connection.use { connection ->
                assertEquals("PostgreSQL", connection.metaData.databaseProductName)
            }
        }
    }
}
