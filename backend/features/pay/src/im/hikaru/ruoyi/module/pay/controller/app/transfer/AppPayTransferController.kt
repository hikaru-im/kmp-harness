package im.hikaru.ruoyi.module.pay.controller.app.transfer

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Payment Transfer")
@RestController
@RequestMapping("/pay/transfer")
@Validated
class AppPayTransferController(
    private val transferService: PayTransferService,
) {
    @GetMapping("/sync")
    @Operation(summary = "Sync transfer status")
    @Parameter(name = "id", description = "Transfer id", required = true, example = "1024")
    fun syncTransfer(@RequestParam("id") id: Long): CommonResult<Boolean> {
        transferService.syncTransfer(id)
        return CommonResult.success(true)
    }
}
