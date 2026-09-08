package im.hikaru.ruoyi.module.system.service.tenant.handler

import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO

fun interface TenantInfoHandler { fun handle(tenant: TenantDO) }
