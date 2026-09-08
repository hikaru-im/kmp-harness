package im.hikaru.ruoyi.module.system.controller.admin.socail

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserBindReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserRespVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserUnbindReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserDO
import im.hikaru.ruoyi.module.system.service.social.SocialUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - Social user")
@RestController
@RequestMapping("/system/social-user")
@Validated
class SocialUserController(
    private val socialUserService: SocialUserService,
) {
    @PostMapping("/bind")
    @Operation(summary = "Bind social user")
    fun bind(@Valid @RequestBody req: SocialUserBindReqVO): CommonResult<Boolean> {
        socialUserService.bindSocialUser(SocialUserBindReqDTO().apply {
            userId = requireLoginId()
            userType = UserTypeEnum.ADMIN.value
            socialType = req.type
            code = req.code
            state = req.state
        })
        return CommonResult.success(true)
    }

    @DeleteMapping("/unbind")
    @Operation(summary = "Unbind social user")
    fun unbind(@Valid @RequestBody req: SocialUserUnbindReqVO): CommonResult<Boolean> {
        socialUserService.unbindSocialUser(
            requireLoginId(),
            UserTypeEnum.ADMIN.value,
            requireNotNull(req.type),
            requireNotNull(req.openid),
        )
        return CommonResult.success(true)
    }

    @GetMapping("/get-bind-list")
    @Operation(summary = "List bound social users")
    fun getBindList(): CommonResult<List<SocialUserRespVO>> {
        val list = socialUserService.getSocialUserList(requireLoginId(), UserTypeEnum.ADMIN.value)
        return CommonResult.success(list.map { it.toBriefResponse() })
    }

    @GetMapping("/get")
    @Operation(summary = "Get social user")
    @PreAuthorize("@ss.hasPermission('system:social-user:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<SocialUserRespVO?> =
        CommonResult.success(socialUserService.getSocialUser(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page social users")
    @PreAuthorize("@ss.hasPermission('system:social-user:query')")
    fun page(@Valid req: SocialUserPageReqVO): CommonResult<PageResult<SocialUserRespVO>> {
        val result = socialUserService.getSocialUserPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    private fun requireLoginId(): Long =
        requireNotNull(SecurityFrameworkUtils.getLoginUserId()) { "No authenticated user" }

    private fun SocialUserDO.toBriefResponse() = SocialUserRespVO().apply {
        id = this@toBriefResponse.id
        type = this@toBriefResponse.type
        openid = this@toBriefResponse.openid
        nickname = this@toBriefResponse.nickname
        avatar = this@toBriefResponse.avatar
    }

    private fun SocialUserDO.toResponse() = SocialUserRespVO().apply {
        id = this@toResponse.id
        type = this@toResponse.type
        openid = this@toResponse.openid
        token = this@toResponse.token
        rawTokenInfo = this@toResponse.rawTokenInfo
        nickname = this@toResponse.nickname
        avatar = this@toResponse.avatar
        rawUserInfo = this@toResponse.rawUserInfo
        code = this@toResponse.code
        state = this@toResponse.state
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
        updateTime = this@toResponse.updateTime?.toJavaLocalDateTime()
    }
}
