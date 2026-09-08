package im.hikaru.ruoyi.module.pay.controller.admin.order

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderDetailsRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderExcelVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderPageItemRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO
import im.hikaru.ruoyi.module.pay.convert.order.PayOrderConvert
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.wallet.PayWalletService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Payment Order")
@RestController
@RequestMapping("/pay/order")
@Validated
class PayOrderController(
    private val orderService: PayOrderService,
    private val appService: PayAppService,
    private val payWalletService: PayWalletService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get payment order")
    @Parameters(
        Parameter(name = "id", description = "Order id", required = true, example = "1024"),
        Parameter(name = "sync", description = "Sync provider status", example = "true"),
    )
    @PreAuthorize("@ss.hasPermission('pay:order:query')")
    fun getOrder(
        @RequestParam("id") id: Long,
        @RequestParam(value = "sync", required = false) sync: Boolean?,
    ): CommonResult<PayOrderRespVO> {
        var order = orderService.getOrder(id)
        if (sync == true && PayOrderStatusEnum.isWaiting(order.status)) {
            orderService.syncOrderQuietly(id)
            order = orderService.getOrder(id)
        }
        return CommonResult.success(PayOrderConvert.convert(order))
    }

    @GetMapping("/get-detail")
    @Operation(summary = "Get payment order detail")
    @PreAuthorize("@ss.hasPermission('pay:order:query')")
    fun getOrderDetail(@RequestParam("id") id: Long): CommonResult<PayOrderDetailsRespVO> {
        val order = orderService.getOrder(id)
        val extension = order.extensionId?.let { orderService.getOrderExtension(it) }
        val app = order.appId?.let { appService.getApp(it) }
        return CommonResult.success(PayOrderConvert.convertDetail(order, extension, app))
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit payment order")
    fun submitPayOrder(@RequestBody reqVO: PayOrderSubmitReqVO): CommonResult<PayOrderSubmitRespVO> {
        appendWalletId(reqVO)
        return CommonResult.success(orderService.submitOrder(reqVO, ServletUtils.getClientIP() ?: "127.0.0.1"))
    }

    @GetMapping("/page")
    @Operation(summary = "Get payment order page")
    @PreAuthorize("@ss.hasPermission('pay:order:query')")
    fun getOrderPage(@Valid pageVO: PayOrderPageReqVO): CommonResult<PageResult<PayOrderPageItemRespVO>> {
        val page = orderService.getOrderPage(pageVO)
        val apps = appService.getAppMap(page.list.mapNotNull { it.appId }.toSet())
        return CommonResult.success(PayOrderConvert.convertPage(page, apps))
    }

    @GetMapping("/export-excel")
    @Operation(summary = "Export payment orders")
    @PreAuthorize("@ss.hasPermission('pay:order:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun exportOrderExcel(@Valid exportReqVO: PayOrderExportReqVO, response: HttpServletResponse) {
        val orders = orderService.getOrderList(exportReqVO)
        val apps = appService.getAppMap(orders.mapNotNull { it.appId }.toSet())
        ExcelUtils.write(response, "payment-orders.xls", "Data", PayOrderExcelVO::class.java, PayOrderConvert.convertExcel(orders, apps))
    }

    private fun appendWalletId(reqVO: PayOrderSubmitReqVO) {
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
