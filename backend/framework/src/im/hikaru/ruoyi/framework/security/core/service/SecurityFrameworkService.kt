package im.hikaru.ruoyi.framework.security.core.service

/**
 * Security 框架 Service 接口，定义权限相关的校验操作 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
interface SecurityFrameworkService {

    /** 判断是否有权限 */
    fun hasPermission(permission: String): Boolean

    /** 判断是否有权限，任一一个即可 */
    fun hasAnyPermissions(vararg permissions: String): Boolean

    /**
     * 判断是否有角色
     *
     * 注意，角色使用的是 SysRoleDO 的 code 标识
     */
    fun hasRole(role: String): Boolean

    /** 判断是否有角色，任一一个即可 */
    fun hasAnyRoles(vararg roles: String): Boolean

    /** 判断是否有授权 */
    fun hasScope(scope: String): Boolean

    /** 判断是否有授权范围，任一一个即可 */
    fun hasAnyScopes(vararg scope: String): Boolean
}
