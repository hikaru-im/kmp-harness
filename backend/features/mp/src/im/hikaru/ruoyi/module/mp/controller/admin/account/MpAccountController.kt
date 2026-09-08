package im.hikaru.ruoyi.module.mp.controller.admin.account

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.account.vo.*
import im.hikaru.ruoyi.module.mp.convert.account.MpAccountConvert
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号账号")
@RestController
@RequestMapping("/mp/account")
@Validated
class MpAccountController(
    private val mpAccountService: MpAccountService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建公众号账号")
    @PreAuthorize("@ss.hasPermission('mp:account:create')")
    fun createAccount(@Valid @RequestBody createReqVO: MpAccountCreateReqVO): CommonResult<Long> = CommonResult.success(mpAccountService.createAccount(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新公众号账号")
    @PreAuthorize("@ss.hasPermission('mp:account:update')")
    fun updateAccount(@Valid @RequestBody updateReqVO: MpAccountUpdateReqVO): CommonResult<Boolean> { mpAccountService.updateAccount(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公众号账号")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:account:delete')")
    fun deleteAccount(@RequestParam("id") id: Long): CommonResult<Boolean> { mpAccountService.deleteAccount(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得公众号账号")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mp:account:query')")
    fun getAccount(@RequestParam("id") id: Long): CommonResult<MpAccountRespVO> = CommonResult.success(MpAccountConvert.convert(mpAccountService.getRequiredAccount(id)))

    @GetMapping("/page")
    @Operation(summary = "获得公众号账号分页")
    @PreAuthorize("@ss.hasPermission('mp:account:query')")
    fun getAccountPage(@Valid pageVO: MpAccountPageReqVO): CommonResult<PageResult<MpAccountRespVO>> = CommonResult.success(MpAccountConvert.convertPage(mpAccountService.getAccountPage(pageVO)))

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取公众号账号精简信息列表")
    @PreAuthorize("@ss.hasPermission('mp:account:query')")
    fun getSimpleAccounts(): CommonResult<List<MpAccountSimpleRespVO>> = CommonResult.success(MpAccountConvert.convertSimpleList(mpAccountService.getAccountList()))

    @PutMapping("/generate-qr-code")
    @Operation(summary = "生成公众号二维码")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:account:qr-code')")
    fun generateAccountQrCode(@RequestParam("id") id: Long): CommonResult<Boolean> { mpAccountService.generateAccountQrCode(id); return CommonResult.success(true) }

    @PutMapping("/clear-quota")
    @Operation(summary = "清空公众号 API 配额")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:account:clear-quota')")
    fun clearAccountQuota(@RequestParam("id") id: Long): CommonResult<Boolean> { mpAccountService.clearAccountQuota(id); return CommonResult.success(true) }
}
