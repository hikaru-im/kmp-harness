package im.hikaru.ruoyi.module.system.controller.admin.oauth2

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.enums.logger.LoginLogTypeEnum
import im.hikaru.ruoyi.module.system.service.auth.AdminAuthService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/system/oauth2-token")
class OAuth2TokenController(
    private val oauth2TokenService: OAuth2TokenService,
    private val adminAuthService: AdminAuthService,
) {
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('system:oauth2-token:page')")
    fun page(@Valid req: OAuth2AccessTokenPageReqVO): CommonResult<PageResult<OAuth2AccessTokenRespVO>> {
        val result = oauth2TokenService.getAccessTokenPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('system:oauth2-token:delete')")
    fun delete(@RequestParam("accessToken") accessToken: String): CommonResult<Boolean> {
        adminAuthService.logout(accessToken, LoginLogTypeEnum.LOGOUT_DELETE.type)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @PreAuthorize("@ss.hasPermission('system:oauth2-token:delete')")
    fun deleteList(@RequestParam("accessTokens") accessTokens: List<String>): CommonResult<Boolean> {
        accessTokens.forEach { adminAuthService.logout(it, LoginLogTypeEnum.LOGOUT_DELETE.type) }
        return CommonResult.success(true)
    }

    private fun OAuth2AccessTokenDO.toResponse() = OAuth2AccessTokenRespVO().apply {
        id = this@toResponse.id
        accessToken = this@toResponse.accessToken
        refreshToken = this@toResponse.refreshToken
        userId = this@toResponse.userId
        userType = this@toResponse.userType
        clientId = this@toResponse.clientId
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
        expiresTime = this@toResponse.expiresTime?.toJavaLocalDateTime()
    }
}
