package im.hikaru.ruoyi.module.member.controller.app.user

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.controller.app.user.vo.*
import im.hikaru.ruoyi.module.member.convert.user.MemberUserConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "用户 APP - 用户个人中心")
@RestController
@RequestMapping("/member/user")
@Validated
class AppMemberUserController(
    private val userService: MemberUserService,
    private val levelService: MemberLevelService,
) {
    @GetMapping("/get")
    @Operation(summary = "获得基本信息")
    fun getUserInfo(): CommonResult<AppMemberUserInfoRespVO> {
        val user = userService.getUser(userId()) ?: throw exception(USER_NOT_EXISTS)
        return CommonResult.success(MemberUserConvert.convert(user, user.levelId?.let(levelService::getLevel)))
    }

    @PutMapping("/update")
    @Operation(summary = "修改基本信息")
    fun updateUser(@RequestBody @Valid reqVO: AppMemberUserUpdateReqVO): CommonResult<Boolean> { userService.updateUser(userId(), reqVO); return CommonResult.success(true) }

    @PutMapping("/update-mobile")
    @Operation(summary = "修改用户手机")
    fun updateUserMobile(@RequestBody @Valid reqVO: AppMemberUserUpdateMobileReqVO): CommonResult<Boolean> { userService.updateUserMobile(userId(), reqVO); return CommonResult.success(true) }

    @PutMapping("/update-mobile-by-weixin")
    @Operation(summary = "基于微信小程序的授权码，修改用户手机")
    fun updateUserMobileByWeixin(@RequestBody @Valid reqVO: AppMemberUserUpdateMobileByWeixinReqVO): CommonResult<Boolean> { userService.updateUserMobileByWeixin(userId(), reqVO); return CommonResult.success(true) }

    @PutMapping("/update-password")
    @Operation(summary = "修改用户密码", description = "用户修改密码时使用")
    fun updateUserPassword(@RequestBody @Valid reqVO: AppMemberUserUpdatePasswordReqVO): CommonResult<Boolean> { userService.updateUserPassword(userId(), reqVO); return CommonResult.success(true) }

    @PutMapping("/reset-password")
    @Operation(summary = "重置密码", description = "用户忘记密码时使用")
    @PermitAll
    fun resetUserPassword(@RequestBody @Valid reqVO: AppMemberUserResetPasswordReqVO): CommonResult<Boolean> { userService.resetUserPassword(reqVO); return CommonResult.success(true) }

    private fun userId(): Long = requireNotNull(WebFrameworkUtils.getLoginUserId())
}
