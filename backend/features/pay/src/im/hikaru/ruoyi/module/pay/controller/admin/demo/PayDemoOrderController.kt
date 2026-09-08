package im.hikaru.ruoyi.module.pay.controller.admin.demo

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayOrderNotifyReqDTO
import im.hikaru.ruoyi.module.pay.api.notify.dto.PayRefundNotifyReqDTO
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.order.PayDemoOrderCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.demo.vo.order.PayDemoOrderRespVO
import im.hikaru.ruoyi.module.pay.convert.demo.PayDemoConvert
import im.hikaru.ruoyi.module.pay.service.demo.PayDemoOrderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Demo Order")
@RestController
@RequestMapping("/pay/demo-order")
@Validated
class PayDemoOrderController(
    private val demoOrderService: PayDemoOrderService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create demo order")
    fun createDemoOrder(@Valid @RequestBody createReqVO: PayDemoOrderCreateReqVO): CommonResult<Long> =
        CommonResult.success(demoOrderService.createDemoOrder(requireNotNull(WebFrameworkUtils.getLoginUserId()), createReqVO))

    @GetMapping("/page")
    @Operation(summary = "Get demo order page")
    fun getDemoOrderPage(@Valid pageVO: PageParam): CommonResult<PageResult<PayDemoOrderRespVO>> =
        CommonResult.success(PayDemoConvert.orderPage(demoOrderService.getDemoOrderPage(pageVO)))

    @PostMapping("/update-paid")
    @Operation(summary = "Mark demo order paid")
    @PermitAll
    fun updateDemoOrderPaid(@RequestBody notifyReqDTO: PayOrderNotifyReqDTO): CommonResult<Boolean> {
        demoOrderService.updateDemoOrderPaid(
            requireNotNull(notifyReqDTO.merchantOrderId).toLong(),
            requireNotNull(notifyReqDTO.payOrderId),
        )
        return CommonResult.success(true)
    }

    @PutMapping("/refund")
    @Operation(summary = "Refund demo order")
    fun refundDemoOrder(@RequestParam("id") id: Long): CommonResult<Boolean> {
        demoOrderService.refundDemoOrder(id, ServletUtils.getClientIP() ?: "127.0.0.1")
        return CommonResult.success(true)
    }

    @PostMapping("/update-refunded")
    @Operation(summary = "Mark demo order refunded")
    @PermitAll
    fun updateDemoOrderRefunded(@RequestBody notifyReqDTO: PayRefundNotifyReqDTO): CommonResult<Boolean> {
        demoOrderService.updateDemoOrderRefunded(
            requireNotNull(notifyReqDTO.merchantOrderId).toLong(),
            requireNotNull(notifyReqDTO.merchantRefundId),
            requireNotNull(notifyReqDTO.payRefundId),
        )
        return CommonResult.success(true)
    }
}
