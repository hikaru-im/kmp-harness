package im.hikaru.ruoyi.module.member.controller.app.signin

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.config.AppMemberSignInConfigRespVO
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.module.member.convert.signin.MemberSignInConfigConvert
import im.hikaru.ruoyi.module.member.service.signin.MemberSignInConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户 App - 签到规则")
@RestController
@RequestMapping("/member/sign-in/config")
@Validated
class AppMemberSignInConfigController(
    private val signInConfigService: MemberSignInConfigService,
) {
    @GetMapping("/list")
    @Operation(summary = "获得签到规则列表")
    @PermitAll
    fun getSignInConfigList(): CommonResult<List<AppMemberSignInConfigRespVO>> = CommonResult.success(MemberSignInConfigConvert.convertList02(signInConfigService.getSignInConfigList(CommonStatusEnum.ENABLE.status)))
}
