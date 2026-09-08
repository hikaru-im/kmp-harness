package im.hikaru.ruoyi.module.member.service.level

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelListReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.MemberLevelUpdateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserUpdateLevelReqVO
import im.hikaru.ruoyi.module.member.convert.level.MemberLevelConvert
import im.hikaru.ruoyi.module.member.convert.level.MemberLevelRecordConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelDO
import im.hikaru.ruoyi.module.member.dal.dataobject.level.MemberLevelRecordDO
import im.hikaru.ruoyi.module.member.dal.mysql.level.MemberLevelDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_EXPERIENCE_MAX
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_EXPERIENCE_MIN
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_HAS_USER
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_NAME_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_VALUE_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberLevelServiceImpl(
    private val memberLevelRecordService: MemberLevelRecordService,
    private val memberExperienceRecordService: MemberExperienceRecordService,
    private val memberUserService: MemberUserService,
) : MemberLevelService {

    override fun createLevel(createReqVO: MemberLevelCreateReqVO): Long {
        validateConfigValid(null, requireNotNull(createReqVO.name), requireNotNull(createReqVO.level), requireNotNull(createReqVO.experience))
        return MemberLevelDao.insert(MemberLevelConvert.convert(createReqVO))
    }

    override fun updateLevel(updateReqVO: MemberLevelUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateLevelExists(id)
        validateConfigValid(id, requireNotNull(updateReqVO.name), requireNotNull(updateReqVO.level), requireNotNull(updateReqVO.experience))
        MemberLevelDao.updateById(MemberLevelConvert.convert(updateReqVO))
    }

    override fun deleteLevel(id: Long) {
        validateLevelExists(id)
        if (memberUserService.getUserCountByLevelId(id) > 0) throw exception(LEVEL_HAS_USER)
        MemberLevelDao.deleteById(id)
    }

    override fun getLevel(id: Long): MemberLevelDO? = id.takeIf { it > 0 }?.let(MemberLevelDao::selectById)

    override fun getLevelList(ids: Collection<Long>): List<MemberLevelDO> = MemberLevelDao.selectByIds(ids)

    override fun getLevelList(listReqVO: MemberLevelListReqVO): List<MemberLevelDO> = MemberLevelDao.selectList(listReqVO)

    override fun getLevelListByStatus(status: Int): List<MemberLevelDO> = MemberLevelDao.selectListByStatus(status)

    override fun getEnableLevelList(): List<MemberLevelDO> = getLevelListByStatus(CommonStatusEnum.ENABLE.status)

    @Transactional(rollbackFor = [Exception::class])
    override fun updateUserLevel(updateReqVO: MemberUserUpdateLevelReqVO) {
        val userId = requireNotNull(updateReqVO.id)
        val user = memberUserService.getUser(userId) ?: throw exception(USER_NOT_EXISTS)
        val requestedLevelId = updateReqVO.levelId
        val normalizedLevelId = requestedLevelId ?: 0L
        if ((user.levelId ?: 0L) == normalizedLevelId) return

        val record = MemberLevelRecordDO().apply {
            this.userId = userId
            remark = updateReqVO.reason
        }
        val newLevel = if (requestedLevelId == null || requestedLevelId == 0L) {
            record.levelId = 0L
            record.experience = -(user.experience ?: 0)
            record.userExperience = 0
            record.description = "管理员取消了等级"
            null
        } else {
            validateLevelExists(requestedLevelId).also { level ->
                MemberLevelRecordConvert.copyTo(level, record)
                record.experience = (level.experience ?: 0) - (user.experience ?: 0)
                record.userExperience = level.experience ?: 0
                record.description = "管理员调整为：${level.name.orEmpty()}"
            }
        }
        memberLevelRecordService.createLevelRecord(record)
        memberExperienceRecordService.createExperienceRecord(
            userId,
            record.experience ?: 0,
            record.userExperience ?: 0,
            MemberExperienceBizTypeEnum.ADMIN,
            MemberExperienceBizTypeEnum.ADMIN.type.toString(),
        )
        memberUserService.updateUserLevel(userId, record.levelId ?: 0L, record.userExperience ?: 0)
        notifyMemberLevelChange(userId, newLevel)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun addExperience(userId: Long, experience: Int, bizType: MemberExperienceBizTypeEnum, bizId: String) {
        if (experience == 0) return
        val actualExperience = if (!bizType.add && experience > 0) -experience else experience
        val user = memberUserService.getUser(userId) ?: throw exception(USER_NOT_EXISTS)
        val totalExperience = ((user.experience ?: 0) + actualExperience).coerceAtLeast(0)
        memberExperienceRecordService.createExperienceRecord(userId, actualExperience, totalExperience, bizType, bizId)

        val record = MemberLevelRecordDO().apply {
            this.userId = userId
            this.experience = actualExperience
            userExperience = totalExperience
            levelId = user.levelId ?: 0L
        }
        val newLevel = calculateNewLevel(user.levelId, totalExperience)
        if (newLevel != null) {
            MemberLevelRecordConvert.copyTo(newLevel, record)
            record.description = "经验变动，等级调整为：${newLevel.name.orEmpty()}"
            memberLevelRecordService.createLevelRecord(record)
            notifyMemberLevelChange(userId, newLevel)
        }
        memberUserService.updateUserLevel(userId, record.levelId ?: 0L, totalExperience)
    }

    internal fun validateLevelExists(id: Long): MemberLevelDO =
        MemberLevelDao.selectById(id) ?: throw exception(LEVEL_NOT_EXISTS)

    internal fun validateConfigValid(id: Long?, name: String, level: Int, experience: Int) {
        val levels = MemberLevelDao.selectList()
        levels.firstOrNull { it.name == name && it.id != id }?.let { throw exception(LEVEL_NAME_EXISTS, it.name) }
        levels.firstOrNull { it.level == level && it.id != id }?.let { throw exception(LEVEL_VALUE_EXISTS, it.level, it.name) }
        levels.filter { it.id != id }.forEach { configured ->
            val configuredLevel = configured.level ?: return@forEach
            val configuredExperience = configured.experience ?: return@forEach
            if (configuredLevel < level && experience <= configuredExperience) {
                throw exception(LEVEL_EXPERIENCE_MIN, configured.name, configuredExperience)
            }
            if (configuredLevel > level && experience >= configuredExperience) {
                throw exception(LEVEL_EXPERIENCE_MAX, configured.name, configuredExperience)
            }
        }
    }

    private fun calculateNewLevel(currentLevelId: Long?, experience: Int): MemberLevelDO? {
        val matched = getEnableLevelList()
            .filter { experience >= (it.experience ?: Int.MAX_VALUE) }
            .maxByOrNull { it.level ?: Int.MIN_VALUE }
            ?: return null
        return matched.takeUnless { it.id == currentLevelId }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun notifyMemberLevelChange(userId: Long, level: MemberLevelDO?) {
        // Notification delivery is owned by the system message APIs and can be wired independently.
    }
}
