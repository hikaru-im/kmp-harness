package im.hikaru.ruoyi.module.member.api.user

import im.hikaru.ruoyi.module.member.api.user.dto.MemberUserRespDTO

interface MemberUserApi {
    fun getUser(id: Long): MemberUserRespDTO
    fun getUserList(ids: Collection<Long>): List<MemberUserRespDTO>
    fun getUserMap(ids: Collection<Long>): Map<Long, MemberUserRespDTO> =
        getUserList(ids).associateBy { requireNotNull(it.id) }
    fun getUserListByNickname(nickname: String): List<MemberUserRespDTO>
    fun getUserByMobile(mobile: String): MemberUserRespDTO
    fun validateUser(id: Long): Unit
}
