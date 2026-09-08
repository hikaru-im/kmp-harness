package im.hikaru.ruoyi.module.system.dal.mysql

import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi
import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import im.hikaru.ruoyi.framework.datapermission.core.aop.DataPermissionContextHolder
import im.hikaru.ruoyi.framework.datapermission.core.db.DataPermissionRuleHandler
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRule
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRuleFactoryImpl
import im.hikaru.ruoyi.framework.datapermission.core.rule.dept.DeptDataPermissionRule
import im.hikaru.ruoyi.framework.datapermission.core.util.DataPermissionUtils
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.tenant.config.TenantProperties
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.db.TenantDatabaseInterceptor
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import java.util.concurrent.Callable

class ExposedDataAccessPolicyTest {

    private val registrations = mutableListOf<AutoCloseable>()

    @AfterEach
    fun tearDown() {
        registrations.asReversed().forEach(AutoCloseable::close)
        registrations.clear()
        TenantContextHolder.clear()
        SecurityContextHolder.clearContext()
        DataPermissionContextHolder.clear()
    }

    @Test
    fun `tenant policy isolates selects updates and deletes`() {
        connect("tenant")
        transaction {
            SchemaUtils.create(TenantPolicyTable)
            insertTenantRow(1L, "one")
            insertTenantRow(2L, "two")
        }
        registrations += TenantDatabaseInterceptor(TenantProperties())

        TenantContextHolder.setTenantId(1L)
        assertEquals(listOf("one"), transaction { TenantPolicyTable.selectAll().map { it[TenantPolicyTable.value] } })
        assertEquals(listOf("one"), transaction {
            val tenantAlias = TenantPolicyTable.alias("tenant_alias")
            tenantAlias.selectAll().map { it[tenantAlias[TenantPolicyTable.value]] }
        })

        val updated = transaction {
            TenantPolicyTable.update(where = { TenantPolicyTable.value eq "two" }) {
                it[TenantPolicyTable.value] = "changed"
            }
        }
        assertEquals(0, updated)

        val deleted = transaction {
            TenantPolicyTable.deleteWhere { TenantPolicyTable.value eq "two" }
        }
        assertEquals(0, deleted)

        TenantContextHolder.setIgnore(true)
        assertEquals(2L, transaction { TenantPolicyTable.selectAll().count() })
        assertEquals("two", transaction {
            TenantPolicyTable.selectAll().where { TenantPolicyTable.tenantId eq 2L }
                .single()[TenantPolicyTable.value]
        })
    }

    @Test
    fun `tenant policy requires context unless table is ignored`() {
        connect("tenant_required")
        transaction { SchemaUtils.create(TenantPolicyTable) }
        registrations += TenantDatabaseInterceptor(TenantProperties())

        assertThrows(NullPointerException::class.java) {
            transaction { TenantPolicyTable.selectAll().count() }
        }

        registrations.removeLast().close()
        registrations += TenantDatabaseInterceptor(TenantProperties().apply {
            ignoreTables = setOf(TenantPolicyTable.tableName)
        })
        assertEquals(0L, transaction { TenantPolicyTable.selectAll().count() })
    }

    @Test
    fun `department permission policy composes with existing predicates and protects writes`() {
        connect("data_permission")
        transaction {
            SchemaUtils.create(DataPermissionTable)
            insertPermissionRow(10L, 99L, "department")
            insertPermissionRow(20L, 7L, "self")
            insertPermissionRow(20L, 8L, "denied")
        }

        val rule = DeptDataPermissionRule(permissionApi(deptIds = setOf(10L), self = true)).apply {
            addDeptColumn(DataPermissionTable.tableName, "dept_id")
            addUserColumn(DataPermissionTable.tableName, "user_id")
        }
        registrations += DataPermissionRuleHandler(DataPermissionRuleFactoryImpl(listOf(rule)))
        loginAs(7L)

        assertEquals(
            listOf("department", "self"),
            transaction { DataPermissionTable.selectAll().map { it[DataPermissionTable.value] }.sorted() },
        )
        assertEquals(0L, transaction {
            DataPermissionTable.selectAll().where { DataPermissionTable.value eq "denied" }.count()
        })

        assertEquals(0, transaction {
            DataPermissionTable.update(where = { DataPermissionTable.value eq "denied" }) {
                it[DataPermissionTable.value] = "changed"
            }
        })
        assertEquals(0, transaction {
            DataPermissionTable.deleteWhere { DataPermissionTable.value eq "denied" }
        })

        val unrestrictedCount = DataPermissionUtils.executeIgnore(Callable {
            transaction { DataPermissionTable.selectAll().count() }
        })
        assertEquals(3L, unrestrictedCount)
    }

