package im.hikaru.ruoyi.module.pay.controller.app.wallet

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeCreateRespVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletRechargeRespVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletRechargeConvert
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletRechargeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Wallet Recharge")
@RestController
@RequestMapping("/pay/wallet-recharge")
@Validated
class AppPayWalletRechargeController(
    private val walletRechargeService: PayWalletRechargeService,
    private val payOrderService: PayOrderService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create wallet recharge")
    fun createWalletRecharge(
        @Valid @RequestBody reqVO: AppPayWalletRechargeCreateReqVO,
    ): CommonResult<AppPayWalletRechargeCreateRespVO> {
        val recharge = walletRechargeService.createWalletRecharge(
            requireNotNull(WebFrameworkUtils.getLoginUserId()),
            requireNotNull(WebFrameworkUtils.getLoginUserType()),
            ServletUtils.getClientIP() ?: "127.0.0.1",
            reqVO,
        )
        return CommonResult.success(PayWalletRechargeConvert.createResponse(recharge))
    }

    @GetMapping("/page")
    @Operation(summary = "Get wallet recharge page")
    fun getWalletRechargePage(
        @Valid pageReqVO: PageParam,
    ): CommonResult<PageResult<AppPayWalletRechargeRespVO>> {
        val page = walletRechargeService.getWalletRechargePackagePage(
            requireNotNull(WebFrameworkUtils.getLoginUserId()),
            UserTypeEnum.MEMBER.value,
            pageReqVO,
            true,
        )
        val orders = payOrderService.getOrderList(page.list.mapNotNull { it.payOrderId }.toSet())
            .mapNotNull { order -> order.id?.let { it to order } }
            .toMap()
        return CommonResult.success(PayWalletRechargeConvert.page(page, orders))
    }
}
