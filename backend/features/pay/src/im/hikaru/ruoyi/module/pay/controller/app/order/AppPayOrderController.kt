package im.hikaru.ruoyi.module.pay.controller.app.order

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderRespVO
import im.hikaru.ruoyi.module.pay.controller.app.order.vo.AppPayOrderSubmitReqVO
import im.hikaru.ruoyi.module.pay.controller.app.order.vo.AppPayOrderSubmitRespVO
import im.hikaru.ruoyi.module.pay.convert.order.PayOrderConvert
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User App - Payment Order")
@RestController
@RequestMapping("/pay/order")
@Validated
class AppPayOrderController(
    private val payOrderService: PayOrderService,
    private val payWalletService: PayWalletService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get payment order")
    @Parameters(
        Parameter(name = "id", description = "Order id", example = "1024"),
        Parameter(name = "no", description = "Payment order number", example = "Pxxx"),
        Parameter(name = "sync", description = "Sync provider status", example = "true"),
    )
    fun getOrder(
        @RequestParam(value = "id", required = false) id: Long?,
        @RequestParam(value = "no", required = false) no: String?,
        @RequestParam(value = "sync", required = false) sync: Boolean?,
    ): CommonResult<PayOrderRespVO> {
        var order = if (!no.isNullOrBlank()) payOrderService.getOrder(no) else payOrderService.getOrder(requireNotNull(id))
        val loginUserId = WebFrameworkUtils.getLoginUserId()
        require(order.userId == null || order.userId == loginUserId) { "Payment order does not belong to current user" }
        if (sync == true && PayOrderStatusEnum.isWaiting(order.status)) {
            payOrderService.syncOrderQuietly(requireNotNull(order.id))
            order = payOrderService.getOrder(requireNotNull(order.id))
        }
        return CommonResult.success(PayOrderConvert.convert(order))
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit payment order")
    fun submitPayOrder(@RequestBody reqVO: AppPayOrderSubmitReqVO): CommonResult<AppPayOrderSubmitRespVO> {
        appendWalletId(reqVO)
        val response = payOrderService.submitOrder(reqVO, ServletUtils.getClientIP() ?: "127.0.0.1")
        return CommonResult.success(AppPayOrderSubmitRespVO().apply {
            status = response.status
            displayMode = response.displayMode
            displayContent = response.displayContent
        })
    }

    private fun appendWalletId(reqVO: AppPayOrderSubmitReqVO) {
        if (reqVO.channelCode != PayChannelEnum.WALLET.code) return
        val wallet = payWalletService.getOrCreateWallet(
            requireNotNull(WebFrameworkUtils.getLoginUserId()),
            requireNotNull(WebFrameworkUtils.getLoginUserType()),
        )
        reqVO.channelExtras = reqVO.channelExtras.orEmpty().toMutableMap().apply {
            put(WALLET_ID_KEY, requireNotNull(wallet.id).toString())
        }
    }

    private companion object {
        const val WALLET_ID_KEY = "walletId"
    }
}
