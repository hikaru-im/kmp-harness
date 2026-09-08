package im.hikaru.ruoyi.module.pay.controller.admin.demo

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayTransferNotifyReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw.PayDemoWithdrawCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.withdraw.PayDemoWithdrawRespVO
import im.hikaru.ruoyi.module.pay.convert.demo.PayDemoConvert
import im.hikaru.ruoyi.module.pay.service.demo.PayDemoWithdrawService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Demo Withdraw")
@RestController
@RequestMapping("/pay/demo-withdraw")
@Validated
class PayDemoWithdrawController(
    private val demoWithdrawService: PayDemoWithdrawService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create demo withdraw")
    fun createDemoWithdraw(@Valid @RequestBody createReqVO: PayDemoWithdrawCreateReqVO): CommonResult<Long> =
        CommonResult.success(demoWithdrawService.createDemoWithdraw(createReqVO))

    @PostMapping("/transfer")
    @Operation(summary = "Transfer demo withdraw")
    fun transferDemoWithdraw(@RequestParam("id") id: Long): CommonResult<Long> = CommonResult.success(
        demoWithdrawService.transferDemoWithdraw(id, requireNotNull(WebFrameworkUtils.getLoginUserId())),
    )

    @GetMapping("/page")
    @Operation(summary = "Get demo withdraw page")
    fun getDemoWithdrawPage(@Valid pageVO: PageParam): CommonResult<PageResult<PayDemoWithdrawRespVO>> =
        CommonResult.success(PayDemoConvert.withdrawPage(demoWithdrawService.getDemoWithdrawPage(pageVO)))

    @PostMapping("/update-transferred")
    @Operation(summary = "Update demo withdraw transfer status")
    @PermitAll
    fun updateDemoWithdrawTransferred(@RequestBody notifyReqDTO: PayTransferNotifyReqDTO): CommonResult<Boolean> {
        demoWithdrawService.updateDemoWithdrawTransferred(
            requireNotNull(notifyReqDTO.merchantTransferId).toLong(),
            requireNotNull(notifyReqDTO.payTransferId),
        )
        return CommonResult.success(true)
    }
}
