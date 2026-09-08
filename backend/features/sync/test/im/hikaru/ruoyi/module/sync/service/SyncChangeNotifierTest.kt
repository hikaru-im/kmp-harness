package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SYNC_CHANGE_NOTIFICATION_TYPE
import im.hikaru.contracts.sync.SyncChangeNotification
import im.hikaru.contracts.sync.SyncPushPlatform
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.sender.WebSocketMessageSender
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeRetentionTable
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeTable
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncPushDeviceDao
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncPushDeviceTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils

class SyncChangeNotifierTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:sync_notification_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(AppSyncChangeTable, AppSyncChangeRetentionTable, AppSyncPushDeviceTable)
        }
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        TenantContextHolder.clear()
    }

    @Test
    fun `database writer publishes only after the surrounding transaction commits`() {
        val observed = mutableListOf<SyncChangeNotification>()
        val writer = DatabaseSyncChangeWriter(
            listOf(SyncChangeNotifier { _, notification -> observed += notification }),
        )
        TransactionSynchronizationManager.initSynchronization()

        val cursor = transaction {
            writer.append(
                context = SyncCommandContext(tenantId = 1L, userId = 7L),
                resource = "member-address",
                aggregateId = "42",
                operation = SyncChangeOperation.UPSERT,
                aggregateVersion = 2,
                payload = mapOf("id" to 42),
            )
        }

        assertTrue(observed.isEmpty())
        TransactionSynchronizationUtils.triggerAfterCommit()
        assertEquals(listOf(SyncChangeNotification("member-address", cursor)), observed)
    }

    @Test
    fun `websocket notifier sends a tenant scoped pull hint`() {
        val sender = RecordingWebSocketMessageSender()
        val beanFactory = StaticListableBeanFactory().apply {
            addBean("webSocketMessageSender", sender)
        }
        val notifier = WebSocketSyncChangeNotifier(
            beanFactory.getBeanProvider(WebSocketMessageSender::class.java),
        )
        TenantContextHolder.setTenantId(99L)

        notifier.notifyChange(
            SyncCommandContext(tenantId = 42L, userId = 7L),
            SyncChangeNotification(resource = "member-profile", cursor = 12L),
        )

        assertEquals(UserTypeEnum.MEMBER.value, sender.userType)
        assertEquals(7L, sender.userId)
        assertEquals(SYNC_CHANGE_NOTIFICATION_TYPE, sender.messageType)
        assertEquals(42L, sender.tenantIdWhileSending)
        assertEquals("member-profile", JsonUtils.parseTree(sender.messageContent).get("resource").stringValue())
        assertEquals(12L, JsonUtils.parseTree(sender.messageContent).get("cursor").longValue())
        assertEquals(99L, TenantContextHolder.getTenantId())
    }

    @Test
    fun `system push notifier sends scoped hints and removes only invalid scoped tokens`() {
        val deviceService = SyncPushDeviceServiceImpl()
        TenantContextHolder.setTenantId(42L)
        deviceService.register(7L, "active-token", SyncPushPlatform.ANDROID)
        deviceService.register(7L, "invalid-token", SyncPushPlatform.IOS)
        TenantContextHolder.setTenantId(99L)
        deviceService.register(7L, "other-scope-token", SyncPushPlatform.ANDROID)

        val gateway = RecordingSyncPushGateway(
            invalidTokens = setOf("invalid-token", "other-scope-token"),
        )
        val beanFactory = StaticListableBeanFactory().apply {
            addBean("syncPushGateway", gateway)
        }
        val notifier = SystemPushSyncChangeNotifier(
            gatewayProvider = beanFactory.getBeanProvider(SyncPushGateway::class.java),
            taskExecutorProvider = beanFactory.getBeanProvider(TaskExecutor::class.java),
        )
        val notification = SyncChangeNotification(resource = "member-profile", cursor = 12L)

        notifier.notifyChange(
            SyncCommandContext(tenantId = 42L, userId = 7L),
            notification,
        )

        assertEquals(setOf("active-token", "invalid-token"), gateway.tokens.toSet())
        assertEquals(notification, gateway.notification)
        assertEquals(
            listOf("active-token"),
            AppSyncPushDeviceDao.selectTokens(SyncCommandContext(tenantId = 42L, userId = 7L)),
        )
        assertEquals(
            listOf("other-scope-token"),
            AppSyncPushDeviceDao.selectTokens(SyncCommandContext(tenantId = 99L, userId = 7L)),
        )
    }
}

private class RecordingSyncPushGateway(
    private val invalidTokens: Set<String>,
) : SyncPushGateway {
    var tokens: List<String> = emptyList()
    var notification: SyncChangeNotification? = null

    override fun send(
        tokens: List<String>,
        notification: SyncChangeNotification,
    ): SyncPushDeliveryResult {
        this.tokens = tokens
        this.notification = notification
        return SyncPushDeliveryResult(invalidTokens)
    }
}

private class RecordingWebSocketMessageSender : WebSocketMessageSender {
    var userType: Int? = null
    var userId: Long? = null
    var messageType: String? = null
    var messageContent: String = ""
    var tenantIdWhileSending: Long? = null

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) {
        this.userType = userType
        this.userId = userId
        this.messageType = messageType
        this.messageContent = messageContent
        tenantIdWhileSending = TenantContextHolder.getTenantId()
    }

    override fun send(userType: Int?, messageType: String, messageContent: String) = Unit

    override fun send(sessionId: String, messageType: String, messageContent: String) = Unit
}
