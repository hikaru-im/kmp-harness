package im.hikaru.ruoyi.module.pay.controller.app.wallet

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletPackageRespVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletRechargePackageConvert
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletRechargePackageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Wallet Recharge Package")
@RestController
@RequestMapping("/pay/wallet-recharge-package")
@Validated
class AppPayWalletRechargePackageController(
    private val rechargePackageService: PayWalletRechargePackageService,
) {
    @GetMapping("/list")
    @Operation(summary = "Get wallet recharge packages")
    fun getWalletRechargePackageList(): CommonResult<List<AppPayWalletPackageRespVO>> {
        val packages = rechargePackageService.getWalletRechargePackageList(CommonStatusEnum.ENABLE.status)
            .sortedBy { it.payPrice }
        return CommonResult.success(PayWalletRechargePackageConvert.app(packages))
    }
}
