package im.hikaru.ruoyi.framework.mybatis.core.handler

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder

class DefaultDBFieldHandlerTest {

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `insert fills missing audit fields from the security context`() {
        loginAs(42L)
        val entity = TestEntity()

        DefaultDBFieldHandler.fillOnInsert(entity)

        assertNotNull(entity.createTime)
        assertEquals(entity.createTime, entity.updateTime)
        assertEquals("42", entity.creator)
        assertEquals("42", entity.updater)
    }

    @Test
    fun `explicit insert and update audit fields are preserved`() {
        loginAs(42L)
        val createTime = LocalDateTime(2020, 1, 2, 3, 4)
        val updateTime = LocalDateTime(2021, 2, 3, 4, 5)
        val entity = TestEntity().apply {
            this.createTime = createTime
            this.updateTime = updateTime
            creator = "creator"
            updater = "updater"
        }

        DefaultDBFieldHandler.fillOnInsert(entity)
        DefaultDBFieldHandler.fillOnUpdate(entity)

        assertEquals(createTime, entity.createTime)
        assertEquals(updateTime, entity.updateTime)
        assertEquals("creator", entity.creator)
        assertEquals("updater", entity.updater)
    }

    @Test
    fun `anonymous update fills time without inventing an updater`() {
        val entity = TestEntity()

        DefaultDBFieldHandler.fillOnUpdate(entity)

        assertNotNull(entity.updateTime)
        assertNull(entity.updater)
    }

    private fun loginAs(userId: Long) {
        SecurityFrameworkUtils.setLoginUser(
            LoginUser().apply {
                id = userId
                userType = 1
            },
            MockHttpServletRequest(),
        )
    }

    private class TestEntity : BaseEntity {
        override var createTime: LocalDateTime? = null
        override var updateTime: LocalDateTime? = null
        override var creator: String? = null
        override var updater: String? = null
        override var deleted: Boolean = false
    }
}
