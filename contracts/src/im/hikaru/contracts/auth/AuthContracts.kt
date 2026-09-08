package im.hikaru.contracts.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthLoginRequest(
    val username: String,
    val password: String,
    val captchaVerification: String? = null,
    val socialType: Int? = null,
    val socialCode: String? = null,
    val socialState: String? = null,
)

@Serializable
data class AuthLoginResponse(
    val userId: Long? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresTime: String? = null,
)

@Serializable
data class AuthPermissionInfo(
    val user: AuthUser? = null,
    val roles: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
    val menus: List<AuthMenu> = emptyList(),
)

@Serializable
data class AuthUser(
    val id: Long? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val deptId: Long? = null,
    val username: String? = null,
    val email: String? = null,
)

@Serializable
data class AuthMenu(
    val id: Long? = null,
    val parentId: Long? = null,
    val name: String? = null,
    val path: String? = null,
    val component: String? = null,
    val componentName: String? = null,
    val icon: String? = null,
    val visible: Boolean? = null,
    val keepAlive: Boolean? = null,
    val alwaysShow: Boolean? = null,
    val children: List<AuthMenu> = emptyList(),
)
