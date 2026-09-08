package im.hikaru.ruoyi.module.member.controller.app.auth

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.security.config.SecurityProperties
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.*
import im.hikaru.ruoyi.module.member.service.auth.MemberAuthService
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxJsapiSignatureRespDTO
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "用户 APP - 认证")
@RestController
@RequestMapping("/member/auth")
@Validated
class AppAuthController(
    private val authService: MemberAuthService,
    private val socialClientApi: SocialClientApi,
    private val securityProperties: SecurityProperties,
) {
    @PostMapping("/login")
    @Operation(summary = "使用手机 + 密码登录")
    @PermitAll
    fun login(@RequestBody @Valid reqVO: AppAuthLoginReqVO): CommonResult<AppAuthLoginRespVO> = CommonResult.success(authService.login(reqVO))

    @PostMapping("/logout")
    @Operation(summary = "登出系统")
    @PermitAll
    fun logout(request: HttpServletRequest): CommonResult<Boolean> {
        SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.tokenHeader, securityProperties.tokenParameter)
            ?.takeIf(String::isNotBlank)
            ?.let(authService::logout)
        return CommonResult.success(true)
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "刷新令牌")
    @Parameter(name = "refreshToken", description = "刷新令牌", required = true)
    @PermitAll
    fun refreshToken(@RequestParam("refreshToken") refreshToken: String): CommonResult<AppAuthLoginRespVO> = CommonResult.success(authService.refreshToken(refreshToken))

    @PostMapping("/sms-login")
    @Operation(summary = "使用手机 + 验证码登录")
    @PermitAll
    fun smsLogin(@RequestBody @Valid reqVO: AppAuthSmsLoginReqVO): CommonResult<AppAuthLoginRespVO> = CommonResult.success(authService.smsLogin(reqVO))

    @PostMapping("/send-sms-code")
    @Operation(summary = "发送手机验证码")
    @PermitAll
    fun sendSmsCode(@RequestBody @Valid reqVO: AppAuthSmsSendReqVO): CommonResult<Boolean> { authService.sendSmsCode(WebFrameworkUtils.getLoginUserId() ?: 0L, reqVO); return CommonResult.success(true) }

    @PostMapping("/validate-sms-code")
    @Operation(summary = "校验手机验证码")
    @PermitAll
    fun validateSmsCode(@RequestBody @Valid reqVO: AppAuthSmsValidateReqVO): CommonResult<Boolean> { authService.validateSmsCode(WebFrameworkUtils.getLoginUserId() ?: 0L, reqVO); return CommonResult.success(true) }

    @GetMapping("/social-auth-redirect")
    @Operation(summary = "社交授权的跳转")
    @Parameters(Parameter(name = "type", description = "社交类型", required = true), Parameter(name = "redirectUri", description = "回调路径"))
    @PermitAll
    fun socialAuthRedirect(@RequestParam("type") type: Int, @RequestParam("redirectUri") redirectUri: String): CommonResult<String> = CommonResult.success(authService.getSocialAuthorizeUrl(type, redirectUri))

    @PostMapping("/social-login")
    @Operation(summary = "社交快捷登录，使用 code 授权码", description = "适合未登录的用户，但是社交账号已绑定用户")
    @PermitAll
    fun socialLogin(@RequestBody @Valid reqVO: AppAuthSocialLoginReqVO): CommonResult<AppAuthLoginRespVO> = CommonResult.success(authService.socialLogin(reqVO))

    @PostMapping("/weixin-mini-app-login")
    @Operation(summary = "微信小程序的一键登录")
    @PermitAll
    fun weixinMiniAppLogin(@RequestBody @Valid reqVO: AppAuthWeixinMiniAppLoginReqVO): CommonResult<AppAuthLoginRespVO> = CommonResult.success(authService.weixinMiniAppLogin(reqVO))

    @PostMapping("/create-weixin-jsapi-signature")
    @Operation(summary = "创建微信 JS SDK 初始化所需的签名", description = "参考 https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/JS-SDK.html 文档")
    @PermitAll
    fun createWeixinMpJsapiSignature(@RequestParam("url") url: String): CommonResult<SocialWxJsapiSignatureRespDTO> = CommonResult.success(checkNotNull(socialClientApi.createWxMpJsapiSignature(UserTypeEnum.MEMBER.value, url)))
}
