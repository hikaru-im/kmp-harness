package im.hikaru.ruoyi.module.member.controller.admin.signin

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigRespVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.config.MemberSignInConfigUpdateReqVO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.convert.signin.MemberSignInConfigConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.SIGN_IN_CONFIG_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.signin.MemberSignInConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 签到规则")
@RestController
@RequestMapping("/member/sign-in/config")
@Validated
class MemberSignInConfigController(
    private val signInConfigService: MemberSignInConfigService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建签到规则")
    @PreAuthorize("@ss.hasPermission('point:sign-in-config:create')")
    fun createSignInConfig(@Valid @RequestBody createReqVO: MemberSignInConfigCreateReqVO): CommonResult<Long> = CommonResult.success(signInConfigService.createSignInConfig(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新签到规则")
    @PreAuthorize("@ss.hasPermission('point:sign-in-config:update')")
    fun updateSignInConfig(@Valid @RequestBody updateReqVO: MemberSignInConfigUpdateReqVO): CommonResult<Boolean> { signInConfigService.updateSignInConfig(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除签到规则")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('point:sign-in-config:delete')")
    fun deleteSignInConfig(@RequestParam("id") id: Long): CommonResult<Boolean> { signInConfigService.deleteSignInConfig(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得签到规则")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('point:sign-in-config:query')")
    fun getSignInConfig(@RequestParam("id") id: Long): CommonResult<MemberSignInConfigRespVO> = CommonResult.success(MemberSignInConfigConvert.convert(signInConfigService.getSignInConfig(id) ?: throw exception(SIGN_IN_CONFIG_NOT_EXISTS)))

    @GetMapping("/list")
    @Operation(summary = "获得签到规则列表")
    @PreAuthorize("@ss.hasPermission('point:sign-in-config:query')")
    fun getSignInConfigList(): CommonResult<List<MemberSignInConfigRespVO>> = CommonResult.success(MemberSignInConfigConvert.convertList(signInConfigService.getSignInConfigList()))
}
