package im.hikaru.ruoyi.module.system.controller.admin.sms

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateRespVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO
import im.hikaru.ruoyi.module.system.service.sms.SmsSendService
import im.hikaru.ruoyi.module.system.service.sms.SmsTemplateService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletResponse
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - SMS template")
@RestController
@RequestMapping("/system/sms-template")
class SmsTemplateController(
    private val smsTemplateService: SmsTemplateService,
    private val smsSendService: SmsSendService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create SMS template")
    @PreAuthorize("@ss.hasPermission('system:sms-template:create')")
    fun create(@Valid @RequestBody req: SmsTemplateSaveReqVO): CommonResult<Long> =
        CommonResult.success(smsTemplateService.createSmsTemplate(req))

    @PutMapping("/update")
    @Operation(summary = "Update SMS template")
    @PreAuthorize("@ss.hasPermission('system:sms-template:update')")
    fun update(@Valid @RequestBody req: SmsTemplateSaveReqVO): CommonResult<Boolean> {
        smsTemplateService.updateSmsTemplate(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete SMS template")
    @PreAuthorize("@ss.hasPermission('system:sms-template:delete')")
    fun delete(@RequestParam id: Long): CommonResult<Boolean> {
        smsTemplateService.deleteSmsTemplate(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete SMS templates")
    @PreAuthorize("@ss.hasPermission('system:sms-template:delete')")
    fun deleteList(@RequestParam ids: List<Long>): CommonResult<Boolean> {
        smsTemplateService.deleteSmsTemplateList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get SMS template")
    @PreAuthorize("@ss.hasPermission('system:sms-template:query')")
    fun get(@RequestParam id: Long): CommonResult<SmsTemplateRespVO?> =
        CommonResult.success(smsTemplateService.getSmsTemplate(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page SMS templates")
    @PreAuthorize("@ss.hasPermission('system:sms-template:query')")
    fun page(@Valid req: SmsTemplatePageReqVO): CommonResult<PageResult<SmsTemplateRespVO>> {
        val result = smsTemplateService.getSmsTemplatePage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/list-all-simple", "/simple-list")
    @Operation(summary = "List enabled SMS templates")
    fun simpleList(): CommonResult<List<SmsTemplateSimpleRespVO>> = CommonResult.success(
        smsTemplateService.getSmsTemplateListByStatus(CommonStatusEnum.ENABLE.status).map { it.toSimpleResponse() },
    )

    @GetMapping("/export-excel")
    @Operation(summary = "Export SMS templates")
    @PreAuthorize("@ss.hasPermission('system:sms-template:export')")
    fun export(@Valid req: SmsTemplatePageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val data = smsTemplateService.getSmsTemplatePage(req).list.map { it.toResponse() }
        ExcelUtils.write(response, "sms-templates.xls", "Templates", SmsTemplateRespVO::class.java, data)
    }

    @PostMapping("/send-sms")
    @Operation(summary = "Send SMS")
    @PreAuthorize("@ss.hasPermission('system:sms-template:send-sms')")
    fun sendSms(@Valid @RequestBody req: SmsTemplateSendReqVO): CommonResult<Long> = CommonResult.success(
        smsSendService.sendSingleSmsToAdmin(
            requireNotNull(req.mobile), null, requireNotNull(req.templateCode), req.templateParams.orEmpty(),
        ),
    )

    private fun SmsTemplateDO.toSimpleResponse() = SmsTemplateSimpleRespVO().apply {
        id = this@toSimpleResponse.id
        name = this@toSimpleResponse.name
        code = this@toSimpleResponse.code
    }

    private fun SmsTemplateDO.toResponse() = SmsTemplateRespVO().apply {
        id = this@toResponse.id
        type = this@toResponse.type
        status = this@toResponse.status
        code = this@toResponse.code
        name = this@toResponse.name
        content = this@toResponse.content
        params = this@toResponse.params
        remark = this@toResponse.remark
        apiTemplateId = this@toResponse.apiTemplateId
        channelId = this@toResponse.channelId
        channelCode = this@toResponse.channelCode
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
