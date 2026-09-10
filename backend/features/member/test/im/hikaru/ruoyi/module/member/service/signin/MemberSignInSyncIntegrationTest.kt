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
            MemberSignInDateProvider { currentDate },
        )
        syncService = SyncCommandServiceImpl(
            SyncCommandProcessor(
                SyncCommandDispatcher(emptyList(), im.hikaru.ruoyi.module.sync.service.SyncCommandWhitelist()),
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `old queued sign in is consistently rejected without rewards`() {
        val userId = insertUser()
        for (date in listOf(currentDate, LocalDate(2026, 7, 26))) {
            val command = createCommand("old-$date", userId, date)
            repeat(2) {
                val result = transaction { syncService.submit(userId, batch(command)) }.results.single()
                assertEquals(AppSyncCommandOutcomeVO.REJECTED, result.outcome)
                assertEquals("SYNC_NOT_ALLOWED", result.errorCode)
            }
        }
        assertEquals(0, MemberSignInRecordDao.selectCountByUserId(userId))
        org.mockito.Mockito.verifyNoInteractions(pointService, levelService)
    }

    @Test
    fun `online sign in awards once without entering sync feed`() {
        val userId = insertUser()
        val record = recordService.createSignRecord(userId)
        assertEquals(currentDate, record.signDate)
        assertEquals(true, recordService.getSignInRecordSummary(userId).todaySignIn)
        assertTrue(syncService.getChanges(userId, AppSyncChangesQuery("member-sign-in", 0, 100)).items.isEmpty())
        verify(pointService, times(1)).createPointRecord(userId, 10, MemberPointBizTypeEnum.SIGN, record.id.toString())
        verify(levelService, times(1)).addExperience(userId, 5, MemberExperienceBizTypeEnum.SIGN_IN, record.id.toString())
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
