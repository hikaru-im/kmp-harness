package im.hikaru.ruoyi.module.pay.controller.admin.refund

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundDetailsRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundExcelVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundExportReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundPageItemRespVO
import im.hikaru.ruoyi.module.pay.controller.admin.refund.vo.PayRefundPageReqVO
import im.hikaru.ruoyi.module.pay.convert.refund.PayRefundConvert
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.refund.PayRefundService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Payment Refund")
@RestController
@RequestMapping("/pay/refund")
@Validated
class PayRefundController(
    private val refundService: PayRefundService,
    private val appService: PayAppService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get payment refund")
    @PreAuthorize("@ss.hasPermission('pay:refund:query')")
    fun getRefund(@RequestParam("id") id: Long): CommonResult<PayRefundDetailsRespVO> {
        val refund = refundService.getRefund(id)
        return CommonResult.success(PayRefundConvert.convert(refund, refund.appId?.let { appService.getApp(it) }))
    }

    @GetMapping("/page")
    @Operation(summary = "Get payment refund page")
    @PreAuthorize("@ss.hasPermission('pay:refund:query')")
    fun getRefundPage(@Valid pageVO: PayRefundPageReqVO): CommonResult<PageResult<PayRefundPageItemRespVO>> {
        val page = refundService.getRefundPage(pageVO)
        return CommonResult.success(PayRefundConvert.convertPage(page, appService.getAppMap(page.list.mapNotNull { it.appId }.toSet())))
    }

    @GetMapping("/export-excel")
    @Operation(summary = "Export payment refunds")
    @PreAuthorize("@ss.hasPermission('pay:refund:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun exportRefundExcel(@Valid exportReqVO: PayRefundExportReqVO, response: HttpServletResponse) {
        val refunds = refundService.getRefundList(exportReqVO)
        val apps = appService.getAppMap(refunds.mapNotNull { it.appId }.toSet())
        ExcelUtils.write(response, "payment-refunds.xls", "Data", PayRefundExcelVO::class.java, PayRefundConvert.convertExcel(refunds, apps))
    }
}
