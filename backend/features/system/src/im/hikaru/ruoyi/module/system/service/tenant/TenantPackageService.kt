package im.hikaru.ruoyi.module.system.service.tenant

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages.TenantPackagePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages.TenantPackageSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantPackageDO

interface TenantPackageService { fun createTenantPackage(req: TenantPackageSaveReqVO): Long; fun updateTenantPackage(req: TenantPackageSaveReqVO); fun deleteTenantPackage(id: Long); fun deleteTenantPackageList(ids: List<Long>); fun getTenantPackage(id: Long): TenantPackageDO?; fun getTenantPackagePage(req: TenantPackagePageReqVO): PageResult<TenantPackageDO>; fun validTenantPackage(id: Long): TenantPackageDO; fun getTenantPackageListByStatus(status: Int): List<TenantPackageDO> }
