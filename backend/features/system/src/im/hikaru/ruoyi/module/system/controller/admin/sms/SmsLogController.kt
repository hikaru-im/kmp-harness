package im.hikaru.ruoyi.module.system.controller.admin.sms

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.log.SmsLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.log.SmsLogRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsLogDO
import im.hikaru.ruoyi.module.system.service.sms.SmsLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletResponse
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - SMS log")
@RestController
@RequestMapping("/system/sms-log")
class SmsLogController(
    private val smsLogService: SmsLogService,
) {
    @GetMapping("/page")
    @Operation(summary = "Page SMS logs")
    @PreAuthorize("@ss.hasPermission('system:sms-log:query')")
    fun page(@Valid req: SmsLogPageReqVO): CommonResult<PageResult<SmsLogRespVO>> {
        val result = smsLogService.getSmsLogPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/get")
    @Operation(summary = "Get SMS log")
    @PreAuthorize("@ss.hasPermission('system:sms-log:query')")
    fun get(@RequestParam id: Long): CommonResult<SmsLogRespVO?> =
        CommonResult.success(smsLogService.getSmsLog(id)?.toResponse())

    @GetMapping("/export-excel")
    @Operation(summary = "Export SMS logs")
    @PreAuthorize("@ss.hasPermission('system:sms-log:export')")
    fun export(@Valid req: SmsLogPageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val data = smsLogService.getSmsLogPage(req).list.map { it.toResponse() }
        ExcelUtils.write(response, "sms-logs.xls", "Logs", SmsLogRespVO::class.java, data)
    }

    private fun SmsLogDO.toResponse() = SmsLogRespVO().apply {
        id = this@toResponse.id
        channelId = this@toResponse.channelId
        channelCode = this@toResponse.channelCode
        templateId = this@toResponse.templateId
        templateCode = this@toResponse.templateCode
        templateType = this@toResponse.templateType
        templateContent = this@toResponse.templateContent
        templateParams = this@toResponse.templateParams
        apiTemplateId = this@toResponse.apiTemplateId
        mobile = this@toResponse.mobile
        userId = this@toResponse.userId
        userType = this@toResponse.userType
        sendStatus = this@toResponse.sendStatus
        sendTime = this@toResponse.sendTime?.toJavaLocalDateTime()
        apiSendCode = this@toResponse.apiSendCode
        apiSendMsg = this@toResponse.apiSendMsg
        apiRequestId = this@toResponse.apiRequestId
        apiSerialNo = this@toResponse.apiSerialNo
        receiveStatus = this@toResponse.receiveStatus
        receiveTime = this@toResponse.receiveTime?.toJavaLocalDateTime()
        apiReceiveCode = this@toResponse.apiReceiveCode
        apiReceiveMsg = this@toResponse.apiReceiveMsg
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
