package im.hikaru.ruoyi.module.system.service.tenant

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RoleSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO
import im.hikaru.ruoyi.module.system.dal.mysql.tenant.TenantDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_CAN_NOT_UPDATE_SYSTEM
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_DISABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_EXPIRE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_WEBSITE_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.permission.RoleCodeEnum
import im.hikaru.ruoyi.module.system.enums.permission.RoleTypeEnum
import im.hikaru.ruoyi.module.system.service.permission.MenuService
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import im.hikaru.ruoyi.module.system.service.permission.RoleService
import im.hikaru.ruoyi.module.system.service.tenant.handler.TenantInfoHandler
import im.hikaru.ruoyi.module.system.service.tenant.handler.TenantMenuHandler
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import kotlinx.datetime.toLocalDateTime
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import kotlin.time.Clock

@Service
class TenantServiceImpl(
    private val packageService: TenantPackageService,
    private val roleService: RoleService,
    private val menuService: MenuService,
    private val permissionService: PermissionService,
    private val userServiceProvider: ObjectProvider<AdminUserService>,
) : TenantService {
    override fun createTenant(req: TenantSaveReqVO): Long {
        validateName(null, requireNotNull(req.name)); validateWebsites(null, req.websites.orEmpty()); val pkg = packageService.validTenantPackage(requireNotNull(req.packageId))
        val entity = req.toEntity(); val id = TenantDao.insert(entity)
        if (!req.username.isNullOrBlank() && !req.password.isNullOrBlank()) TenantUtils.execute(id, Runnable {
            val roleId = roleService.createRole(RoleSaveReqVO().apply { name = RoleCodeEnum.TENANT_ADMIN.roleName; code = RoleCodeEnum.TENANT_ADMIN.code; sort = 0; status = 0; remark = "System generated" }, RoleTypeEnum.SYSTEM.type)
            permissionService.assignRoleMenu(roleId, pkg.menuIds.orEmpty())
            val userId = userServiceProvider.ifAvailable?.createUser(UserSaveReqVO().apply { username = req.username; nickname = req.contactName; mobile = req.contactMobile; password = req.password })
            if (userId != null) { permissionService.assignUserRole(userId, setOf(roleId)); TenantDao.updateById(TenantDO().apply { this.id = id; contactUserId = userId }) }
        })
        return id
    }
    override fun updateTenant(req: TenantSaveReqVO) { val id = requireNotNull(req.id); val old = validateMutable(id); validateName(id, requireNotNull(req.name)); validateWebsites(id, req.websites.orEmpty()); val pkg = packageService.validTenantPackage(requireNotNull(req.packageId)); TenantDao.updateById(req.toEntity()); if (old.packageId != req.packageId) updateTenantRoleMenu(id, pkg.menuIds.orEmpty()) }
    override fun updateTenantRoleMenu(tenantId: Long, menuIds: Set<Long>) { TenantUtils.execute(tenantId, Runnable { roleService.getRoleList().forEach { role -> val allowed = if (role.code == RoleCodeEnum.TENANT_ADMIN.code) menuIds else permissionService.getRoleMenuListByRoleId(listOf(requireNotNull(role.id))).intersect(menuIds); permissionService.assignRoleMenu(requireNotNull(role.id), allowed) } }) }
    override fun deleteTenant(id: Long) { validateMutable(id); TenantDao.deleteById(id) }
    override fun deleteTenantList(ids: List<Long>) = ids.forEach(::deleteTenant)
    override fun getTenant(id: Long) = TenantDao.selectById(id)
    override fun getTenantPage(req: TenantPageReqVO) = TenantDao.selectPage(req)
    override fun getTenantByName(name: String) = TenantDao.selectByName(name)
    override fun getTenantByWebsite(website: String) = TenantDao.selectListByWebsite(website).firstOrNull()
    override fun getTenantCountByPackageId(packageId: Long) = TenantDao.selectCountByPackageId(packageId)
    override fun getTenantListByPackageId(packageId: Long) = TenantDao.selectListByPackageId(packageId)
    override fun getTenantListByStatus(status: Int) = TenantDao.selectListByStatus(status)
    override fun handleTenantInfo(handler: TenantInfoHandler) { handler.handle(getTenant(TenantContextHolder.getRequiredTenantId()) ?: throw exception(TENANT_NOT_EXISTS)) }
    override fun handleTenantMenu(handler: TenantMenuHandler) { val tenant = getTenant(TenantContextHolder.getRequiredTenantId()) ?: throw exception(TENANT_NOT_EXISTS); handler.handle(if (tenant.packageId == TenantDO.PACKAGE_ID_SYSTEM) menuService.getMenuList().mapNotNull { it.id }.toSet() else packageService.getTenantPackage(requireNotNull(tenant.packageId))?.menuIds.orEmpty()) }
    override fun getTenantIdList() = TenantDao.selectList().mapNotNull { it.id }
    override fun validTenant(id: Long) { val tenant = getTenant(id) ?: throw exception(TENANT_NOT_EXISTS); if (tenant.status == CommonStatusEnum.DISABLE.status) throw exception(TENANT_DISABLE, tenant.name ?: ""); if (tenant.expireTime?.let { it < Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()) } == true) throw exception(TENANT_EXPIRE, tenant.name ?: "") }
    private fun validateMutable(id: Long): TenantDO { val tenant = getTenant(id) ?: throw exception(TENANT_NOT_EXISTS); if (tenant.packageId == TenantDO.PACKAGE_ID_SYSTEM) throw exception(TENANT_CAN_NOT_UPDATE_SYSTEM); return tenant }
    private fun validateName(id: Long?, name: String) { TenantDao.selectByName(name)?.let { if (it.id != id) throw exception(TENANT_NAME_DUPLICATE, name) } }
    private fun validateWebsites(id: Long?, websites: List<String>) { websites.filter { it.isNotBlank() }.forEach { website -> if (TenantDao.selectListByWebsite(website).any { it.id != id }) throw exception(TENANT_WEBSITE_DUPLICATE, website) } }
    private fun TenantSaveReqVO.toEntity() = TenantDO().apply { id = this@toEntity.id; name = this@toEntity.name; contactName = this@toEntity.contactName; contactMobile = this@toEntity.contactMobile; status = this@toEntity.status; websites = this@toEntity.websites; packageId = this@toEntity.packageId; expireTime = this@toEntity.expireTime; accountCount = this@toEntity.accountCount }
}
