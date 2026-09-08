package im.hikaru.ruoyi.module.system.service.user

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserImportExcelVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserImportRespVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthRegisterReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO

interface AdminUserService {
    fun createUser(req: UserSaveReqVO): Long
    fun registerUser(req: AuthRegisterReqVO): Long
    fun updateUser(req: UserSaveReqVO)
    fun updateUserLogin(id: Long, loginIp: String)
    fun updateUserProfile(id: Long, req: UserProfileUpdateReqVO)
    fun updateUserPassword(id: Long, req: UserProfileUpdatePasswordReqVO)
    fun updateUserPassword(id: Long, password: String)
    fun updateUserStatus(id: Long, status: Int)
    fun deleteUser(id: Long)
    fun deleteUserList(ids: List<Long>)
    fun getUserByUsername(username: String): AdminUserDO?
    fun getUserByMobile(mobile: String): AdminUserDO?
    fun getUserPage(req: UserPageReqVO): PageResult<AdminUserDO>
    fun getUser(id: Long): AdminUserDO?
    fun getUserListByDeptIds(deptIds: Collection<Long>): List<AdminUserDO>
    fun getUserListByPostIds(postIds: Collection<Long>): List<AdminUserDO>
    fun getUserList(ids: Collection<Long>): List<AdminUserDO>
    fun getUserMap(ids: Collection<Long>): Map<Long, AdminUserDO> =
        if (ids.isEmpty()) emptyMap() else getUserList(ids).associateBy { requireNotNull(it.id) }
    fun getUserListAll(): List<AdminUserDO>
    fun validateUserList(ids: Collection<Long>)
    fun getUserListByNickname(nickname: String): List<AdminUserDO>
    fun getUserListByStatus(status: Int): List<AdminUserDO>
    fun getDeptUsers(deptIds: Collection<Long>): List<AdminUserDO>
    fun importUserList(importUsers: List<UserImportExcelVO>, isUpdateSupport: Boolean): UserImportRespVO
    fun isPasswordMatch(rawPassword: String, encodedPassword: String): Boolean
}
