package im.hikaru.ruoyi.module.pay.controller.admin.notify

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskDetailRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.notify.vo.PayNotifyTaskRespVO
import im.hikaru.ruoyi.module.pay.convert.notify.PayNotifyConvert
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.channel.PayChannelService
import im.hikaru.ruoyi.module.pay.service.notify.PayNotifyService
import im.hikaru.ruoyi.module.pay.service.order.PayOrderService
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Payment Notify")
@RestController
@RequestMapping("/pay/notify")
@Validated
class PayNotifyController(
    private val orderService: PayOrderService,
    private val refundService: PayRefundService,
    private val transferService: PayTransferService,
    private val notifyService: PayNotifyService,
    private val appService: PayAppService,
    private val channelService: PayChannelService,
) {
    @PostMapping("/order/{channelId}")
    @Operation(summary = "Payment order provider callback")
    @PermitAll
    @TenantIgnore
    fun notifyOrder(
        @PathVariable("channelId") channelId: Long,
        @RequestParam(required = false) params: Map<String, String>?,
        @RequestBody(required = false) body: String?,
        @RequestHeader headers: Map<String, String>,
    ): String {
        val notify = channelService.getPayClient(channelId).parseOrderNotify(params.orEmpty(), body.orEmpty(), headers)
        orderService.notifyOrder(channelId, notify)
        return "success"
    }

    @PostMapping("/refund/{channelId}")
    @Operation(summary = "Payment refund provider callback")
    @PermitAll
    @TenantIgnore
    fun notifyRefund(
        @PathVariable("channelId") channelId: Long,
        @RequestParam(required = false) params: Map<String, String>?,
        @RequestBody(required = false) body: String?,
        @RequestHeader headers: Map<String, String>,
    ): String {
        val notify = channelService.getPayClient(channelId).parseRefundNotify(params.orEmpty(), body.orEmpty(), headers)
        refundService.notifyRefund(channelId, notify)
        return "success"
    }

    @PostMapping("/transfer/{channelId}")
    @Operation(summary = "Payment transfer provider callback")
    @PermitAll
    @TenantIgnore
    fun notifyTransfer(
        @PathVariable("channelId") channelId: Long,
        @RequestParam(required = false) params: Map<String, String>?,
        @RequestBody(required = false) body: String?,
        @RequestHeader headers: Map<String, String>,
    ): String {
        val notify = channelService.getPayClient(channelId).parseTransferNotify(params.orEmpty(), body.orEmpty(), headers)
        transferService.notifyTransfer(channelId, notify)
        return "success"
    }

    @GetMapping("/get-detail")
    @Operation(summary = "Get notification task detail")
    @PreAuthorize("@ss.hasPermission('pay:notify:query')")
    fun getNotifyTaskDetail(@RequestParam("id") id: Long): CommonResult<PayNotifyTaskDetailRespVO> {
        val task = notifyService.getNotifyTask(id)
        val appName = task.appId?.let { appService.getApp(it).name }
        return CommonResult.success(PayNotifyConvert.detail(task, notifyService.getNotifyLogList(id), appName))
    }

    @GetMapping("/page")
    @Operation(summary = "Get notification task page")
    @PreAuthorize("@ss.hasPermission('pay:notify:query')")
    fun getNotifyTaskPage(
        @Valid pageVO: PayNotifyTaskPageReqVO,
    ): CommonResult<PageResult<PayNotifyTaskRespVO>> {
        val page = notifyService.getNotifyTaskPage(pageVO)
        val apps = appService.getAppMap(page.list.mapNotNull { it.appId }.toSet())
        return CommonResult.success(PageResult(page.total, page.list.map { PayNotifyConvert.convert(it, apps[it.appId]?.name) }))
    }
}
