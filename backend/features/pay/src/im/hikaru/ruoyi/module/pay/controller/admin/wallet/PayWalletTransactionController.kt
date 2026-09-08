package im.hikaru.ruoyi.module.pay.controller.admin.wallet

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionRespVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletTransactionConvert
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletTransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Wallet Transaction")
@RestController
@RequestMapping("/pay/wallet-transaction")
@Validated
class PayWalletTransactionController(
    private val transactionService: PayWalletTransactionService,
) {
    @GetMapping("/page")
    @Operation(summary = "Get wallet transaction page")
    @PreAuthorize("@ss.hasPermission('pay:wallet:query')")
    fun getWalletTransactionPage(
        @Valid pageReqVO: PayWalletTransactionPageReqVO,
    ): CommonResult<PageResult<PayWalletTransactionRespVO>> = CommonResult.success(
        PayWalletTransactionConvert.adminPage(transactionService.getWalletTransactionPage(pageReqVO)),
    )
}
