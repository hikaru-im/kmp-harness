package im.hikaru.ruoyi.module.infra.framework.file

import im.hikaru.ruoyi.module.infra.dal.dataobject.db.DataSourceConfigDO
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenSceneEnum
import im.hikaru.ruoyi.module.infra.framework.codegen.config.CodegenProperties
import im.hikaru.ruoyi.module.infra.service.codegen.inner.CodegenBuilder
import im.hikaru.ruoyi.module.infra.service.codegen.inner.CodegenEngine
import im.hikaru.ruoyi.module.infra.service.db.DataSourceConfigService
import im.hikaru.ruoyi.module.infra.service.db.DatabaseTableServiceImpl
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class CodegenTest {

    @Test
    fun `database metadata is converted into Kotlin Exposed generation models`() {
        val dataSource = JdbcDataSource().apply { setURL("jdbc:h2:mem:codegen;DB_CLOSE_DELAY=-1") }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE infra_demo_user (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "user_name VARCHAR(64) NOT NULL, " +
                        "tenant_id BIGINT NOT NULL, " +
                        "create_time TIMESTAMP)",
                )
                statement.execute("COMMENT ON TABLE infra_demo_user IS 'User table'")
                statement.execute("COMMENT ON COLUMN infra_demo_user.id IS 'Primary key'")
                statement.execute("COMMENT ON COLUMN infra_demo_user.user_name IS 'User name'")
                statement.execute("COMMENT ON COLUMN infra_demo_user.create_time IS 'Created time'")
            }
        }
        val metadataService = DatabaseTableServiceImpl(dataSource, mock(DataSourceConfigService::class.java))

        val tableInfo = requireNotNull(metadataService.getTable(DataSourceConfigDO.ID_MASTER, "INFRA_DEMO_USER"))
        val builder = CodegenBuilder()
        val table = builder.buildTable(tableInfo).apply {
            id = 1
            scene = CodegenSceneEnum.ADMIN.scene
            dataSourceConfigId = 0
            frontType = CodegenProperties().frontType
            author = "tester"
        }
        val columns = builder.buildColumns(1, tableInfo.columns)
        val generated = CodegenEngine().execute(table, columns)

        assertEquals("infra", table.moduleName)
        assertEquals("DemoUser", table.className)
        assertTrue(columns.first { it.columnName.equals("ID", true) }.primaryKey == true)
        assertEquals("LIKE", columns.first { it.columnName.equals("USER_NAME", true) }.listOperationCondition)
        assertTrue(generated.keys.any { it.endsWith("DemoUserTable.kt") })
        assertTrue(generated.keys.any { it.startsWith("src/im/hikaru/ruoyi/module/infra/") })
        assertFalse(generated.keys.any { it.contains("src/im.hikaru.ruoyi/") })
        assertTrue(generated.values.any { it.contains("BaseTable(\"INFRA_DEMO_USER\")") })
        assertTrue(generated.values.any { it.contains("autoIncrement(\"INFRA_DEMO_USER_seq\")") })
        assertTrue(generated.values.any { it.contains("tenantId = long(\"TENANT_ID\")") })
        assertFalse(generated.values.any { it.contains("TODO(") })
    }
}
