package im.hikaru.ruoyi.module.member.service.user

import im.hikaru.ruoyi.module.member.controller.app.user.vo.AppMemberUserUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO

interface ProfileCommandService {
    fun update(
        userId: Long,
        request: AppMemberUserUpdateReqVO,
        expectedVersion: Long? = null,
    ): ProfileMutationResult
}

sealed interface ProfileMutationResult {
    data class Applied(val profile: MemberUserDO) : ProfileMutationResult
    data object NotFound : ProfileMutationResult
    data class Rejected(val errorCode: String) : ProfileMutationResult
    data class Conflict(val serverProfile: MemberUserDO) : ProfileMutationResult {
        val serverVersion: Long get() = serverProfile.profileVersion
    }
}
