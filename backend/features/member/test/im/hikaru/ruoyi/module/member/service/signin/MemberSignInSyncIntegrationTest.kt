package im.hikaru.ruoyi.module.member.service.signin

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInConfigDO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.dal.mysql.signin.MemberSignInRecordDao
import im.hikaru.ruoyi.module.member.dal.mysql.signin.MemberSignInRecordTable
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserDao
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserTable
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import im.hikaru.ruoyi.module.member.service.point.MemberPointRecordService
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
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MemberSignInSyncIntegrationTest {
    private val currentDate = LocalDate(2026, 7, 27)
    private lateinit var pointService: MemberPointRecordService
    private lateinit var levelService: MemberLevelService
    private lateinit var recordService: MemberSignInRecordServiceImpl
    private lateinit var syncService: SyncCommandServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:member_sign_in_sync_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                MemberUserTable,
                MemberSignInRecordTable,
                AppSyncCommandTable,
                AppSyncChangeTable,
                AppSyncChangeRetentionTable,
            )
        }
        TenantContextHolder.setTenantId(1L)

        val configService = mock(MemberSignInConfigService::class.java)
        pointService = mock(MemberPointRecordService::class.java)
        levelService = mock(MemberLevelService::class.java)
        `when`(configService.getSignInConfigList(0)).thenReturn(
            listOf(
                MemberSignInConfigDO().apply {
                    day = 1
                    point = 10
                    experience = 5
                    status = 0
                },
            ),
        )
        recordService = MemberSignInRecordServiceImpl(
            configService,
            pointService,
            levelService,
            DatabaseSyncChangeWriter(emptyList()),
            MemberSignInDateProvider { currentDate },
        )
        syncService = SyncCommandServiceImpl(
            SyncCommandProcessor(
                SyncCommandDispatcher(listOf(MemberSignInSyncCommandHandler(recordService))),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `sign in applies once and replays the same receipt`() {
        val userId = insertUser()
        val command = createCommand("sign-in-1", userId, currentDate)

        val first = transaction { syncService.submit(userId, batch(command)) }.results.single()
        val replay = transaction { syncService.submit(userId, batch(command)) }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertFalse(first.replayed)
        assertEquals(AppSyncCommandOutcomeVO.APPLIED, replay.outcome)
        assertTrue(replay.replayed)
        assertEquals(first.serverVersion, replay.serverVersion)
        assertEquals(1, MemberSignInRecordDao.selectCountByUserId(userId))
        verify(pointService, times(1)).createPointRecord(
            userId,
            10,
            MemberPointBizTypeEnum.SIGN,
            requireNotNull(first.serverVersion).toString(),
        )
        verify(levelService, times(1)).addExperience(
            userId,
            5,
            MemberExperienceBizTypeEnum.SIGN_IN,
            requireNotNull(first.serverVersion).toString(),
        )
    }

    @Test
    fun `different commands cannot award the same business date twice`() {
        val userId = insertUser()

        val first = transaction {
            syncService.submit(userId, batch(createCommand("sign-in-1", userId, currentDate)))
        }.results.single()
        val duplicate = transaction {
            syncService.submit(userId, batch(createCommand("sign-in-2", userId, currentDate)))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.APPLIED, first.outcome)
        assertEquals(AppSyncCommandOutcomeVO.REJECTED, duplicate.outcome)
        assertEquals("SIGN_IN_ALREADY_COMPLETED", duplicate.errorCode)
        assertEquals(1, MemberSignInRecordDao.selectCountByUserId(userId))
        verify(pointService, times(1)).createPointRecord(
            userId,
            10,
            MemberPointBizTypeEnum.SIGN,
            requireNotNull(first.serverVersion).toString(),
        )
    }

    @Test
    fun `queued sign in is rejected after its requested date expires`() {
        val userId = insertUser()

        val result = transaction {
            syncService.submit(
                userId,
                batch(createCommand("sign-in-old", userId, LocalDate(2026, 7, 26))),
            )
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, result.outcome)
        assertEquals("SIGN_IN_DATE_NOT_CURRENT", result.errorCode)
        assertEquals(0, MemberSignInRecordDao.selectCountByUserId(userId))
    }

    @Test
    fun `sign in command cannot target another user`() {
        val userId = insertUser()
        val otherUserId = insertUser("Other")

        val result = transaction {
            syncService.submit(userId, batch(createCommand("sign-in-other", otherUserId, currentDate)))
        }.results.single()

        assertEquals(AppSyncCommandOutcomeVO.REJECTED, result.outcome)
        assertEquals("INVALID_AGGREGATE_ID", result.errorCode)
        assertEquals(0, MemberSignInRecordDao.selectCountByUserId(otherUserId))
    }

    @Test
    fun `online sign in uses the same mutation path and enters the change feed`() {
        val userId = insertUser()

        val record = recordService.createSignRecord(userId)
        val summary = recordService.getSignInRecordSummary(userId)
        val changes = syncService.getChanges(userId, AppSyncChangesQuery("member-sign-in", 0, 100))

        assertEquals(currentDate, record.signDate)
        assertEquals(true, summary.todaySignIn)
        assertEquals(1, summary.totalDay)
        assertEquals(listOf(requireNotNull(record.id)), changes.items.map { it.aggregateVersion })
        assertEquals(userId.toString(), changes.items.single().aggregateId)
        assertEquals("UPSERT", changes.items.single().operation)
        assertEquals(record.id, changes.items.single().payload.get("record").get("id").asLong())
        assertTrue(changes.items.single().payload.get("summary").get("todaySignIn").asBoolean())
    }

    private fun insertUser(nickname: String = "Alice"): Long = MemberUserDao.insert(
        MemberUserDO().apply {
            this.nickname = nickname
            password = "password"
            status = 0
            registerIp = "127.0.0.1"
            point = 0
            experience = 0
        },
    )

    private fun createCommand(
        commandId: String,
        userId: Long,
        requestedDate: LocalDate,
    ): AppSyncCommandReqVO = AppSyncCommandReqVO().apply {
        this.commandId = commandId
        aggregateType = "member-sign-in"
        aggregateId = userId.toString()
        operation = "create"
        baseVersion = null
        payload = JsonUtils.objectMapper.createObjectNode().put("requestedDate", requestedDate.toString())
    }

    private fun batch(command: AppSyncCommandReqVO): AppSyncCommandBatchReqVO =
        AppSyncCommandBatchReqVO().apply { commands = listOf(command) }
}
