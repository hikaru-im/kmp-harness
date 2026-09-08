package im.hikaru.ruoyi.module.member.service.user

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserDao
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserTable
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

class MemberProfileSyncIntegrationTest {
    private lateinit var commandService: ProfileCommandServiceImpl
    private lateinit var syncService: SyncCommandServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:member_profile_sync_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                MemberUserTable,
                AppSyncCommandTable,
                AppSyncChangeTable,
                AppSyncChangeRetentionTable,
            )
        }
        TenantContextHolder.setTenantId(1L)

        commandService = ProfileCommandServiceImpl(DatabaseSyncChangeWriter(emptyList()))
        val validator = Validation.buildDefaultValidatorFactory().validator
        val handler = MemberProfileSyncCommandHandler(commandService, validator)
        syncService = SyncCommandServiceImpl(
            SyncCommandProcessor(SyncCommandDispatcher(listOf(handler))),
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `profile update applies once then returns a replayed receipt`() {
        val userId = insertUser("Alice", "alice@example.com")

        val first = transaction {
            syncService.submit(userId, batch(updateCommand("profile-1", userId, 1, "Bob")))
        }.results.single()
        val replay = transaction {
            syncService.submit(userId, batch(updateCommand("profile-1", userId, 1, "Bob")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertEquals(2, first.serverVersion)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, replay.outcome)
        assertEquals(2, replay.serverVersion)
        assertTrue(replay.replayed)
        assertEquals("Bob", MemberUserDao.selectById(userId)?.nickname)
        assertEquals(2, MemberUserDao.selectById(userId)?.profileVersion)
    }

    @Test
    fun `stale profile update returns the current editable snapshot`() {
        val userId = insertUser("Alice", "alice@example.com")
        transaction {
            syncService.submit(userId, batch(updateCommand("profile-1", userId, 1, "Bob")))
        }

        val conflict = transaction {
            syncService.submit(userId, batch(updateCommand("profile-2", userId, 1, "Carol")))
        }.results.single()
        val replay = transaction {
            syncService.submit(userId, batch(updateCommand("profile-2", userId, 1, "Carol")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, conflict.outcome)
        assertEquals(2, conflict.serverVersion)
        assertEquals("Bob", JsonUtils.getText(conflict.serverPayload, "nickname"))
        assertEquals(2, conflict.serverPayload?.get("profileVersion")?.asLong())
        assertEquals(AppSyncCommandOutcomeVO.CONFLICT, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(conflict.serverPayload.toString(), replay.serverPayload.toString())
        assertEquals("Bob", MemberUserDao.selectById(userId)?.nickname)
    }

    @Test
    fun `profile command cannot target another user`() {
        val userId = insertUser("Alice", "alice@example.com")
        val otherUserId = insertUser("Mallory", "mallory@example.com")

        val rejected = transaction {
            syncService.submit(userId, batch(updateCommand("profile-other", otherUserId, 1, "Changed")))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, rejected.outcome)
        assertEquals("INVALID_AGGREGATE_ID", rejected.errorCode)
        assertEquals("Mallory", MemberUserDao.selectById(otherUserId)?.nickname)
    }

    @Test
    fun `duplicate email is a deterministic rejected outcome`() {
        val userId = insertUser("Alice", "alice@example.com")
        insertUser("Bob", "bob@example.com")
        val command = updateCommand("profile-email", userId, 1, "Alice", "bob@example.com")

        val first = transaction { syncService.submit(userId, batch(command)) }.results.single()
        val replay = transaction { syncService.submit(userId, batch(command)) }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, first.outcome)
        assertEquals("EMAIL_ALREADY_USED", first.errorCode)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.REJECTED, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals("alice@example.com", MemberUserDao.selectById(userId)?.email)
        assertEquals(1, MemberUserDao.selectById(userId)?.profileVersion)
    }

    @Test
    fun `online profile writes increment only profile version and enter the change feed`() {
        val userId = insertUser("Alice", "alice@example.com")

        val result = commandService.update(userId, updateRequest("Online", "online@example.com"))
        val changes = syncService.getChanges(userId, AppSyncChangesQuery("member-profile", 0, 100))

        assertTrue(result is ProfileMutationResult.Applied)
        assertEquals(2, MemberUserDao.selectById(userId)?.profileVersion)
        assertEquals(listOf(2L), changes.items.map { it.aggregateVersion })
        assertEquals(listOf("UPSERT"), changes.items.map { it.operation })
        assertEquals("Online", JsonUtils.getText(changes.items.single().payload, "nickname"))
    }

    private fun insertUser(nickname: String, email: String): Long = MemberUserDao.insert(
        MemberUserDO().apply {
            this.nickname = nickname
            this.email = email
            avatar = null
            sex = 0
            password = "password"
            status = 0
            registerIp = "127.0.0.1"
            point = 10
            experience = 20
        },
    )

    private fun updateRequest(
        nickname: String,
        email: String = "alice@example.com",
    ): AppMemberUserUpdateReqVO = AppMemberUserUpdateReqVO().apply {
        this.nickname = nickname
        avatar = null
        this.email = email
        sex = 1
    }

    private fun updateCommand(
        commandId: String,
        userId: Long,
        baseVersion: Long,
        nickname: String,
        email: String = "alice@example.com",
    ): AppSyncCommandReqVO = AppSyncCommandReqVO().apply {
        this.commandId = commandId
        aggregateType = "member-profile"
        aggregateId = userId.toString()
        operation = "update"
        this.baseVersion = baseVersion
        payload = JsonUtils.objectMapper.valueToTree(updateRequest(nickname, email))
    }

    private fun batch(command: AppSyncCommandReqVO): AppSyncCommandBatchReqVO =
        AppSyncCommandBatchReqVO().apply { commands = listOf(command) }
}
