package im.hikaru.ruoyi.module.system.controller.admin.socail

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientRespVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialClientDO
import im.hikaru.ruoyi.module.system.service.social.SocialClientService
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

@Tag(name = "Management - Social client")
@RestController
@RequestMapping("/system/social-client")
@Validated
class SocialClientController(
    private val socialClientService: SocialClientService,
    private val socialClientApi: SocialClientApi,
) {
    @PostMapping("/create")
    @Operation(summary = "Create social client")
    @PreAuthorize("@ss.hasPermission('system:social-client:create')")
    fun create(@Valid @RequestBody req: SocialClientSaveReqVO): CommonResult<Long> =
        CommonResult.success(socialClientService.createSocialClient(req))

    @PutMapping("/update")
    @Operation(summary = "Update social client")
    @PreAuthorize("@ss.hasPermission('system:social-client:update')")
    fun update(@Valid @RequestBody req: SocialClientSaveReqVO): CommonResult<Boolean> {
        socialClientService.updateSocialClient(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete social client")
    @PreAuthorize("@ss.hasPermission('system:social-client:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        socialClientService.deleteSocialClient(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete social clients")
    @PreAuthorize("@ss.hasPermission('system:social-client:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        socialClientService.deleteSocialClientList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get social client")
    @PreAuthorize("@ss.hasPermission('system:social-client:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<SocialClientRespVO?> =
        CommonResult.success(socialClientService.getSocialClient(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page social clients")
    @PreAuthorize("@ss.hasPermission('system:social-client:query')")
    fun page(@Valid req: SocialClientPageReqVO): CommonResult<PageResult<SocialClientRespVO>> {
        val result = socialClientService.getSocialClientPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @PostMapping("/send-subscribe-message")
    @Operation(summary = "Send subscription message")
    @PreAuthorize("@ss.hasPermission('system:social-client:query')")
    fun sendSubscribeMessage(@RequestBody req: SocialWxaSubscribeMessageSendReqDTO) {
        socialClientApi.sendWxaSubscribeMessage(req)
    }

    private fun SocialClientDO.toResponse() = SocialClientRespVO().apply {
        id = this@toResponse.id
        name = this@toResponse.name
        socialType = this@toResponse.socialType
        userType = this@toResponse.userType
        clientId = this@toResponse.clientId
        clientSecret = this@toResponse.clientSecret
        agentId = this@toResponse.agentId
        publicKey = this@toResponse.publicKey
        status = this@toResponse.status
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
