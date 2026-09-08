package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SyncPushPlatform
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncPushDeviceDao
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncPushDeviceTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncPushDeviceServiceTest {
    private val service = SyncPushDeviceServiceImpl()

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:sync_push_device_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(AppSyncPushDeviceTable) }
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `register is idempotent and moves a token to the current scope`() {
        TenantContextHolder.setTenantId(1L)
        service.register(7L, "device-token", SyncPushPlatform.ANDROID)
        service.register(7L, "device-token", SyncPushPlatform.ANDROID)
        assertEquals(
            listOf("device-token"),
            AppSyncPushDeviceDao.selectTokens(SyncCommandContext(1L, 7L)),
        )

        TenantContextHolder.setTenantId(2L)
        service.register(8L, "device-token", SyncPushPlatform.IOS)

        assertEquals(emptyList<String>(), AppSyncPushDeviceDao.selectTokens(SyncCommandContext(1L, 7L)))
        assertEquals(
            listOf("device-token"),
            AppSyncPushDeviceDao.selectTokens(SyncCommandContext(2L, 8L)),
        )
    }

    @Test
    fun `unregister cannot remove a token owned by another scope`() {
        TenantContextHolder.setTenantId(1L)
        service.register(7L, "device-token", SyncPushPlatform.ANDROID)

        service.unregister(8L, "device-token")
        assertEquals(
            listOf("device-token"),
            AppSyncPushDeviceDao.selectTokens(SyncCommandContext(1L, 7L)),
        )

        service.unregister(7L, "device-token")
        assertEquals(emptyList<String>(), AppSyncPushDeviceDao.selectTokens(SyncCommandContext(1L, 7L)))
    }
}
