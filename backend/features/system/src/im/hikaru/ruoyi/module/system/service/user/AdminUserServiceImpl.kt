package im.hikaru.ruoyi.module.system.service.user

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.validation.ValidationUtils
import im.hikaru.ruoyi.module.infra.api.config.ConfigApi
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthRegisterReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserImportExcelVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserImportRespVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.UserPostDO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.dal.mysql.dept.UserPostDao
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_EMAIL_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_COUNT_MAX
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_IS_DISABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_IMPORT_INIT_PASSWORD
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_IMPORT_LIST_IS_EMPTY
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_MOBILE_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_PASSWORD_FAILED
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_USERNAME_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.USER_REGISTER_DISABLED
import im.hikaru.ruoyi.module.system.mq.producer.user.AdminUserProducer
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.dept.PostService
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import im.hikaru.ruoyi.module.system.service.tenant.handler.TenantInfoHandler
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import jakarta.validation.ConstraintViolationException
import kotlin.time.Clock

@Service("adminUserService")
@Validated
class AdminUserServiceImpl(
    private val deptService: DeptService,
    private val postService: PostService,
    private val permissionService: PermissionService,
    private val tenantServiceProvider: ObjectProvider<TenantService>,
    private val oauth2TokenServiceProvider: ObjectProvider<OAuth2TokenService>,
    private val configApi: ConfigApi,
    private val adminUserProducer: AdminUserProducer? = null,
) : AdminUserService {
    private val passwordEncoder = BCryptPasswordEncoder()

    override fun createUser(req: UserSaveReqVO): Long {
        tenantServiceProvider.ifAvailable?.handleTenantInfo(TenantInfoHandler { tenant ->
            if (AdminUserDao.selectCount() >= (tenant.accountCount ?: Int.MAX_VALUE)) throw exception(USER_COUNT_MAX, tenant.accountCount ?: 0)
        })
        validateUserForCreateOrUpdate(null, req)
        val entity = req.toEntity().apply {
            status = CommonStatusEnum.ENABLE.status
            password = passwordEncoder.encode(requireNotNull(req.password))
        }
        val id = AdminUserDao.insert(entity)
        syncPosts(id, entity.postIds)
        return id
    }

    override fun registerUser(req: AuthRegisterReqVO): Long {
        if (configApi.getConfigValueByKey(USER_REGISTER_ENABLED_KEY) != "true") {
            throw exception(USER_REGISTER_DISABLED)
        }
        tenantServiceProvider.ifAvailable?.handleTenantInfo(TenantInfoHandler { tenant ->
            if (AdminUserDao.selectCount() >= (tenant.accountCount ?: Int.MAX_VALUE)) {
                throw exception(USER_COUNT_MAX, tenant.accountCount ?: 0)
            }
        })
        val username = requireNotNull(req.username)
        AdminUserDao.selectByUsername(username)?.let { throw exception(USER_USERNAME_EXISTS) }
        return AdminUserDao.insert(AdminUserDO().apply {
            this.username = username
            nickname = req.nickname
            status = CommonStatusEnum.ENABLE.status
            password = passwordEncoder.encode(requireNotNull(req.password))
        })
    }

    override fun updateUser(req: UserSaveReqVO) {
        val id = requireNotNull(req.id)
        val oldUser = validateExists(id)
        validateUserForCreateOrUpdate(id, req)
        AdminUserDao.updateById(req.toEntity().apply { this.id = id; password = null })
        syncPosts(id, req.postIds)
        publishUserProfileUpdatedIfChanged(oldUser, req.nickname, req.avatar)
    }

    override fun updateUserLogin(id: Long, loginIp: String) {
        AdminUserDao.updateById(AdminUserDO().apply { this.id = id; this.loginIp = loginIp; loginDate = now() })
    }

    override fun updateUserProfile(id: Long, req: UserProfileUpdateReqVO) {
        val oldUser = validateExists(id)
        validateEmailUnique(id, req.email); validateMobileUnique(id, req.mobile)
        AdminUserDao.updateById(AdminUserDO().apply { this.id = id; nickname = req.nickname; email = req.email; mobile = req.mobile; sex = req.sex; avatar = req.avatar })
        publishUserProfileUpdatedIfChanged(oldUser, req.nickname, req.avatar)
    }

    override fun updateUserPassword(id: Long, req: UserProfileUpdatePasswordReqVO) {
        val user = validateExists(id)
        if (!isPasswordMatch(requireNotNull(req.oldPassword), requireNotNull(user.password))) throw exception(USER_PASSWORD_FAILED)
        updateUserPassword(id, requireNotNull(req.newPassword))
    }

    override fun updateUserPassword(id: Long, password: String) {
        validateExists(id); AdminUserDao.updateById(AdminUserDO().apply { this.id = id; this.password = passwordEncoder.encode(password) })
    }

    override fun updateUserStatus(id: Long, status: Int) {
        validateExists(id)
        AdminUserDao.updateById(AdminUserDO().apply { this.id = id; this.status = status })
        if (CommonStatusEnum.isDisable(status)) {
            oauth2TokenServiceProvider.ifAvailable?.removeAccessToken(id, UserTypeEnum.ADMIN.value)
        }
    }

    override fun deleteUser(id: Long) { validateExists(id); AdminUserDao.deleteById(id); UserPostDao.deleteByUserId(id); permissionService.processUserDeleted(id) }
    override fun deleteUserList(ids: List<Long>) { ids.forEach { AdminUserDao.deleteById(it); UserPostDao.deleteByUserId(it); permissionService.processUserDeleted(it) } }
    override fun getUserByUsername(username: String) = AdminUserDao.selectByUsername(username)
    override fun getUserByMobile(mobile: String) = AdminUserDao.selectByMobile(mobile)
    override fun getUserPage(req: UserPageReqVO): PageResult<AdminUserDO> {
        val userIds = req.roleId?.let { permissionService.getUserRoleIdListByRoleId(listOf(it)) }
        if (req.roleId != null && userIds.isNullOrEmpty()) return PageResult.empty()
        return AdminUserDao.selectPage(req, req.deptId?.let { deptService.getChildDeptList(it).mapNotNull { dept -> dept.id } + it }, userIds)
    }
    override fun getUser(id: Long) = AdminUserDao.selectById(id)
    override fun getUserListByDeptIds(deptIds: Collection<Long>) = AdminUserDao.selectListByDeptIds(deptIds)
    override fun getUserListByPostIds(postIds: Collection<Long>): List<AdminUserDO> {
        val ids = UserPostDao.selectListByPostIds(postIds).mapNotNull { it.userId }.toSet()
        return getUserList(ids)
    }
    override fun getUserList(ids: Collection<Long>) = AdminUserDao.selectByIds(ids)
    override fun getUserListAll() = AdminUserDao.selectPage(UserPageReqVO().apply { pageSize = -1 }).list
    override fun validateUserList(ids: Collection<Long>) {
        val users = getUserList(ids).associateBy { it.id }
        ids.forEach { id -> val user = users[id] ?: throw exception(USER_NOT_EXISTS); if (user.status != CommonStatusEnum.ENABLE.status) throw exception(USER_IS_DISABLE, user.nickname ?: "") }
    }
    override fun getUserListByNickname(nickname: String) = AdminUserDao.selectListByNickname(nickname)
    override fun getUserListByStatus(status: Int) = AdminUserDao.selectListByStatus(status)
    override fun getDeptUsers(deptIds: Collection<Long>) = getUserListByDeptIds(deptIds)

    @Transactional(rollbackFor = [Exception::class])
    override fun importUserList(
        importUsers: List<UserImportExcelVO>,
        isUpdateSupport: Boolean,
    ): UserImportRespVO {
        if (importUsers.isEmpty()) throw exception(USER_IMPORT_LIST_IS_EMPTY)
        val initPassword = configApi.getConfigValueByKey(USER_INIT_PASSWORD_KEY)
            ?.takeIf(String::isNotBlank)
            ?: throw exception(USER_IMPORT_INIT_PASSWORD)
        val result = UserImportRespVO()
        importUsers.forEachIndexed { index, imported ->
            val username = imported.username.orEmpty()
            try {
                ValidationUtils.validate(imported.toSaveReq(initPassword))
                val existing = username.takeIf(String::isNotBlank)?.let(AdminUserDao::selectByUsername)
                validateImportedUser(existing?.id, imported)
                if (existing == null) {
                    AdminUserDao.insert(imported.toEntity().apply {
                        password = passwordEncoder.encode(initPassword)
                        postIds = emptySet()
                    })
                    result.createUsernames += username
                } else if (!isUpdateSupport) {
                    result.failureUsernames[username] = USER_USERNAME_EXISTS.msg
                } else {
                    AdminUserDao.updateById(imported.toEntity().apply { id = existing.id })
                    result.updateUsernames += username
                }
            } catch (ex: ConstraintViolationException) {
                result.failureUsernames[username.ifBlank { "Row ${index + 1}" }] = ex.message.orEmpty()
            } catch (ex: ServiceException) {
                result.failureUsernames[username.ifBlank { "Row ${index + 1}" }] = ex.message.orEmpty()
            }
        }
        return result
    }

    override fun isPasswordMatch(rawPassword: String, encodedPassword: String) = passwordEncoder.matches(rawPassword, encodedPassword)

    private fun validateUserForCreateOrUpdate(id: Long?, req: UserSaveReqVO) {
        if (id != null) validateExists(id)
        val username = req.username?.takeIf { it.isNotBlank() }
        if (username != null) AdminUserDao.selectByUsername(username)?.let { if (it.id != id) throw exception(USER_USERNAME_EXISTS) }
        validateMobileUnique(id, req.mobile); validateEmailUnique(id, req.email)
        req.deptId?.let { deptService.validateDeptList(listOf(it)) }
        postService.validatePostList(req.postIds.orEmpty())
    }
    private fun validateExists(id: Long): AdminUserDO = AdminUserDao.selectById(id) ?: throw exception(USER_NOT_EXISTS)
    private fun validateEmailUnique(id: Long?, email: String?) { if (email.isNullOrBlank()) return; AdminUserDao.selectByEmail(email)?.let { if (it.id != id) throw exception(USER_EMAIL_EXISTS) } }
    private fun validateMobileUnique(id: Long?, mobile: String?) { if (mobile.isNullOrBlank()) return; AdminUserDao.selectByMobile(mobile)?.let { if (it.id != id) throw exception(USER_MOBILE_EXISTS) } }
    private fun syncPosts(userId: Long, postIds: Set<Long>?) {
        val desired = postIds.orEmpty(); val current = UserPostDao.selectListByUserId(userId).mapNotNull { it.postId }.toSet()
        val added = desired - current; val removed = current - desired
        added.forEach { UserPostDao.insert(UserPostDO().apply { this.userId = userId; postId = it }) }
        if (removed.isNotEmpty()) UserPostDao.deleteByUserIdAndPostId(userId, removed)
    }
    private fun validateImportedUser(id: Long?, imported: UserImportExcelVO) {
        validateMobileUnique(id, imported.mobile)
        validateEmailUnique(id, imported.email)
        imported.deptId?.let { deptService.validateDeptList(listOf(it)) }
    }
    private fun UserImportExcelVO.toSaveReq(password: String) = UserSaveReqVO().apply {
        username = this@toSaveReq.username
        nickname = this@toSaveReq.nickname
        deptId = this@toSaveReq.deptId
        email = this@toSaveReq.email
        mobile = this@toSaveReq.mobile
        sex = this@toSaveReq.sex
        this.password = password
    }
    private fun UserImportExcelVO.toEntity() = AdminUserDO().apply {
        username = this@toEntity.username
        nickname = this@toEntity.nickname?.takeIf(String::isNotBlank) ?: this@toEntity.username
        deptId = this@toEntity.deptId
        email = this@toEntity.email
        mobile = this@toEntity.mobile
        sex = this@toEntity.sex
        status = this@toEntity.status ?: CommonStatusEnum.ENABLE.status
    }
    private fun publishUserProfileUpdatedIfChanged(oldUser: AdminUserDO, nickname: String?, avatar: String?) {
        val changedNickname = nickname?.takeIf { it != oldUser.nickname }
        val changedAvatar = avatar?.takeIf { it != oldUser.avatar }
        if (changedNickname == null && changedAvatar == null) return
        adminUserProducer?.sendUserProfileUpdateMessage(requireNotNull(oldUser.id), changedNickname, changedAvatar)
    }
    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private fun UserSaveReqVO.toEntity() = AdminUserDO().apply { id = this@toEntity.id; username = this@toEntity.username; nickname = this@toEntity.nickname ?: this@toEntity.username; remark = this@toEntity.remark; deptId = this@toEntity.deptId; postIds = this@toEntity.postIds; email = this@toEntity.email; mobile = this@toEntity.mobile; sex = this@toEntity.sex; avatar = this@toEntity.avatar }

    companion object {
        private const val USER_REGISTER_ENABLED_KEY = "system.user.register-enabled"
        private const val USER_INIT_PASSWORD_KEY = "system.user.init-password"
    }
}
