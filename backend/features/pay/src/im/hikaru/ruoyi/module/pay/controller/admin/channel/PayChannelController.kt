package im.hikaru.ruoyi.module.pay.controller.admin.channel

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.PayChannelCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.PayChannelRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.channel.vo.PayChannelUpdateReqVO
import im.hikaru.ruoyi.module.pay.convert.channel.PayChannelConvert
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

@Tag(name = "Admin - Payment Channel")
@RestController
@RequestMapping("/pay/channel")
@Validated
class PayChannelController(
    private val channelService: PayChannelService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create payment channel")
    @PreAuthorize("@ss.hasPermission('pay:channel:create')")
    fun createChannel(@Valid @RequestBody createReqVO: PayChannelCreateReqVO): CommonResult<Long> =
        CommonResult.success(channelService.createChannel(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "Update payment channel")
    @PreAuthorize("@ss.hasPermission('pay:channel:update')")
    fun updateChannel(@Valid @RequestBody updateReqVO: PayChannelUpdateReqVO): CommonResult<Boolean> {
        channelService.updateChannel(updateReqVO)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete payment channel")
    @Parameter(name = "id", description = "Channel id", required = true)
    @PreAuthorize("@ss.hasPermission('pay:channel:delete')")
    fun deleteChannel(@RequestParam("id") id: Long): CommonResult<Boolean> {
        channelService.deleteChannel(id)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get payment channel")
    @PreAuthorize("@ss.hasPermission('pay:channel:query')")
    fun getChannel(
        @RequestParam(value = "id", required = false) id: Long?,
        @RequestParam(value = "appId", required = false) appId: Long?,
        @RequestParam(value = "code", required = false) code: String?,
    ): CommonResult<PayChannelRespVO> {
        val channel = when {
            id != null -> channelService.getChannel(id)
            appId != null && code != null -> requireNotNull(channelService.getChannelByAppIdAndCode(appId, code))
            else -> throw IllegalArgumentException("Either id or appId/code is required")
        }
        return CommonResult.success(PayChannelConvert.convert(channel))
    }

    @GetMapping("/list")
    @Operation(summary = "Get application payment channels")
    @PreAuthorize("@ss.hasPermission('pay:channel:query')")
    fun getChannelList(@RequestParam("appId") appId: Long): CommonResult<List<PayChannelRespVO>> =
        CommonResult.success(PayChannelConvert.convertList(channelService.getChannelListByAppId(appId)))

    @GetMapping("/get-enable-code-list")
    @Operation(summary = "Get enabled payment channel codes")
    fun getEnableChannelCodeList(@RequestParam("appId") appId: Long): CommonResult<Set<String>> =
        CommonResult.success(channelService.getEnableChannelList(appId).mapNotNull { it.code }.toSet())
}
