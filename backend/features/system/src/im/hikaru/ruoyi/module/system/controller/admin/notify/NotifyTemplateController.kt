package im.hikaru.ruoyi.module.system.controller.admin.notify

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateRespVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplateSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO
import im.hikaru.ruoyi.module.system.service.notify.NotifySendService
import im.hikaru.ruoyi.module.system.service.notify.NotifyTemplateService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - Notify template")
@RestController
@RequestMapping("/system/notify-template")
@Validated
class NotifyTemplateController(
    private val notifyTemplateService: NotifyTemplateService,
    private val notifySendService: NotifySendService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create notify template")
    @PreAuthorize("@ss.hasPermission('system:notify-template:create')")
    fun create(@Valid @RequestBody req: NotifyTemplateSaveReqVO): CommonResult<Long> =
        CommonResult.success(notifyTemplateService.createNotifyTemplate(req))

    @PutMapping("/update")
    @Operation(summary = "Update notify template")
    @PreAuthorize("@ss.hasPermission('system:notify-template:update')")
    fun update(@Valid @RequestBody req: NotifyTemplateSaveReqVO): CommonResult<Boolean> {
        notifyTemplateService.updateNotifyTemplate(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete notify template")
    @PreAuthorize("@ss.hasPermission('system:notify-template:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        notifyTemplateService.deleteNotifyTemplate(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete notify templates")
    @PreAuthorize("@ss.hasPermission('system:notify-template:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        notifyTemplateService.deleteNotifyTemplateList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get notify template")
    @PreAuthorize("@ss.hasPermission('system:notify-template:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<NotifyTemplateRespVO?> =
        CommonResult.success(notifyTemplateService.getNotifyTemplate(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page notify templates")
    @PreAuthorize("@ss.hasPermission('system:notify-template:query')")
    fun page(@Valid req: NotifyTemplatePageReqVO): CommonResult<PageResult<NotifyTemplateRespVO>> {
        val result = notifyTemplateService.getNotifyTemplatePage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/export-excel")
    @Operation(summary = "Export notify templates")
    @PreAuthorize("@ss.hasPermission('system:notify-template:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun export(response: HttpServletResponse, req: NotifyTemplatePageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        val data = notifyTemplateService.getNotifyTemplatePage(req).list.map { it.toResponse() }
        ExcelUtils.write(response, "notify-templates.xls", "Notify templates", NotifyTemplateRespVO::class.java, data)
    }

    @GetMapping("/list-all-simple", "/simple-list")
    @Operation(summary = "List enabled notify templates")
    fun simpleList(): CommonResult<List<NotifyTemplateSimpleRespVO>> = CommonResult.success(
        notifyTemplateService.getNotifyTemplateListByStatus(CommonStatusEnum.ENABLE.status).map { it.toSimpleResponse() },
    )

    @PostMapping("/send-notify")
    @Operation(summary = "Send notify message")
    @PreAuthorize("@ss.hasPermission('system:notify-template:send-notify')")
    fun sendNotify(@Valid @RequestBody req: NotifyTemplateSendReqVO): CommonResult<Long?> {
        val id = if (req.userType == UserTypeEnum.MEMBER.value) {
            notifySendService.sendSingleNotifyToMember(
                requireNotNull(req.userId), requireNotNull(req.templateCode), req.templateParams,
            )
        } else {
            notifySendService.sendSingleNotifyToAdmin(
                requireNotNull(req.userId), requireNotNull(req.templateCode), req.templateParams,
            )
        }
        return CommonResult.success(id)
    }

    private fun NotifyTemplateDO.toSimpleResponse() = NotifyTemplateSimpleRespVO().apply {
        id = this@toSimpleResponse.id
        name = this@toSimpleResponse.name
        code = this@toSimpleResponse.code
    }

    private fun NotifyTemplateDO.toResponse() = NotifyTemplateRespVO().apply {
        id = this@toResponse.id
        name = this@toResponse.name
        code = this@toResponse.code
        type = this@toResponse.type
        nickname = this@toResponse.nickname
        content = this@toResponse.content
        params = this@toResponse.params
        status = this@toResponse.status
        remark = this@toResponse.remark
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
