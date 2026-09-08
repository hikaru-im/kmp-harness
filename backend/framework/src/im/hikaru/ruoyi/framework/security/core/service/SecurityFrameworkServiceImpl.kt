package im.hikaru.ruoyi.framework.security.core.service

import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils.getLoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils.skipPermissionCheck

/**
 * 默认的 [SecurityFrameworkService] 实现类 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：Hutool CollUtil.containsAny → Kotlin intersect
 *
 * @author 芋道源码
 */
class SecurityFrameworkServiceImpl(
    private val permissionApi: PermissionCommonApi,
) : SecurityFrameworkService {

    override fun hasPermission(permission: String): Boolean = hasAnyPermissions(permission)

    override fun hasAnyPermissions(vararg permissions: String): Boolean {
        // 特殊：跨租户访问
        if (skipPermissionCheck()) return true
        // 权限校验
        val userId = getLoginUserId() ?: return false
        return permissionApi.hasAnyPermissions(userId, *permissions)
    }

    override fun hasRole(role: String): Boolean = hasAnyRoles(role)

    override fun hasAnyRoles(vararg roles: String): Boolean {
        // 特殊：跨租户访问
        if (skipPermissionCheck()) return true
        // 权限校验
        val userId = getLoginUserId() ?: return false
        return permissionApi.hasAnyRoles(userId, *roles)
    }

    override fun hasScope(scope: String): Boolean = hasAnyScopes(scope)

    override fun hasAnyScopes(vararg scope: String): Boolean {
        // 特殊：跨租户访问
        if (skipPermissionCheck()) return true
        // 权限校验
        val user: LoginUser = getLoginUser() ?: return false
        val userScopes = user.scopes ?: return false
        // 替换 Hutool CollUtil.containsAny
        return userScopes.any { it in scope }
    }
}
