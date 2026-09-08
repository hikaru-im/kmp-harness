package im.hikaru.ruoyi.module.system.api.tenant

import im.hikaru.ruoyi.framework.common.biz.system.tenant.TenantCommonApi
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import org.springframework.stereotype.Service

@Service
class TenantApiImpl(private val service: TenantService) : TenantCommonApi { override fun getTenantIdList() = service.getTenantIdList(); override fun validateTenant(id: Long) = service.validTenant(id) }
