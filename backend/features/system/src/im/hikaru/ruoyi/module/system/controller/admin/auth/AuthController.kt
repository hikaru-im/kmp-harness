package im.hikaru.ruoyi.module.system.controller.admin.auth

import im.hikaru.contracts.auth.AuthLoginResponse
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import im.hikaru.ruoyi.framework.security.config.SecurityProperties
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.convert.auth.AuthConvert
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthRegisterReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthResetPasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO
import im.hikaru.ruoyi.module.system.enums.logger.LoginLogTypeEnum
import im.hikaru.ruoyi.module.system.service.auth.AdminAuthService
import im.hikaru.ruoyi.module.system.service.permission.MenuService
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import im.hikaru.ruoyi.module.system.service.permission.RoleService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/system/auth")
@Validated
class AuthController(
    private val authService: AdminAuthService,
    private val userService: AdminUserService,
    private val roleService: RoleService,
    private val menuService: MenuService,
    private val permissionService: PermissionService,
    private val socialClientApi: SocialClientApi,
    private val securityProperties: SecurityProperties,
) {
    @PostMapping("/login")
    @PermitAll
    fun login(@Valid @RequestBody req: AuthLoginReqVO): CommonResult<AuthLoginResponse> =
        CommonResult.success(authService.login(req).toContract())

    @PostMapping("/logout")
    @PermitAll
    fun logout(request: HttpServletRequest): CommonResult<Boolean> {
        SecurityFrameworkUtils.obtainAuthorization(
            request,
            securityProperties.tokenHeader,
            securityProperties.tokenParameter,
        )?.takeIf { it.isNotBlank() }?.let { authService.logout(it, LoginLogTypeEnum.LOGOUT_SELF.type) }
        return CommonResult.success(true)
    }

    @PostMapping("/refresh-token")
    @PermitAll
    fun refreshToken(@RequestParam("refreshToken") refreshToken: String): CommonResult<AuthLoginResponse> =
        CommonResult.success(authService.refreshToken(refreshToken).toContract())

    @GetMapping("/get-permission-info")
    @DataPermission(enable = false)
    fun getPermissionInfo(): CommonResult<AuthPermissionInfoRespVO?> {
        val userId = SecurityFrameworkUtils.getLoginUserId() ?: return CommonResult.success(null)
        val user = userService.getUser(userId) ?: return CommonResult.success(null)
        val roleIds = permissionService.getUserRoleIdListByUserId(userId)
        if (roleIds.isEmpty()) return CommonResult.success(AuthConvert.convert(user, emptyList(), emptyList()))
        val roles = roleService.getRoleList(roleIds).filter { it.status == CommonStatusEnum.ENABLE.status }
        val menuIds = permissionService.getRoleMenuListByRoleId(roles.mapNotNull { it.id })
        val menus = menuService.filterDisableMenus(menuService.getMenuList(menuIds))
        return CommonResult.success(AuthConvert.convert(user, roles, menus))
    }

    @PostMapping("/register")
    @PermitAll
    fun register(@Valid @RequestBody req: AuthRegisterReqVO): CommonResult<AuthLoginResponse> =
        CommonResult.success(authService.register(req).toContract())

    @PostMapping("/sms-login")
    @PermitAll
    fun smsLogin(@Valid @RequestBody req: AuthSmsLoginReqVO): CommonResult<AuthLoginResponse> =
        CommonResult.success(authService.smsLogin(req).toContract())

    @PostMapping("/send-sms-code")
    @PermitAll
    fun sendSmsCode(@Valid @RequestBody req: AuthSmsSendReqVO): CommonResult<Boolean> {
        authService.sendSmsCode(req)
        return CommonResult.success(true)
    }

    @PostMapping("/reset-password")
    @PermitAll
    fun resetPassword(@Valid @RequestBody req: AuthResetPasswordReqVO): CommonResult<Boolean> {
        authService.resetPassword(req)
        return CommonResult.success(true)
    }

    @GetMapping("/social-auth-redirect")
    @PermitAll
    fun socialAuthRedirect(
        @RequestParam("type") type: Int,
        @RequestParam("redirectUri") redirectUri: String,
    ): CommonResult<String> = CommonResult.success(
        socialClientApi.getAuthorizeUrl(type, UserTypeEnum.ADMIN.value, redirectUri),
    )

    @PostMapping("/social-login")
    @PermitAll
    fun socialLogin(@Valid @RequestBody req: AuthSocialLoginReqVO): CommonResult<AuthLoginResponse> =
        CommonResult.success(authService.socialLogin(req).toContract())
}
