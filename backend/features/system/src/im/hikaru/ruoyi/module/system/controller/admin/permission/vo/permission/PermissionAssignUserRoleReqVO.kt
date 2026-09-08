package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.permission

import jakarta.validation.constraints.NotNull

class PermissionAssignUserRoleReqVO { @field:NotNull var userId: Long? = null; var roleIds: Set<Long> = emptySet() }
