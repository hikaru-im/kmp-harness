package im.hikaru.ruoyi.module.member.service.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.biz.system.oauth2.OAuth2TokenCommonApi
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.MemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserResetPasswordReqVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateMobileByWeixinReqVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateMobileReqVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdatePasswordReqVO
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_MOBILE_USED
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_EMAIL_USED
import im.hikaru.ruoyi.module.member.mq.producer.user.MemberUserProducer
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.validation.annotation.Validated
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Clock

@Service
@Validated
class MemberUserServiceImpl(
    private val memberUserProducer: MemberUserProducer,
    private val profileCommandService: ProfileCommandService,
    private val smsCodeApi: SmsCodeApi? = null,
    private val socialClientApi: SocialClientApi? = null,
    private val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder(),
    private val oauth2TokenApi: OAuth2TokenCommonApi? = null,
) : MemberUserService {

    override fun getUserByMobile(mobile: String): MemberUserDO? = MemberUserDao.selectByMobile(mobile)

    override fun getUserListByNickname(nickname: String): List<MemberUserDO> =
        MemberUserDao.selectListByNicknameLike(nickname)

    @Transactional(rollbackFor = [Exception::class])
    override fun createUserIfAbsent(mobile: String, registerIp: String, terminal: Int): MemberUserDO =
        MemberUserDao.selectByMobile(mobile) ?: createUserInternal(mobile, null, null, registerIp, terminal)

    @Transactional(rollbackFor = [Exception::class])
    override fun createUser(nickname: String?, avtar: String?, registerIp: String, terminal: Int): MemberUserDO =
        createUserInternal(null, nickname, avtar, registerIp, terminal)

    override fun updateUserLogin(id: Long, loginIp: String) {
        validateUserExists(id)
        MemberUserDao.updateById(MemberUserDO().apply {
            this.id = id
            this.loginIp = loginIp
            loginDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        })
    }

    override fun getUser(id: Long): MemberUserDO? = MemberUserDao.selectById(id)

    override fun getUserList(ids: Collection<Long>): List<MemberUserDO> = MemberUserDao.selectByIds(ids)

    override fun updateUser(userId: Long, reqVO: AppMemberUserUpdateReqVO) {
        when (profileCommandService.update(userId, reqVO)) {
            is ProfileMutationResult.Applied -> Unit
            ProfileMutationResult.NotFound -> throw exception(USER_NOT_EXISTS)
            is ProfileMutationResult.Rejected -> throw exception(USER_EMAIL_USED, reqVO.email)
            is ProfileMutationResult.Conflict -> error("Profile changed too frequently to update")
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateUserMobile(userId: Long, reqVO: AppMemberUserUpdateMobileReqVO) {
        val user = validateUserExists(userId)
        val mobile = requireNotNull(reqVO.mobile)
        validateMobileUnique(userId, mobile)
        reqVO.oldCode?.takeIf(String::isNotBlank)?.let { code ->
            useSmsCode(requireNotNull(user.mobile), code, SmsSceneEnum.MEMBER_UPDATE_MOBILE)
        }
        useSmsCode(mobile, requireNotNull(reqVO.code), SmsSceneEnum.MEMBER_UPDATE_MOBILE)
        MemberUserDao.updateById(MemberUserDO().apply { id = userId; this.mobile = mobile })
    }

    override fun updateUserMobileByWeixin(userId: Long, reqVO: AppMemberUserUpdateMobileByWeixinReqVO) {
        validateUserExists(userId)
        val client = checkNotNull(socialClientApi) { "SocialClientApi is required for Weixin mobile updates" }
        val mobile = client.getWxMaPhoneNumberInfo(
            im.hikaru.ruoyi.framework.common.enums.UserTypeEnum.MEMBER.value,
            requireNotNull(reqVO.code),
        )?.phoneNumber ?: error("Weixin did not return a phone number")
        validateMobileUnique(userId, mobile)
        MemberUserDao.updateById(MemberUserDO().apply { id = userId; this.mobile = mobile })
    }

    override fun updateUserPassword(userId: Long, reqVO: AppMemberUserUpdatePasswordReqVO) {
        val user = validateUserExists(userId)
        useSmsCode(requireNotNull(user.mobile), requireNotNull(reqVO.code), SmsSceneEnum.MEMBER_UPDATE_PASSWORD)
        MemberUserDao.updateById(MemberUserDO().apply {
            id = userId
            password = passwordEncoder.encode(requireNotNull(reqVO.password))
        })
    }

    override fun resetUserPassword(reqVO: AppMemberUserResetPasswordReqVO) {
        val mobile = requireNotNull(reqVO.mobile)
        val user = MemberUserDao.selectByMobile(mobile) ?: throw exception(USER_MOBILE_NOT_EXISTS)
        useSmsCode(mobile, requireNotNull(reqVO.code), SmsSceneEnum.MEMBER_RESET_PASSWORD)
        MemberUserDao.updateById(MemberUserDO().apply {
            id = user.id
            password = passwordEncoder.encode(requireNotNull(reqVO.password))
        })
    }

    override fun isPasswordMatch(rawPassword: String, encodedPassword: String): Boolean =
        passwordEncoder.matches(rawPassword, encodedPassword)

    @Transactional(rollbackFor = [Exception::class])
    override fun updateUser(updateReqVO: MemberUserUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id)
        val current = validateUserExists(id)
        validateMobileUnique(id, updateReqVO.mobile)
        MemberUserDao.updateById(MemberUserDO().apply {
            this.id = id
            mobile = updateReqVO.mobile
            status = updateReqVO.status?.toInt()
            name = updateReqVO.name
            areaId = updateReqVO.areaId?.toInt()
            birthday = updateReqVO.birthday?.toKotlinLocalDateTime()
            mark = updateReqVO.mark
            tagIds = updateReqVO.tagIds
            levelId = updateReqVO.levelId
            groupId = updateReqVO.groupId
        })
        if (CommonStatusEnum.isDisable(updateReqVO.status?.toInt())) {
            oauth2TokenApi?.removeAccessToken(id, UserTypeEnum.MEMBER.value)
        }
        val profileRequest = AppMemberUserUpdateReqVO().apply {
            nickname = updateReqVO.nickname ?: current.nickname
            avatar = updateReqVO.avatar ?: current.avatar
            email = updateReqVO.email ?: current.email
            sex = updateReqVO.sex ?: current.sex
        }
        if (
            profileRequest.nickname != current.nickname ||
            profileRequest.avatar != current.avatar ||
            profileRequest.email != current.email ||
            profileRequest.sex != current.sex
        ) {
            when (profileCommandService.update(id, profileRequest)) {
                is ProfileMutationResult.Applied -> Unit
                ProfileMutationResult.NotFound -> throw exception(USER_NOT_EXISTS)
                is ProfileMutationResult.Rejected -> throw exception(USER_EMAIL_USED, profileRequest.email)
                is ProfileMutationResult.Conflict -> error("Member profile changed too frequently to update")
            }
        }
    }

    override fun getUserPage(pageReqVO: MemberUserPageReqVO): PageResult<MemberUserDO> =
        MemberUserDao.selectPage(pageReqVO)

    override fun updateUserLevel(id: Long, levelId: Long, experience: Int) {
        validateUserExists(id)
        MemberUserDao.updateById(MemberUserDO().apply {
            this.id = id
            this.levelId = levelId
            this.experience = experience
        })
    }

    override fun getUserCountByGroupId(groupId: Long): Long = MemberUserDao.selectCountByGroupId(groupId)

    override fun getUserCountByLevelId(levelId: Long): Long = MemberUserDao.selectCountByLevelId(levelId)

    override fun getUserCountByTagId(tagId: Long): Long = MemberUserDao.selectCountByTagId(tagId)

    override fun updateUserPoint(id: Long, point: Int): Boolean = point == 0 || MemberUserDao.updatePoint(id, point) > 0

    private fun createUserInternal(
        mobile: String?,
        nickname: String?,
        avatar: String?,
        registerIp: String,
        terminal: Int,
    ): MemberUserDO {
        mobile?.let { validateMobileUnique(null, it) }
        val entity = MemberUserDO().apply {
            this.mobile = mobile
            password = passwordEncoder.encode(UUID.randomUUID().toString().replace("-", ""))
            status = CommonStatusEnum.ENABLE.status
            this.registerIp = registerIp
            registerTerminal = terminal
            this.nickname = nickname?.takeIf(String::isNotBlank) ?: "用户${randomDigits(6)}"
            this.avatar = avatar
            point = 0
            experience = 0
        }
        MemberUserDao.insert(entity)
        publishUserCreatedAfterCommit(requireNotNull(entity.id))
        return entity
    }

    private fun publishUserCreatedAfterCommit(userId: Long) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            memberUserProducer.sendUserCreateMessage(userId)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                memberUserProducer.sendUserCreateMessage(userId)
            }
        })
    }

    private fun validateUserExists(id: Long): MemberUserDO =
        MemberUserDao.selectById(id) ?: throw exception(USER_NOT_EXISTS)

    private fun validateMobileUnique(id: Long?, mobile: String?) {
        if (mobile.isNullOrBlank()) return
        MemberUserDao.selectByMobile(mobile)?.let { if (id == null || it.id != id) throw exception(USER_MOBILE_USED, mobile) }
    }

    private fun useSmsCode(mobile: String, code: String, scene: SmsSceneEnum) {
        smsCodeApi?.useSmsCode(SmsCodeUseReqDTO().apply {
            this.mobile = mobile
            this.code = code
            this.scene = scene.scene
            usedIp = ServletUtils.getClientIP() ?: "unknown"
        })
    }

    private fun randomDigits(length: Int): String = buildString(length) {
        repeat(length) { append(SECURE_RANDOM.nextInt(10)) }
    }

    private companion object {
        val SECURE_RANDOM = SecureRandom()
    }
}
