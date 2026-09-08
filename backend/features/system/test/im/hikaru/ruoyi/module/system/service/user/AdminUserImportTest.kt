package im.hikaru.ruoyi.module.system.service.user

import im.hikaru.ruoyi.module.infra.api.config.ConfigApi
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserImportExcelVO
import im.hikaru.ruoyi.module.system.dal.mysql.dept.UserPostTable
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserDao
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserTable
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.dept.PostService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.ObjectProvider

class AdminUserImportTest {

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:system_user_import_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(AdminUserTable, UserPostTable) }
    }

    @Test
    fun `imports new users and optionally updates existing users`() {
        val configApi = mock(ConfigApi::class.java)
        `when`(configApi.getConfigValueByKey("system.user.init-password")).thenReturn("123456")
        val service = AdminUserServiceImpl(
            mock(DeptService::class.java),
            mock(PostService::class.java),
            mock(PermissionService::class.java),
            provider<TenantService>(),
            provider<OAuth2TokenService>(),
            configApi,
        )
        val imported = user("imported", "First name")

        val created = service.importUserList(listOf(imported), false)
        assertEquals(listOf("imported"), created.createUsernames)
        assertTrue(requireNotNull(AdminUserDao.selectByUsername("imported")?.password).startsWith("\$2"))

        val duplicate = service.importUserList(listOf(user("imported", "Second name")), false)
        assertTrue(duplicate.failureUsernames.containsKey("imported"))

        val updated = service.importUserList(listOf(user("imported", "Second name")), true)
        assertEquals(listOf("imported"), updated.updateUsernames)
        assertEquals("Second name", AdminUserDao.selectByUsername("imported")?.nickname)
    }

    private fun user(username: String, nickname: String) = UserImportExcelVO().apply {
        this.username = username
        this.nickname = nickname
        email = "$username@example.com"
        mobile = "15601691300"
        sex = 1
        status = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) as ObjectProvider<T>
}
