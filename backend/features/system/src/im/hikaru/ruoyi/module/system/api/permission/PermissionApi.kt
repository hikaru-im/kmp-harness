package im.hikaru.ruoyi.module.system.api.permission

import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi

interface PermissionApi : PermissionCommonApi {
    fun getUserRoleIdListByRoleIds(roleIds: Collection<Long>): Set<Long>
}
