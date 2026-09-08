package im.hikaru.ruoyi.module.pay.controller.admin.app

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppPageItemRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppUpdateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.PayAppUpdateStatusReqVO
import im.hikaru.ruoyi.module.pay.convert.app.PayAppConvert
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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

@Tag(name = "Admin - Payment Application")
@RestController
@RequestMapping("/pay/app")
@Validated
class PayAppController(
    private val appService: PayAppService,
    private val channelService: PayChannelService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create payment application")
    @PreAuthorize("@ss.hasPermission('pay:app:create')")
    fun createApp(@Valid @RequestBody createReqVO: PayAppCreateReqVO): CommonResult<Long> =
        CommonResult.success(appService.createApp(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "Update payment application")
    @PreAuthorize("@ss.hasPermission('pay:app:update')")
    fun updateApp(@Valid @RequestBody updateReqVO: PayAppUpdateReqVO): CommonResult<Boolean> {
        appService.updateApp(updateReqVO)
        return CommonResult.success(true)
    }

    @PutMapping("/update-status")
    @Operation(summary = "Update payment application status")
    @PreAuthorize("@ss.hasPermission('pay:app:update')")
    fun updateAppStatus(@Valid @RequestBody updateReqVO: PayAppUpdateStatusReqVO): CommonResult<Boolean> {
        appService.updateAppStatus(requireNotNull(updateReqVO.id), requireNotNull(updateReqVO.status))
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete payment application")
    @Parameter(name = "id", description = "Application id", required = true)
    @PreAuthorize("@ss.hasPermission('pay:app:delete')")
    fun deleteApp(@RequestParam("id") id: Long): CommonResult<Boolean> {
        appService.deleteApp(id)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get payment application")
    @Parameter(name = "id", description = "Application id", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pay:app:query')")
    fun getApp(@RequestParam("id") id: Long): CommonResult<PayAppRespVO> =
        CommonResult.success(PayAppConvert.convert(appService.getApp(id)))

    @GetMapping("/page")
    @Operation(summary = "Get payment application page")
    @PreAuthorize("@ss.hasPermission('pay:app:query')")
    fun getAppPage(@Valid pageVO: PayAppPageReqVO): CommonResult<PageResult<PayAppPageItemRespVO>> {
        val page = appService.getAppPage(pageVO)
        val channels = channelService.getChannelListByAppIds(page.list.mapNotNull { it.id }.toSet())
            .filter { it.status == CommonStatusEnum.ENABLE.status }
        return CommonResult.success(PayAppConvert.convertPage(page, channels))
    }

    @GetMapping("/list")
    @Operation(summary = "Get payment application list")
    @PreAuthorize("@ss.hasPermission('pay:merchant:query')")
    fun getAppList(): CommonResult<List<PayAppRespVO>> =
        CommonResult.success(PayAppConvert.convertList(appService.getAppList()))
}
