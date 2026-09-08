package im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages

import java.time.LocalDateTime

class TenantPackageRespVO { var id: Long? = null; var name: String? = null; var status: Int? = null; var remark: String? = null; var menuIds: Set<Long>? = null; var createTime: LocalDateTime? = null }
