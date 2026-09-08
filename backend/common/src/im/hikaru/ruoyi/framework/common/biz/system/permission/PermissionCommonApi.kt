package im.hikaru.ruoyi.framework.common.biz.system.permission

import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO

/**
 * 权限 API 接口 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
interface PermissionCommonApi {

    /**
     * 判断是否有权限，任一一个即可
     *
     * @param userId 用户编号
     * @param permissions 权限
     * @return 是否
     */
    fun hasAnyPermissions(userId: Long, vararg permissions: String): Boolean

    /**
     * 判断是否有角色，任一一个即可
     *
     * @param userId 用户编号
     * @param roles 角色数组
     * @return 是否
     */
    fun hasAnyRoles(userId: Long, vararg roles: String): Boolean

    /**
     * 获得登陆用户的部门数据权限
     *
     * @param userId 用户编号
     * @return 部门数据权限
     */
    fun getDeptDataPermission(userId: Long): DeptDataPermissionRespDTO?
}
