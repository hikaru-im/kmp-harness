package im.hikaru.ruoyi.module.pay.controller.admin.wallet

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletUpdateBalanceReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletUserReqVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletConvert
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - User Wallet")
@RestController
@RequestMapping("/pay/wallet")
@Validated
class PayWalletController(
    private val payWalletService: PayWalletService,
) {
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('pay:wallet:query')")
    @Operation(summary = "Get user wallet")
    fun getWallet(@Valid reqVO: PayWalletUserReqVO): CommonResult<PayWalletRespVO> = CommonResult.success(
        PayWalletConvert.admin(
            payWalletService.getOrCreateWallet(requireNotNull(reqVO.userId), UserTypeEnum.MEMBER.value),
        ),
    )

    @GetMapping("/page")
    @Operation(summary = "Get wallet page")
    @PreAuthorize("@ss.hasPermission('pay:wallet:query')")
    fun getWalletPage(@Valid pageVO: PayWalletPageReqVO): CommonResult<PageResult<PayWalletRespVO>> =
        CommonResult.success(PayWalletConvert.page(payWalletService.getWalletPage(pageVO)))

    @PutMapping("/update-balance")
    @Operation(summary = "Update user wallet balance")
    @PreAuthorize("@ss.hasPermission('pay:wallet:update-balance')")
    fun updateWalletBalance(
        @Valid @RequestBody updateReqVO: PayWalletUpdateBalanceReqVO,
    ): CommonResult<Boolean> {
        val wallet = payWalletService.getOrCreateWallet(
            requireNotNull(updateReqVO.userId),
            UserTypeEnum.MEMBER.value,
        )
        val delta = requireNotNull(updateReqVO.balance)
        require(delta != 0) { "Balance delta must not be zero" }
        if (delta > 0) {
            payWalletService.addWalletBalance(
                requireNotNull(wallet.id),
                "admin-${updateReqVO.userId}-${System.nanoTime()}",
                PayWalletBizTypeEnum.UPDATE_BALANCE,
                delta,
            )
        } else {
            payWalletService.reduceWalletBalance(
                requireNotNull(wallet.id),
                System.nanoTime(),
                PayWalletBizTypeEnum.UPDATE_BALANCE,
                -delta,
            )
        }
        return CommonResult.success(true)
    }
}
