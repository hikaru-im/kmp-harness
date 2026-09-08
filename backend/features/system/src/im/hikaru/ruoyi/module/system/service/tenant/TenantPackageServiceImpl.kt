package im.hikaru.ruoyi.module.system.service.tenant

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages.TenantPackagePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages.TenantPackageSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantPackageDO
import im.hikaru.ruoyi.module.system.dal.mysql.tenant.TenantPackageDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_PACKAGE_DISABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_PACKAGE_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_PACKAGE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.TENANT_PACKAGE_USED
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class TenantPackageServiceImpl(private val tenantServiceProvider: ObjectProvider<TenantService>) : TenantPackageService {
    override fun createTenantPackage(req: TenantPackageSaveReqVO): Long { validateName(null, requireNotNull(req.name)); return TenantPackageDao.insert(req.toEntity()) }
    override fun updateTenantPackage(req: TenantPackageSaveReqVO) { val id = requireNotNull(req.id); val old = validateExists(id); validateName(id, requireNotNull(req.name)); TenantPackageDao.updateById(req.toEntity()); if (old.menuIds != req.menuIds) tenantServiceProvider.ifAvailable?.getTenantListByPackageId(id)?.forEach { tenantServiceProvider.ifAvailable?.updateTenantRoleMenu(requireNotNull(it.id), req.menuIds) } }
    override fun deleteTenantPackage(id: Long) { validateExists(id); if ((tenantServiceProvider.ifAvailable?.getTenantCountByPackageId(id) ?: 0) > 0) throw exception(TENANT_PACKAGE_USED); TenantPackageDao.deleteById(id) }
    override fun deleteTenantPackageList(ids: List<Long>) = ids.forEach(::deleteTenantPackage)
    override fun getTenantPackage(id: Long) = TenantPackageDao.selectById(id)
    override fun getTenantPackagePage(req: TenantPackagePageReqVO) = TenantPackageDao.selectPage(req)
    override fun validTenantPackage(id: Long): TenantPackageDO { val value = validateExists(id); if (value.status == CommonStatusEnum.DISABLE.status) throw exception(TENANT_PACKAGE_DISABLE, value.name ?: ""); return value }
    override fun getTenantPackageListByStatus(status: Int) = TenantPackageDao.selectListByStatus(status)
    private fun validateExists(id: Long) = TenantPackageDao.selectById(id) ?: throw exception(TENANT_PACKAGE_NOT_EXISTS)
    private fun validateName(id: Long?, name: String) { TenantPackageDao.selectByName(name)?.let { if (it.id != id) throw exception(TENANT_PACKAGE_NAME_DUPLICATE) } }
    private fun TenantPackageSaveReqVO.toEntity() = TenantPackageDO().apply { id = this@toEntity.id; name = this@toEntity.name; status = this@toEntity.status; remark = this@toEntity.remark; menuIds = this@toEntity.menuIds }
}
