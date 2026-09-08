package im.hikaru.ruoyi.module.system.service.tenant

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO
import im.hikaru.ruoyi.module.system.service.tenant.handler.TenantInfoHandler
import im.hikaru.ruoyi.module.system.service.tenant.handler.TenantMenuHandler

interface TenantService { fun createTenant(req: TenantSaveReqVO): Long; fun updateTenant(req: TenantSaveReqVO); fun updateTenantRoleMenu(tenantId: Long, menuIds: Set<Long>); fun deleteTenant(id: Long); fun deleteTenantList(ids: List<Long>); fun getTenant(id: Long): TenantDO?; fun getTenantPage(req: TenantPageReqVO): PageResult<TenantDO>; fun getTenantByName(name: String): TenantDO?; fun getTenantByWebsite(website: String): TenantDO?; fun getTenantCountByPackageId(packageId: Long): Long; fun getTenantListByPackageId(packageId: Long): List<TenantDO>; fun getTenantListByStatus(status: Int): List<TenantDO>; fun handleTenantInfo(handler: TenantInfoHandler); fun handleTenantMenu(handler: TenantMenuHandler); fun getTenantIdList(): List<Long>; fun validTenant(id: Long) }
