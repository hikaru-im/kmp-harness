package im.hikaru.ruoyi.module.member.service.address

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.mysql.address.MemberAddressDao
import im.hikaru.ruoyi.module.member.dal.mysql.address.MemberAddressTable
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandOutcomeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesQuery
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeRetentionTable
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncChangeTable
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncCommandTable
import im.hikaru.ruoyi.module.sync.service.DatabaseSyncChangeWriter
import im.hikaru.ruoyi.module.sync.service.SyncCommandDispatcher
import im.hikaru.ruoyi.module.sync.service.SyncCommandProcessor
import im.hikaru.ruoyi.module.sync.service.SyncCommandServiceImpl
import jakarta.validation.Validation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemberAddressSyncIntegrationTest {
    private lateinit var commandService: AddressCommandServiceImpl
    private lateinit var syncService: SyncCommandServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:member_address_sync_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                MemberAddressTable,
                AppSyncCommandTable,
                AppSyncChangeTable,
                AppSyncChangeRetentionTable,
            )
        }
        TenantContextHolder.setTenantId(1L)

        commandService = AddressCommandServiceImpl(DatabaseSyncChangeWriter(emptyList()))
        val validator = Validation.buildDefaultValidatorFactory().validator
        val handler = MemberAddressSyncCommandHandler(commandService, validator)
        syncService = SyncCommandServiceImpl(
            SyncCommandProcessor(SyncCommandDispatcher(listOf(handler))),
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `address update applies once then returns a replayed receipt`() {
        val address = commandService.create(7L, createRequest("Alice"))
        val id = requireNotNull(address.id)

        val first = transaction {
            syncService.submit(7L, batch(updateCommand("update-1", id, 1, "Bob")))
        }.results.single()
        val replay = transaction {
            syncService.submit(7L, batch(updateCommand("update-1", id, 1, "Bob")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertEquals(2, first.serverVersion)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(2, replay.serverVersion)
        assertEquals("Bob", MemberAddressDao.selectByIdAndUserId(id, 7L)?.name)
        assertEquals(2, MemberAddressDao.selectByIdAndUserId(id, 7L)?.version)
    }

    @Test
    fun `address create maps a temporary id exactly once and replays the server id`() {
        val first = transaction {
            syncService.submit(7L, batch(createCommand("create-1", -1, "Alice")))
        }.results.single()
        val replay = transaction {
            syncService.submit(7L, batch(createCommand("create-1", -1, "Alice")))
        }.results.single()

        val serverId = requireNotNull(first.aggregateId).toLong()
        assertTrue(serverId > 0)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertEquals(1, first.serverVersion)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(serverId.toString(), replay.aggregateId)
        assertEquals(1, replay.serverVersion)
        assertEquals(1, MemberAddressDao.selectListByUserIdAndDefaultStatus(7L, null).size)
        assertEquals("Alice", MemberAddressDao.selectByIdAndUserId(serverId, 7L)?.name)
    }

    @Test
    fun `address create rejects a server style aggregate id`() {
        val rejected = transaction {
            syncService.submit(7L, batch(createCommand("create-invalid", 42, "Alice")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, rejected.outcome)
        assertEquals("INVALID_AGGREGATE_ID", rejected.errorCode)
        assertTrue(MemberAddressDao.selectListByUserIdAndDefaultStatus(7L, null).isEmpty())
    }

    @Test
    fun `stale base version returns a deterministic conflict without changing the address`() {
        val address = commandService.create(7L, createRequest("Alice"))
        val id = requireNotNull(address.id)
        transaction {
            syncService.submit(7L, batch(updateCommand("update-1", id, 1, "Bob")))
        }

        val conflict = transaction {
            syncService.submit(7L, batch(updateCommand("update-2", id, 1, "Carol")))
        }.results.single()
        val replay = transaction {
            syncService.submit(7L, batch(updateCommand("update-2", id, 1, "Carol")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, conflict.outcome)
        assertEquals(2, conflict.serverVersion)
        assertEquals("Bob", JsonUtils.getText(conflict.serverPayload, "name"))
        assertEquals(2, conflict.serverPayload?.get("version")?.asLong())
        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(conflict.serverPayload.toString(), replay.serverPayload.toString())
        assertEquals("Bob", MemberAddressDao.selectByIdAndUserId(id, 7L)?.name)
    }

    @Test
    fun `address delete applies once and replays after the row is gone`() {
        val address = commandService.create(7L, createRequest("Alice"))
        val id = requireNotNull(address.id)

        val first = transaction {
            syncService.submit(7L, batch(deleteCommand("delete-1", id, 1)))
        }.results.single()
        val replay = transaction {
            syncService.submit(7L, batch(deleteCommand("delete-1", id, 1)))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertEquals(2, first.serverVersion)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, replay.outcome)
        assertEquals(2, replay.serverVersion)
        assertTrue(replay.replayed)
        assertEquals(null, MemberAddressDao.selectByIdAndUserId(id, 7L))
        val changes = syncService.getChanges(7L, AppSyncChangesQuery("member-address", 0, 100))
        assertEquals(listOf("UPSERT", "DELETE"), changes.items.map { it.operation })
        assertEquals(listOf(1L, 2L), changes.items.map { it.aggregateVersion })
    }

    @Test
    fun `stale delete returns a conflict with the current server snapshot`() {
        val address = commandService.create(7L, createRequest("Alice"))
        val id = requireNotNull(address.id)
        commandService.update(7L, updateRequest(id, "Server"))

        val conflict = transaction {
            syncService.submit(7L, batch(deleteCommand("delete-stale", id, 1)))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, conflict.outcome)
        assertEquals(2, conflict.serverVersion)
        assertEquals("Server", JsonUtils.getText(conflict.serverPayload, "name"))
        assertEquals("Server", MemberAddressDao.selectByIdAndUserId(id, 7L)?.name)
    }

    @Test
    fun `deleting an already absent address converges as applied`() {
        val result = transaction {
            syncService.submit(7L, batch(deleteCommand("delete-absent", 999, 4)))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, result.outcome)
        assertEquals(5, result.serverVersion)
    }

    @Test
    fun `another user cannot mutate an address through its aggregate id`() {
        val address = commandService.create(7L, createRequest("Alice"))
        val id = requireNotNull(address.id)

        val rejected = transaction {
            syncService.submit(8L, batch(updateCommand("update-other-user", id, 1, "Mallory")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, rejected.outcome)
        assertEquals("AGGREGATE_NOT_FOUND", rejected.errorCode)
        assertEquals("Alice", MemberAddressDao.selectByIdAndUserId(id, 7L)?.name)
    }

    @Test
    fun `online writes also increment versions and appear in the change feed`() {
        val address = commandService.create(7L, createRequest("Alice"))
        val id = requireNotNull(address.id)
        val onlineUpdate = updateRequest(id, "Online")

        val result = commandService.update(7L, onlineUpdate)
        val changes = syncService.getChanges(7L, AppSyncChangesQuery("member-address", 0, 100))

        assertTrue(result is AddressMutationResult.Applied)
        assertEquals(2, MemberAddressDao.selectByIdAndUserId(id, 7L)?.version)
        assertEquals(listOf(1L, 2L), changes.items.map { it.aggregateVersion })
        assertEquals(listOf("UPSERT", "UPSERT"), changes.items.map { it.operation })
    }

    private fun createRequest(name: String): AppAddressCreateReqVO = AppAddressCreateReqVO().apply {
        this.name = name
        mobile = "13800138000"
        areaId = 110101
        detailAddress = "No. 1 Test Road"
        defaultStatus = false
    }

    private fun updateRequest(id: Long, name: String): AppAddressUpdateReqVO = AppAddressUpdateReqVO().apply {
        this.id = id
        this.name = name
        mobile = "13800138000"
        areaId = 110101
        detailAddress = "No. 2 Test Road"
        defaultStatus = false
    }

    private fun updateCommand(
        commandId: String,
        id: Long,
        baseVersion: Long,
        name: String,
    ): AppSyncCommandReqVO = AppSyncCommandReqVO().apply {
        this.commandId = commandId
        aggregateType = "member-address"
        aggregateId = id.toString()
        operation = "update"
        this.baseVersion = baseVersion
        payload = JsonUtils.objectMapper.valueToTree(updateRequest(id, name))
    }

    private fun createCommand(
        commandId: String,
        temporaryId: Long,
        name: String,
    ): AppSyncCommandReqVO = AppSyncCommandReqVO().apply {
        this.commandId = commandId
        aggregateType = "member-address"
        aggregateId = temporaryId.toString()
        operation = "create"
        baseVersion = null
        payload = JsonUtils.objectMapper.valueToTree(createRequest(name))
    }

    private fun deleteCommand(
        commandId: String,
        id: Long,
        baseVersion: Long,
    ): AppSyncCommandReqVO = AppSyncCommandReqVO().apply {
        this.commandId = commandId
        aggregateType = "member-address"
        aggregateId = id.toString()
        operation = "delete"
        this.baseVersion = baseVersion
        payload = JsonUtils.objectMapper.createObjectNode()
    }

    private fun batch(command: AppSyncCommandReqVO): AppSyncCommandBatchReqVO = AppSyncCommandBatchReqVO().apply {
        commands = listOf(command)
    }
}
