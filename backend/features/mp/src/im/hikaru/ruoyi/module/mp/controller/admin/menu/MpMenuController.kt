package im.hikaru.ruoyi.module.mp.controller.admin.menu

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuSaveReqVO
import im.hikaru.ruoyi.module.mp.convert.menu.MpMenuConvert
import im.hikaru.ruoyi.module.mp.service.menu.MpMenuService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号菜单")
@RestController
@RequestMapping("/mp/menu")
@Validated
class MpMenuController(
    private val mpMenuService: MpMenuService,
) {
    @PostMapping("/save")
    @Operation(summary = "保存公众号菜单")
    @PreAuthorize("@ss.hasPermission('mp:menu:save')")
    fun saveMenu(@Valid @RequestBody createReqVO: MpMenuSaveReqVO): CommonResult<Boolean> { mpMenuService.saveMenu(createReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公众号菜单")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('mp:menu:delete')")
    fun deleteMenu(@RequestParam("accountId") accountId: Long): CommonResult<Boolean> { mpMenuService.deleteMenuByAccountId(accountId); return CommonResult.success(true) }

    @GetMapping("/list")
    @Operation(summary = "获得公众号菜单列表")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "10")
    @PreAuthorize("@ss.hasPermission('mp:menu:query')")
    fun getMenuList(@RequestParam("accountId") accountId: Long): CommonResult<List<MpMenuRespVO>> = CommonResult.success(MpMenuConvert.convertList(mpMenuService.getMenuListByAccountId(accountId)))
}
