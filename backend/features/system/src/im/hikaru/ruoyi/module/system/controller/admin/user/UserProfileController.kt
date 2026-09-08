package im.hikaru.ruoyi.module.system.controller.admin.user

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileRespVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin - User profile")
@RestController
@RequestMapping("/system/user/profile")
@Validated
class UserProfileController(private val userService: AdminUserService) {
    @GetMapping("/get")
    @Operation(summary = "Get current user profile")
    fun get(): CommonResult<UserProfileRespVO?> = CommonResult.success(userService.getUser(requireLoginId())?.toResp())
    @PutMapping("/update")
    fun update(@Valid @RequestBody req: UserProfileUpdateReqVO): CommonResult<Boolean> { userService.updateUserProfile(requireLoginId(), req); return CommonResult.success(true) }
    @PutMapping("/update-password")
    fun updatePassword(@Valid @RequestBody req: UserProfileUpdatePasswordReqVO): CommonResult<Boolean> { userService.updateUserPassword(requireLoginId(), req); return CommonResult.success(true) }
    private fun requireLoginId() = requireNotNull(SecurityFrameworkUtils.getLoginUserId()) { "No authenticated user" }
    private fun im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO.toResp() = UserProfileRespVO().apply {
        id = this@toResp.id; username = this@toResp.username; nickname = this@toResp.nickname; email = this@toResp.email; mobile = this@toResp.mobile; sex = this@toResp.sex; avatar = this@toResp.avatar; loginIp = this@toResp.loginIp
        loginDate = this@toResp.loginDate?.let { java.time.LocalDateTime.of(it.year, it.month.ordinal + 1, it.day, it.hour, it.minute, it.second, it.nanosecond) }
        createTime = this@toResp.createTime?.let { java.time.LocalDateTime.of(it.year, it.month.ordinal + 1, it.day, it.hour, it.minute, it.second, it.nanosecond) }
    }
}
