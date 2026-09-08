package im.hikaru.ruoyi.module.member.controller.app.social

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.controller.app.social.vo.*
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.api.social.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.util.Base64

@Tag(name = "用户 App - 社交用户")
@RestController
@RequestMapping("/member/social-user")
@Validated
class AppSocialUserController(
    private val socialUserApi: SocialUserApi,
    private val socialClientApi: SocialClientApi,
) {
    @PostMapping("/bind")
    @Operation(summary = "社交绑定，使用 code 授权码")
    fun socialBind(@RequestBody @Valid reqVO: AppSocialUserBindReqVO): CommonResult<String> = CommonResult.success(socialUserApi.bindSocialUser(SocialUserBindReqDTO().apply {
        userId = requireNotNull(WebFrameworkUtils.getLoginUserId()); userType = UserTypeEnum.MEMBER.value
        socialType = reqVO.type; code = reqVO.code; state = reqVO.state
    }))

    @DeleteMapping("/unbind")
    @Operation(summary = "取消社交绑定")
    fun socialUnbind(@RequestBody reqVO: AppSocialUserUnbindReqVO): CommonResult<Boolean> {
        socialUserApi.unbindSocialUser(SocialUserUnbindReqDTO().apply {
            userId = requireNotNull(WebFrameworkUtils.getLoginUserId()); userType = UserTypeEnum.MEMBER.value
            socialType = reqVO.type; openid = reqVO.openid
        })
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "获得社交用户")
    @Parameter(name = "type", description = "社交平台的类型，参见 SocialTypeEnum 枚举值", required = true, example = "10")
    fun getSocialUser(@RequestParam("type") type: Int): CommonResult<AppSocialUserRespVO> = CommonResult.success(
        BeanUtils.toBean(socialUserApi.getSocialUserByUserId(UserTypeEnum.MEMBER.value, requireNotNull(WebFrameworkUtils.getLoginUserId()), type), AppSocialUserRespVO::class.java) ?: AppSocialUserRespVO(),
    )

    @PostMapping("/wxa-qrcode")
    @Operation(summary = "获得微信小程序码(base64 image)")
    @PermitAll
    fun getWxaQrcode(@RequestBody @Valid reqVO: AppSocialWxaQrcodeReqVO): CommonResult<String> = CommonResult.success(Base64.getEncoder().encodeToString(socialClientApi.getWxaQrcode(requireNotNull(BeanUtils.toBean(reqVO, SocialWxQrcodeReqDTO::class.java)))))

    @GetMapping("/get-subscribe-template-list")
    @Operation(summary = "获得微信小程订阅模板列表")
    @PermitAll
    fun getSubscribeTemplateList(): CommonResult<List<AppSocialWxaSubscribeTemplateRespVO>> = CommonResult.success(BeanUtils.toBean(socialClientApi.getWxaSubscribeTemplateList(UserTypeEnum.MEMBER.value), AppSocialWxaSubscribeTemplateRespVO::class.java) ?: emptyList())
}
