package im.hikaru.ruoyi.module.sync.service

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandOutcomeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesQuery
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeDao
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeRetentionTable
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeTable
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncCommandDao
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncCommandTable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncCommandServiceTest {
    private val invocations = AtomicInteger()
    private lateinit var service: SyncCommandServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:sync_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(AppSyncCommandTable, AppSyncChangeTable, AppSyncChangeRetentionTable)
        }
        TenantContextHolder.setTenantId(1L)
        service = serviceWith { context, command ->
            invocations.incrementAndGet()
            DatabaseSyncChangeWriter(emptyList()).append(
                context = context,
                resource = command.aggregateType,
                aggregateId = requireNotNull(command.aggregateId),
                operation = SyncChangeOperation.UPSERT,
                aggregateVersion = 2,
                payload = mapOf("id" to command.aggregateId, "version" to 2),
            )
            SyncCommandDecision.Applied(serverVersion = 2)
        }
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `same command is executed once and its first result is replayed`() {
        val first = service.submit(7L, batch(command("command-1", "{\"id\":1024,\"name\":\"Alice\"}")))
            .results.single()
        val replay = service.submit(7L, batch(command("command-1", "{\"name\":\"Alice\",\"id\":1024}")))
            .results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(1, invocations.get())
        assertEquals(1, changes(7L).items.size)
    }

    @Test
    fun `concurrent duplicate waits for and replays the first transaction`() {
        val handlerEntered = CountDownLatch(1)
        val releaseHandler = CountDownLatch(1)
        service = serviceWith { _, _ ->
            invocations.incrementAndGet()
            handlerEntered.countDown()
            check(releaseHandler.await(5, TimeUnit.SECONDS))
            SyncCommandDecision.Applied(serverVersion = 2)
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandResultVO> {
                TenantContextHolder.setTenantId(1L)
                try {
                    transaction {
                        service.submit(7L, batch(command("concurrent-1", "{\"id\":1024}")))
                    }.results.single()
                } finally {
                    TenantContextHolder.clear()
                }
            }
            assertTrue(handlerEntered.await(5, TimeUnit.SECONDS))
            val duplicate = executor.submit<im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandResultVO> {
                TenantContextHolder.setTenantId(1L)
                try {
                    transaction {
                        service.submit(7L, batch(command("concurrent-1", "{\"id\":1024}")))
                    }.results.single()
                } finally {
                    TenantContextHolder.clear()
                }
            }
            releaseHandler.countDown()

            val results = listOf(first.get(5, TimeUnit.SECONDS), duplicate.get(5, TimeUnit.SECONDS))
            assertEquals(setOf(false, true), results.map { it.replayed }.toSet())
            assertEquals(setOf(AppSyncCommandOutcomeVO.APPLIED), results.map { it.outcome }.toSet())
            assertEquals(1, invocations.get())
        } finally {
            releaseHandler.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `same command id with a different request is rejected without replacing the receipt`() {
        service.submit(7L, batch(command("command-1", "{\"id\":1024,\"name\":\"Alice\"}")))

        val reused = service.submit(7L, batch(command("command-1", "{\"id\":1024,\"name\":\"Bob\"}")))
            .results.single()
        val originalReplay = service.submit(7L, batch(command("command-1", "{\"id\":1024,\"name\":\"Alice\"}")))
            .results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, reused.outcome)
        assertEquals("COMMAND_ID_REUSED", reused.errorCode)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, originalReplay.outcome)
        assertEquals(1, invocations.get())
    }

    @Test
    fun `conflict remains a conflict when replayed`() {
        service = serviceWith {
                _, _ -> SyncCommandDecision.Conflict(
                    serverVersion = 9,
                    serverPayload = JsonUtils.parseTree("{\"id\":1024,\"name\":\"Server\",\"version\":9}"),
                )
        }

        val first = service.submit(7L, batch(command("conflict-1", "{\"id\":1024}"))).results.single()
        val replay = service.submit(7L, batch(command("conflict-1", "{\"id\":1024}"))).results.single()

        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, first.outcome)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(9, replay.serverVersion)
        assertEquals("Server", JsonUtils.getText(first.serverPayload, "name"))
        assertEquals(first.serverPayload.toString(), replay.serverPayload.toString())
    }

    @Test
    fun `command ids and changes are isolated by tenant and user`() {
        service.submit(7L, batch(command("shared-id", "{\"id\":1024}")))
        service.submit(8L, batch(command("shared-id", "{\"id\":1024}")))
        TenantContextHolder.setTenantId(2L)
        service.submit(7L, batch(command("shared-id", "{\"id\":1024}")))

        assertEquals(3, invocations.get())
        assertEquals(1, changes(7L).items.size)
        TenantContextHolder.setTenantId(1L)
        assertEquals(1, changes(7L).items.size)
        assertEquals(1, changes(8L).items.size)
    }

    @Test
    fun `change feed uses a stable exclusive cursor and reports more pages`() {
        repeat(3) { index ->
            service.submit(7L, batch(command("command-$index", "{\"id\":$index}")))
        }

        val first = changes(7L, cursor = 0, limit = 2)
        val second = changes(7L, cursor = first.nextCursor, limit = 2)

        assertEquals(2, first.items.size)
        assertTrue(first.hasMore)
        assertEquals(1, second.items.size)
        assertFalse(second.hasMore)
        assertTrue(second.items.single().cursor > first.nextCursor)
    }

    @Test
    fun `expired cursor requests a snapshot reset and continues from its high watermark`() {
        repeat(3) { index ->
            service.submit(7L, batch(command("retained-$index", "{\"id\":$index}")))
        }
        val highWatermark = changes(7L).nextCursor

        val deleted = SyncChangeRetentionService().pruneBefore(
            cutoff = LocalDateTime.now().plusDays(1).toKotlinLocalDateTime(),
            batchSize = 2,
            maxBatches = 2,
        )
        val expired = changes(7L, cursor = 0)

        assertEquals(3, deleted)
        assertTrue(expired.resetRequired)
        assertEquals(highWatermark, expired.resetCursor)
        assertEquals(highWatermark, expired.nextCursor)
        assertTrue(expired.items.isEmpty())
        assertFalse(expired.hasMore)

        service.submit(7L, batch(command("after-reset", "{\"id\":4}")))
        val incremental = changes(7L, cursor = requireNotNull(expired.resetCursor))
        assertFalse(incremental.resetRequired)
        assertEquals(1, incremental.items.size)
        assertTrue(incremental.items.single().cursor > highWatermark)
    }

    @Test
    fun `cursor ahead of the scoped high watermark also requests a reset`() {
        service.submit(7L, batch(command("ahead-1", "{\"id\":1}")))
        val highWatermark = changes(7L).nextCursor

        val response = changes(7L, cursor = highWatermark + 100)

        assertTrue(response.resetRequired)
        assertEquals(highWatermark, response.resetCursor)
        assertEquals(highWatermark, response.nextCursor)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `technical failure rolls back both command registration and change`() {
        service = serviceWith { context, command ->
            DatabaseSyncChangeWriter(emptyList()).append(
                context,
                command.aggregateType,
                requireNotNull(command.aggregateId),
                SyncChangeOperation.UPSERT,
                2,
                mapOf("id" to command.aggregateId),
            )
            error("database dependency failed")
        }
        val context = SyncCommandContext(1L, 7L)

        assertThrows(IllegalStateException::class.java) {
            transaction {
                service.submit(7L, batch(command("failed-1", "{\"id\":1024}")))
            }
        }

        assertNull(AppSyncCommandDao.select(context, "failed-1"))
        assertEquals(0, AppSyncChangeDao.selectAfter(context, "member-address", 0, 10).size)
    }

    private fun serviceWith(
        block: (SyncCommandContext, SyncCommandEnvelope) -> SyncCommandDecision,
    ): SyncCommandServiceImpl {
        val handler = object : SyncCommandHandler {
            override val keys = setOf(SyncCommandHandlerKey("member-address", "update"))
            override fun handle(context: SyncCommandContext, command: SyncCommandEnvelope) = block(context, command)
        }
        return SyncCommandServiceImpl(
            SyncCommandProcessor(SyncCommandDispatcher(listOf(handler), SyncCommandWhitelist()))
        )
    }

    private fun command(commandId: String, payload: String): AppSyncCommandReqVO = AppSyncCommandReqVO().apply {
        this.commandId = commandId
        aggregateType = "member-address"
        aggregateId = "1024"
        operation = "update"
        baseVersion = 1
        this.payload = JsonUtils.parseTree(payload)
    }

    private fun batch(command: AppSyncCommandReqVO): AppSyncCommandBatchReqVO = AppSyncCommandBatchReqVO().apply {
        commands = listOf(command)
    }

    private fun changes(userId: Long, cursor: Long = 0, limit: Int = 100) = service.getChanges(
        userId,
        AppSyncChangesQuery("member-address", cursor, limit),
    )
}
