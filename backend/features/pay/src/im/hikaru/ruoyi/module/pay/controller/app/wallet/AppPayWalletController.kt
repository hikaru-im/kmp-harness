package im.hikaru.ruoyi.module.pay.controller.app.wallet

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.wallet.AppPayWalletRespVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletConvert
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Wallet")
@RestController
@RequestMapping("/pay/wallet")
@Validated
class AppPayWalletController(
    private val payWalletService: PayWalletService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get wallet")
    fun getPayWallet(): CommonResult<AppPayWalletRespVO> = CommonResult.success(
        PayWalletConvert.app(
            payWalletService.getOrCreateWallet(
                requireNotNull(WebFrameworkUtils.getLoginUserId()),
                UserTypeEnum.MEMBER.value,
            ),
        ),
    )
}