    @Test
    fun `data permission annotations include exclude and disable rules`() {
        connect("data_permission_annotations")
        transaction {
            SchemaUtils.create(DataPermissionTable)
            insertPermissionRow(10L, 99L, "department")
            insertPermissionRow(20L, 7L, "self")
            insertPermissionRow(20L, 8L, "denied")
        }
        val deptRule = DeptDataPermissionRule(permissionApi(deptIds = setOf(10L), self = true)).apply {
            addDeptColumn(DataPermissionTable.tableName, "dept_id")
            addUserColumn(DataPermissionTable.tableName, "user_id")
        }
        registrations += DataPermissionRuleHandler(DataPermissionRuleFactoryImpl(listOf(deptRule, DenyRule())))
        loginAs(7L)

        assertEquals(0L, transaction { DataPermissionTable.selectAll().count() })
        assertEquals(2L, withDataPermission("includeDeptRule") {
            transaction { DataPermissionTable.selectAll().count() }
        })
        assertEquals(2L, withDataPermission("excludeDenyRule") {
            transaction { DataPermissionTable.selectAll().count() }
        })
        assertEquals(3L, withDataPermission("disableRules") {
            transaction { DataPermissionTable.selectAll().count() }
        })
    }

    private fun connect(suffix: String) {
        Database.connect(
            "jdbc:h2:mem:policy_${suffix}_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
    }

    private fun loginAs(userId: Long) {
        SecurityFrameworkUtils.setLoginUser(
            LoginUser().apply {
                id = userId
                userType = UserTypeEnum.ADMIN.value
            },
            MockHttpServletRequest(),
        )
    }

    private fun permissionApi(deptIds: Set<Long>, self: Boolean): PermissionCommonApi =
        object : PermissionCommonApi {
            override fun hasAnyPermissions(userId: Long, vararg permissions: String): Boolean = false
            override fun hasAnyRoles(userId: Long, vararg roles: String): Boolean = false
            override fun getDeptDataPermission(userId: Long): DeptDataPermissionRespDTO =
                DeptDataPermissionRespDTO().apply {
                    all = false
                    this.self = self
                    this.deptIds = deptIds
                }
        }

    private fun insertTenantRow(tenantId: Long, value: String) {
        TenantPolicyTable.insert {
            it[TenantPolicyTable.tenantId] = tenantId
            it[TenantPolicyTable.value] = value
        }
    }

    private fun insertPermissionRow(deptId: Long, userId: Long, value: String) {
        DataPermissionTable.insert {
            it[DataPermissionTable.deptId] = deptId
            it[DataPermissionTable.userId] = userId
            it[DataPermissionTable.value] = value
        }
    }

    private fun <T> withDataPermission(marker: String, block: () -> T): T {
        val annotation = javaClass.getDeclaredMethod(marker).getAnnotation(DataPermission::class.java)
        DataPermissionContextHolder.add(annotation)
        return try {
            block()
        } finally {
            DataPermissionContextHolder.remove()
        }
    }

    @DataPermission(includeRules = [DeptDataPermissionRule::class])
    private fun includeDeptRule() = Unit

    @DataPermission(excludeRules = [DenyRule::class])
    private fun excludeDenyRule() = Unit

    @DataPermission(enable = false)
    private fun disableRules() = Unit

    private object TenantPolicyTable : Table("policy_tenant") {
        val id = long("id").autoIncrement()
        val tenantId = long("tenant_id")
        val value = varchar("value", 32)
        override val primaryKey = PrimaryKey(id)
    }

    private object DataPermissionTable : Table("policy_data_permission") {
        val id = long("id").autoIncrement()
        val deptId = long("dept_id")
        val userId = long("user_id")
        val value = varchar("value", 32)
        override val primaryKey = PrimaryKey(id)
    }

    class DenyRule : DataPermissionRule {
        override fun getTableNames(): Set<String> = setOf(DataPermissionTable.tableName)
        override fun getExpression(table: Table): Op<Boolean> = Op.FALSE
    }
}
