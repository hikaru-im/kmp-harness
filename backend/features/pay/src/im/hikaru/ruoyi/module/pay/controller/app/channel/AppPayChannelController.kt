package im.hikaru.ruoyi.module.pay.controller.app.channel

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Payment Channel")
@RestController
@RequestMapping("/pay/channel")
@Validated
class AppPayChannelController(
    private val channelService: PayChannelService,
) {
    @GetMapping("/get-enable-code-list")
    @Operation(summary = "Get enabled payment channel codes")
    @Parameter(name = "appId", description = "Application id", required = true, example = "1")
    fun getEnableChannelCodeList(@RequestParam("appId") appId: Long): CommonResult<Set<String>> =
        CommonResult.success(channelService.getEnableChannelList(appId).mapNotNull { it.code }.toSet())
}
