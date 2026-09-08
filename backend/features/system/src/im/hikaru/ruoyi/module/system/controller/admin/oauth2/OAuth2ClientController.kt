package im.hikaru.ruoyi.module.system.controller.admin.oauth2

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientRespVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - OAuth2 client")
@RestController
@RequestMapping("/system/oauth2-client")
@Validated
class OAuth2ClientController(private val oauth2ClientService: OAuth2ClientService) {
    @PostMapping("/create")
    @Operation(summary = "Create OAuth2 client")
    @PreAuthorize("@ss.hasPermission('system:oauth2-client:create')")
    fun create(@Valid @RequestBody req: OAuth2ClientSaveReqVO): CommonResult<Long> =
        CommonResult.success(oauth2ClientService.createOAuth2Client(req))

    @PutMapping("/update")
    @Operation(summary = "Update OAuth2 client")
    @PreAuthorize("@ss.hasPermission('system:oauth2-client:update')")
    fun update(@Valid @RequestBody req: OAuth2ClientSaveReqVO): CommonResult<Boolean> {
        oauth2ClientService.updateOAuth2Client(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete OAuth2 client")
    @PreAuthorize("@ss.hasPermission('system:oauth2-client:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        oauth2ClientService.deleteOAuth2Client(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete OAuth2 clients")
    @PreAuthorize("@ss.hasPermission('system:oauth2-client:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        oauth2ClientService.deleteOAuth2ClientList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get OAuth2 client")
    @PreAuthorize("@ss.hasPermission('system:oauth2-client:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<OAuth2ClientRespVO?> =
        CommonResult.success(oauth2ClientService.getOAuth2Client(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page OAuth2 clients")
    @PreAuthorize("@ss.hasPermission('system:oauth2-client:query')")
    fun page(@Valid req: OAuth2ClientPageReqVO): CommonResult<PageResult<OAuth2ClientRespVO>> {
        val result = oauth2ClientService.getOAuth2ClientPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    private fun OAuth2ClientDO.toResponse() = OAuth2ClientRespVO().apply {
        id = this@toResponse.id
        clientId = this@toResponse.clientId
        secret = this@toResponse.secret
        name = this@toResponse.name
        logo = this@toResponse.logo
        description = this@toResponse.description
        status = this@toResponse.status
        accessTokenValiditySeconds = this@toResponse.accessTokenValiditySeconds
        refreshTokenValiditySeconds = this@toResponse.refreshTokenValiditySeconds
        redirectUris = this@toResponse.redirectUris
        authorizedGrantTypes = this@toResponse.authorizedGrantTypes
        scopes = this@toResponse.scopes
        autoApproveScopes = this@toResponse.autoApproveScopes
        authorities = this@toResponse.authorities
        resourceIds = this@toResponse.resourceIds
        additionalInformation = this@toResponse.additionalInformation
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
