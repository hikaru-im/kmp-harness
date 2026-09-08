package im.hikaru.ruoyi.module.mp.dal.mysql.account

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.MpAccountPageReqVO
import im.hikaru.ruoyi.module.mp.convert.account.MpAccountConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MpAccountDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:mp_account_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(MpAccountTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `account CRUD keeps tenant filtering and response create time`() {
        val account = MpAccountDO().apply {
            name = "Official account"
            this.account = "ruoyi"
            appId = "wx-test"
            url = "https://example.com/wechat"
            appSecret = "secret"
            token = "token"
            aesKey = "aes"
            remark = "migration"
        }
        val id = MpAccountDao.insert(account)

        val page = MpAccountDao.selectPage(MpAccountPageReqVO().apply {
            name = "Official"
            appId = "wx-"
        })
        assertEquals(1L, page.total)
        assertEquals(id, page.list.single().id)
        assertNotNull(MpAccountConvert.convert(page.list.single()).createTime)
        assertEquals("https://example.com/wechat", page.list.single().url)
        assertEquals(id, MpAccountDao.selectByAppId("wx-test")?.id)

        TenantContextHolder.setTenantId(2L)
        assertNull(MpAccountDao.selectById(id))

        TenantContextHolder.setTenantId(1L)
        assertEquals(1, MpAccountDao.updateById(MpAccountDO().apply {
            this.id = id
            name = "Updated account"
            qrCodeUrl = "https://example.com/qr.png"
        }))
        assertEquals("Updated account", MpAccountDao.selectById(id)?.name)
        assertEquals(1, MpAccountDao.deleteById(id))
        assertNull(MpAccountDao.selectById(id))
    }
}
