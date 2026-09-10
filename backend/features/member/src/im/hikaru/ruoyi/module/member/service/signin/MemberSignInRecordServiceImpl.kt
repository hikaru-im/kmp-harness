package im.hikaru.ruoyi.module.member.service.signin

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record.MemberSignInRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.record.AppMemberSignInRecordSummaryRespVO
import im.hikaru.ruoyi.module.member.convert.signin.MemberSignInRecordConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInRecordDO
import im.hikaru.ruoyi.module.member.dal.mysql.signin.MemberSignInRecordDao
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.SIGN_IN_RECORD_TODAY_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.MemberExperienceBizTypeEnum
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import im.hikaru.ruoyi.module.member.service.point.MemberPointRecordService
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberSignInRecordServiceImpl(
    private val signInConfigService: MemberSignInConfigService,
    private val pointRecordService: MemberPointRecordService,
    private val memberLevelService: MemberLevelService,
    private val dateProvider: MemberSignInDateProvider,
) : MemberSignInRecordService {

    override fun getSignInRecordSummary(userId: Long): AppMemberSignInRecordSummaryRespVO {
        val count = MemberSignInRecordDao.selectCountByUserId(userId)
        val summary = AppMemberSignInRecordSummaryRespVO().apply {
            totalDay = count.toInt()
            continuousDay = 0
            todaySignIn = false
        }
        val lastRecord = MemberSignInRecordDao.selectLastByUserId(userId) ?: return summary
        val currentDate = dateProvider.currentDate()
        val lastDate = lastRecord.signDate ?: lastRecord.createTime?.date ?: return summary
        summary.todaySignIn = lastDate == currentDate
        if (summary.todaySignIn == true || lastDate == currentDate.minus(1, DateTimeUnit.DAY)) {
            summary.continuousDay = lastRecord.day ?: 0
        }
        return summary
    }

    override fun getSignInRecordPage(pageReqVO: MemberSignInRecordPageReqVO): PageResult<MemberSignInRecordDO> {
        val userIds = pageReqVO.nickname?.takeIf(String::isNotBlank)?.let { nickname ->
            val users = MemberUserDao.selectListByNicknameLike(nickname)
            if (users.isEmpty()) return PageResult.empty()
            users.mapNotNull { it.id }.toSet()
        }
        return MemberSignInRecordDao.selectPage(pageReqVO, userIds)
    }

    override fun getSignRecordPage(userId: Long, pageParam: PageParam): PageResult<MemberSignInRecordDO> =
        MemberSignInRecordDao.selectPage(userId, pageParam)

    @Transactional(rollbackFor = [Exception::class])
    override fun createSignRecord(userId: Long): MemberSignInRecordDO =
        when (val result = createSignRecord(userId, dateProvider.currentDate())) {
            is SignInMutationResult.Applied -> result.record
            SignInMutationResult.AlreadySigned -> throw exception(SIGN_IN_RECORD_TODAY_EXISTS)
            is SignInMutationResult.DateNotCurrent -> error("Current date changed during online sign-in")
            SignInMutationResult.UserNotFound -> throw exception(USER_NOT_EXISTS)
        }

    @Transactional(rollbackFor = [Exception::class])
    override fun createSignRecord(userId: Long, requestedDate: LocalDate): SignInMutationResult {
        val currentDate = dateProvider.currentDate()
        if (requestedDate != currentDate) return SignInMutationResult.DateNotCurrent(currentDate)
        if (MemberUserDao.selectById(userId) == null) return SignInMutationResult.UserNotFound

        val lastRecord = MemberSignInRecordDao.selectLastByUserId(userId)
        val lastDate = lastRecord?.signDate ?: lastRecord?.createTime?.date
        if (lastDate == currentDate || MemberSignInRecordDao.selectByUserIdAndSignDate(userId, currentDate) != null) {
            return SignInMutationResult.AlreadySigned
        }
        val configs = signInConfigService.getSignInConfigList(CommonStatusEnum.ENABLE.status)
        val record = MemberSignInRecordConvert.convert(userId, lastRecord, configs, currentDate)
        if (!MemberSignInRecordDao.insertIfAbsent(record)) return SignInMutationResult.AlreadySigned
        record.point?.takeIf { it != 0 }?.let {
            pointRecordService.createPointRecord(userId, it, MemberPointBizTypeEnum.SIGN, requireNotNull(record.id).toString())
        }
        record.experience?.takeIf { it != 0 }?.let {
            memberLevelService.addExperience(userId, it, MemberExperienceBizTypeEnum.SIGN_IN, requireNotNull(record.id).toString())
        }
        return SignInMutationResult.Applied(record, getSignInRecordSummary(userId))
    }
}
