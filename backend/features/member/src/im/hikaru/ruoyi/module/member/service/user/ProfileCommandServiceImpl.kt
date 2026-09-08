package im.hikaru.ruoyi.module.member.service.user

import im.hikaru.contracts.app.member.MemberProfileSyncContract
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.dal.mysql.user.MemberUserDao
import im.hikaru.ruoyi.module.sync.service.SyncChangeOperation
import im.hikaru.ruoyi.module.sync.service.SyncChangeWriter
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileCommandServiceImpl(
    private val changeWriter: SyncChangeWriter,
) : ProfileCommandService {
    @Transactional(rollbackFor = [Exception::class])
    override fun update(
        userId: Long,
        request: AppMemberUserUpdateReqVO,
        expectedVersion: Long?,
    ): ProfileMutationResult {
        repeat(MAX_OPTIMISTIC_ATTEMPTS) {
            val current = MemberUserDao.selectById(userId) ?: return ProfileMutationResult.NotFound
            if (expectedVersion != null && current.profileVersion != expectedVersion) {
                return ProfileMutationResult.Conflict(current)
            }
            if (!isEmailAvailable(userId, request.email)) {
                return ProfileMutationResult.Rejected(EMAIL_ALREADY_USED)
            }

            val update = MemberUserDO().apply {
                id = userId
                nickname = request.nickname
                avatar = request.avatar
                email = request.email
                sex = request.sex
            }
            if (MemberUserDao.updateProfileByIdAndVersion(userId, update, current.profileVersion) == 1) {
                val applied = checkNotNull(MemberUserDao.selectById(userId))
                appendUpsert(applied)
                return ProfileMutationResult.Applied(applied)
            }

            if (expectedVersion != null) {
                return MemberUserDao.selectById(userId)
                    ?.let(ProfileMutationResult::Conflict)
                    ?: ProfileMutationResult.NotFound
            }
        }
        return MemberUserDao.selectById(userId)
            ?.let(ProfileMutationResult::Conflict)
            ?: ProfileMutationResult.NotFound
    }

    private fun isEmailAvailable(userId: Long, email: String?): Boolean =
        email.isNullOrBlank() || MemberUserDao.selectByEmail(email)?.id.let { it == null || it == userId }

    private fun appendUpsert(profile: MemberUserDO) {
        val userId = requireNotNull(profile.id)
        changeWriter.append(
            context = SyncCommandContext(
                tenantId = TenantContextHolder.getRequiredTenantId(),
                userId = userId,
            ),
            resource = MemberProfileSyncContract.RESOURCE,
            aggregateId = userId.toString(),
            operation = SyncChangeOperation.UPSERT,
            aggregateVersion = profile.profileVersion,
            payload = ProfileChangePayload(
                id = userId,
                nickname = profile.nickname,
                avatar = profile.avatar,
                email = profile.email,
                sex = profile.sex,
                profileVersion = profile.profileVersion,
            ),
        )
    }

    private companion object {
        const val EMAIL_ALREADY_USED = "EMAIL_ALREADY_USED"
        const val MAX_OPTIMISTIC_ATTEMPTS = 3
    }
}

private data class ProfileChangePayload(
    val id: Long,
    val nickname: String?,
    val avatar: String?,
    val email: String?,
    val sex: Int?,
    val profileVersion: Long,
)
