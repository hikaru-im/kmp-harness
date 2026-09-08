package im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class TenantPackageSaveReqVO { var id: Long? = null; @field:NotBlank var name: String? = null; @field:NotNull var status: Int? = null; var remark: String? = null; var menuIds: Set<Long> = emptySet() }
