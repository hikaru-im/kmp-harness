package im.hikaru.ruoyi.module.system.controller.admin.permission.vo.permission

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.permission.DataScopeEnum
import jakarta.validation.constraints.NotNull

class PermissionAssignRoleDataScopeReqVO { @field:NotNull var roleId: Long? = null; @field:NotNull @field:InEnum(DataScopeEnum::class) var dataScope: Int? = null; var dataScopeDeptIds: Set<Long> = emptySet() }
