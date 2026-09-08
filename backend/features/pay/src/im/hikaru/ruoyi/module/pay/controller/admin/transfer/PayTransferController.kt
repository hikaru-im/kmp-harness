package im.hikaru.ruoyi.module.pay.controller.admin.transfer

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferPageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.transfer.vo.PayTransferRespVO
import im.hikaru.ruoyi.module.pay.convert.transfer.PayTransferConvert
import im.hikaru.ruoyi.module.pay.service.app.PayAppService
import im.hikaru.ruoyi.module.pay.service.transfer.PayTransferService
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

@Tag(name = "Admin - Payment Transfer")
@RestController
@RequestMapping("/pay/transfer")
@Validated
class PayTransferController(
    private val transferService: PayTransferService,
    private val appService: PayAppService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get payment transfer")
    @PreAuthorize("@ss.hasPermission('pay:transfer:query')")
    fun getTransfer(@RequestParam("id") id: Long): CommonResult<PayTransferRespVO> {
        val transfer = transferService.getTransfer(id)
        return CommonResult.success(PayTransferConvert.convert(transfer, transfer.appId?.let { appService.getApp(it).name }))
    }

    @GetMapping("/page")
    @Operation(summary = "Get payment transfer page")
    @PreAuthorize("@ss.hasPermission('pay:transfer:query')")
    fun getTransferPage(@Valid pageVO: PayTransferPageReqVO): CommonResult<PageResult<PayTransferRespVO>> {
        val page = transferService.getTransferPage(pageVO)
        val apps = appService.getAppMap(page.list.mapNotNull { it.appId }.toSet())
        return CommonResult.success(PageResult(page.total, page.list.map { PayTransferConvert.convert(it, apps[it.appId]?.name) }))
    }

    @GetMapping("/export-excel")
    @Operation(summary = "Export payment transfers")
    @PreAuthorize("@ss.hasPermission('pay:transfer:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun exportTransfer(pageReqVO: PayTransferPageReqVO, response: HttpServletResponse) {
        pageReqVO.pageSize = PageParam.PAGE_SIZE_NONE
        val data = getTransferPage(pageReqVO).data?.list.orEmpty()
        ExcelUtils.write(response, "payment-transfers.xls", "Data", PayTransferRespVO::class.java, data)
    }
}
