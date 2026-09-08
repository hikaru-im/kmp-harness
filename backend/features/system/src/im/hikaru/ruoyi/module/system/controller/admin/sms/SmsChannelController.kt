package im.hikaru.ruoyi.module.system.controller.admin.sms

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelRespVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
import im.hikaru.ruoyi.module.system.service.sms.SmsChannelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
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

@Tag(name = "Management - SMS channel")
@RestController
@RequestMapping("/system/sms-channel")
class SmsChannelController(
    private val smsChannelService: SmsChannelService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create SMS channel")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:create')")
    fun create(@Valid @RequestBody req: SmsChannelSaveReqVO): CommonResult<Long> =
        CommonResult.success(smsChannelService.createSmsChannel(req))

    @PutMapping("/update")
    @Operation(summary = "Update SMS channel")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:update')")
    fun update(@Valid @RequestBody req: SmsChannelSaveReqVO): CommonResult<Boolean> {
        smsChannelService.updateSmsChannel(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete SMS channel")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:delete')")
    fun delete(@RequestParam id: Long): CommonResult<Boolean> {
        smsChannelService.deleteSmsChannel(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete SMS channels")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:delete')")
    fun deleteList(@RequestParam ids: List<Long>): CommonResult<Boolean> {
        smsChannelService.deleteSmsChannelList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get SMS channel")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:query')")
    fun get(@RequestParam id: Long): CommonResult<SmsChannelRespVO?> =
        CommonResult.success(smsChannelService.getSmsChannel(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page SMS channels")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:query')")
    fun page(@Valid req: SmsChannelPageReqVO): CommonResult<PageResult<SmsChannelRespVO>> {
        val result = smsChannelService.getSmsChannelPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/export-excel")
    @Operation(summary = "Export SMS channels")
    @PreAuthorize("@ss.hasPermission('system:sms-channel:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun export(response: HttpServletResponse, req: SmsChannelPageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val data = smsChannelService.getSmsChannelPage(req).list.map { it.toResponse() }
        ExcelUtils.write(response, "sms-channels.xls", "SMS channels", SmsChannelRespVO::class.java, data)
    }

    @GetMapping("/list-all-simple", "/simple-list")
    @Operation(summary = "List SMS channels")
    fun simpleList(): CommonResult<List<SmsChannelSimpleRespVO>> = CommonResult.success(
        smsChannelService.getSmsChannelList().sortedBy { it.id }.map { it.toSimpleResponse() },
    )

    private fun SmsChannelDO.toSimpleResponse() = SmsChannelSimpleRespVO().apply {
        id = this@toSimpleResponse.id
        signature = this@toSimpleResponse.signature
        code = this@toSimpleResponse.code
    }

    private fun SmsChannelDO.toResponse() = SmsChannelRespVO().apply {
        id = this@toResponse.id
        signature = this@toResponse.signature
        code = this@toResponse.code
        status = this@toResponse.status
        remark = this@toResponse.remark
        apiKey = this@toResponse.apiKey
        apiSecret = this@toResponse.apiSecret
        callbackUrl = this@toResponse.callbackUrl
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
