package im.hikaru.ruoyi.module.system.api.user

import im.hikaru.ruoyi.module.system.api.user.dto.AdminUserRespDTO

interface AdminUserApi {
    fun getUser(id: Long): AdminUserRespDTO?
    fun getUserListBySubordinate(id: Long): List<AdminUserRespDTO>
    fun getUserList(ids: Collection<Long>): List<AdminUserRespDTO>
    fun getUserListByDeptIds(deptIds: Collection<Long>): List<AdminUserRespDTO>
    fun getUserListByPostIds(postIds: Collection<Long>): List<AdminUserRespDTO>
    fun getUserListByNickname(nickname: String): List<AdminUserRespDTO>
    fun getUserMap(ids: Collection<Long>): Map<Long, AdminUserRespDTO> =
        getUserList(ids).mapNotNull { dto -> dto.id?.let { it to dto } }.toMap()
    fun validateUser(id: Long) = validateUserList(listOf(id))
    fun validateUserList(ids: Collection<Long>)
}
