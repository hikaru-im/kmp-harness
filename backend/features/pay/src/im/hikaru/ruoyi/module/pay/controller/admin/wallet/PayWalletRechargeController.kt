package im.hikaru.ruoyi.module.pay.controller.admin.wallet

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayOrderNotifyReqDTO
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayRefundNotifyReqDTO
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletRechargeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Wallet Recharge")
@RestController
@RequestMapping("/pay/wallet-recharge")
@Validated
class PayWalletRechargeController(
    private val walletRechargeService: PayWalletRechargeService,
) {
    @PostMapping("/update-paid")
    @Operation(summary = "Mark wallet recharge paid")
    @PermitAll
    fun updateWalletRechargerPaid(
        @Valid @RequestBody notifyReqDTO: PayOrderNotifyReqDTO,
    ): CommonResult<Boolean> {
        walletRechargeService.updateWalletRechargerPaid(
            requireNotNull(notifyReqDTO.merchantOrderId).toLong(),
            requireNotNull(notifyReqDTO.payOrderId),
        )
        return CommonResult.success(true)
    }

    @PostMapping("/refund")
    @Operation(summary = "Refund wallet recharge")
    fun refundWalletRecharge(@RequestParam("id") id: Long): CommonResult<Boolean> {
        walletRechargeService.refundWalletRecharge(id, ServletUtils.getClientIP() ?: "127.0.0.1")
        return CommonResult.success(true)
    }

    @PostMapping("/update-refunded")
    @Operation(summary = "Mark wallet recharge refunded")
    @PermitAll
    fun updateWalletRechargeRefunded(
        @RequestBody notifyReqDTO: PayRefundNotifyReqDTO,
    ): CommonResult<Boolean> {
        walletRechargeService.updateWalletRechargeRefunded(
            requireNotNull(notifyReqDTO.merchantOrderId).toLong(),
            requireNotNull(notifyReqDTO.merchantRefundId),
            requireNotNull(notifyReqDTO.payRefundId),
        )
        return CommonResult.success(true)
    }
}
