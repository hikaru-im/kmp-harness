package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.permission

import jakarta.validation.constraints.NotNull

class PermissionAssignRoleMenuReqVO { @field:NotNull var roleId: Long? = null; var menuIds: Set<Long> = emptySet() }
