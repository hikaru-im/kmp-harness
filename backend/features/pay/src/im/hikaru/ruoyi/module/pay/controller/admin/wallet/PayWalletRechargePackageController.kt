package im.hikaru.ruoyi.module.pay.controller.admin.wallet

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackagePageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageUpdateReqVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletRechargePackageConvert
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletRechargePackageService
import io.swagger.v3.oas.annotations.Operation
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

@Tag(name = "Admin - Wallet Recharge Package")
@RestController
@RequestMapping("/pay/wallet-recharge-package")
@Validated
class PayWalletRechargePackageController(
    private val rechargePackageService: PayWalletRechargePackageService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create wallet recharge package")
    @PreAuthorize("@ss.hasPermission('pay:wallet-recharge-package:create')")
    fun createWalletRechargePackage(
        @Valid @RequestBody createReqVO: WalletRechargePackageCreateReqVO,
    ): CommonResult<Long> = CommonResult.success(rechargePackageService.createWalletRechargePackage(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "Update wallet recharge package")
    @PreAuthorize("@ss.hasPermission('pay:wallet-recharge-package:update')")
    fun updateWalletRechargePackage(
        @Valid @RequestBody updateReqVO: WalletRechargePackageUpdateReqVO,
    ): CommonResult<Boolean> {
        rechargePackageService.updateWalletRechargePackage(updateReqVO)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete wallet recharge package")
    @PreAuthorize("@ss.hasPermission('pay:wallet-recharge-package:delete')")
    fun deleteWalletRechargePackage(@RequestParam("id") id: Long): CommonResult<Boolean> {
        rechargePackageService.deleteWalletRechargePackage(id)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get wallet recharge package")
    @PreAuthorize("@ss.hasPermission('pay:wallet-recharge-package:query')")
    fun getWalletRechargePackage(@RequestParam("id") id: Long): CommonResult<WalletRechargePackageRespVO> =
        CommonResult.success(PayWalletRechargePackageConvert.admin(rechargePackageService.getWalletRechargePackage(id)))

    @GetMapping("/page")
    @Operation(summary = "Get wallet recharge package page")
    @PreAuthorize("@ss.hasPermission('pay:wallet-recharge-package:query')")
    fun getWalletRechargePackagePage(
        @Valid pageVO: WalletRechargePackagePageReqVO,
    ): CommonResult<PageResult<WalletRechargePackageRespVO>> = CommonResult.success(
        PayWalletRechargePackageConvert.page(rechargePackageService.getWalletRechargePackagePage(pageVO)),
    )
}
