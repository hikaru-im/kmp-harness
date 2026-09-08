package im.hikaru.ruoyi.module.mp.controller.admin.user

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.user.vo.MpUserUpdateReqVO
import im.hikaru.ruoyi.module.mp.convert.user.MpUserConvert
import im.hikaru.ruoyi.module.mp.service.user.MpUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号粉丝")
@RestController
@RequestMapping("/mp/user")
@Validated
class MpUserController(
    private val mpUserService: MpUserService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得公众号粉丝分页")
    @PreAuthorize("@ss.hasPermission('mp:user:query')")
    fun getUserPage(@Valid pageVO: MpUserPageReqVO): CommonResult<PageResult<MpUserRespVO>> = CommonResult.success(MpUserConvert.convertPage(mpUserService.getUserPage(pageVO)))

    @GetMapping("/get")
    @Operation(summary = "获得公众号粉丝")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mp:user:query')")
    fun getUser(@RequestParam("id") id: Long): CommonResult<MpUserRespVO> = CommonResult.success(MpUserConvert.convert(mpUserService.getRequiredUser(id)))

    @PutMapping("/update")
    @Operation(summary = "更新公众号粉丝")
    @PreAuthorize("@ss.hasPermission('mp:user:update')")
    fun updateUser(@Valid @RequestBody updateReqVO: MpUserUpdateReqVO): CommonResult<Boolean> { mpUserService.updateUser(updateReqVO); return CommonResult.success(true) }

    @PostMapping("/sync")
    @Operation(summary = "同步公众号粉丝")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:user:sync')")
    fun syncUser(@RequestParam("accountId") accountId: Long): CommonResult<Boolean> { mpUserService.syncUser(accountId); return CommonResult.success(true) }
}
