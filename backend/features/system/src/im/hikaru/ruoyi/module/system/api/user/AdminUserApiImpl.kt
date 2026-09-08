package im.hikaru.ruoyi.module.system.api.user

import im.hikaru.ruoyi.module.system.api.user.dto.AdminUserRespDTO
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import org.springframework.stereotype.Service

@Service
class AdminUserApiImpl(
    private val userService: AdminUserService,
    private val deptService: DeptService,
) : AdminUserApi {
    override fun getUser(id: Long): AdminUserRespDTO? = userService.getUser(id)?.toDto()
    override fun getUserListBySubordinate(id: Long): List<AdminUserRespDTO> {
        val depts = deptService.getDeptListByLeaderUserId(id)
        val ids = depts.mapNotNull { it.id }.toMutableSet()
        ids += deptService.getChildDeptList(ids).mapNotNull { it.id }
        return userService.getUserListByDeptIds(ids).filter { it.id != id }.map { it.toDto() }
    }
    override fun getUserList(ids: Collection<Long>) = userService.getUserList(ids).map { it.toDto() }
    override fun getUserListByDeptIds(deptIds: Collection<Long>) = userService.getUserListByDeptIds(deptIds).map { it.toDto() }
    override fun getUserListByPostIds(postIds: Collection<Long>) = userService.getUserListByPostIds(postIds).map { it.toDto() }
    override fun getUserListByNickname(nickname: String) = userService.getUserListByNickname(nickname).map { it.toDto() }
    override fun validateUserList(ids: Collection<Long>) = userService.validateUserList(ids)
    private fun im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO.toDto() = AdminUserRespDTO().apply { id = this@toDto.id; nickname = this@toDto.nickname; status = this@toDto.status; deptId = this@toDto.deptId; postIds = this@toDto.postIds; mobile = this@toDto.mobile; avatar = this@toDto.avatar }
}
