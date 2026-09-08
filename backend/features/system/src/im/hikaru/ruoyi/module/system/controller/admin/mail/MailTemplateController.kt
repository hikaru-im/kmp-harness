package im.hikaru.ruoyi.module.system.controller.admin.mail

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateRespVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO
import im.hikaru.ruoyi.module.system.service.mail.MailSendService
import im.hikaru.ruoyi.module.system.service.mail.MailTemplateService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Management - Mail template")
@RestController
@RequestMapping("/system/mail-template")
class MailTemplateController(
    private val mailTemplateService: MailTemplateService,
    private val mailSendService: MailSendService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create mail template")
    @PreAuthorize("@ss.hasPermission('system:mail-template:create')")
    fun create(@Valid @RequestBody req: MailTemplateSaveReqVO): CommonResult<Long> =
        CommonResult.success(mailTemplateService.createMailTemplate(req))

    @PutMapping("/update")
    @Operation(summary = "Update mail template")
    @PreAuthorize("@ss.hasPermission('system:mail-template:update')")
    fun update(@Valid @RequestBody req: MailTemplateSaveReqVO): CommonResult<Boolean> {
        mailTemplateService.updateMailTemplate(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete mail template")
    @PreAuthorize("@ss.hasPermission('system:mail-template:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        mailTemplateService.deleteMailTemplate(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete mail templates")
    @PreAuthorize("@ss.hasPermission('system:mail-template:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        mailTemplateService.deleteMailTemplateList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get mail template")
    @PreAuthorize("@ss.hasPermission('system:mail-template:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<MailTemplateRespVO?> =
        CommonResult.success(mailTemplateService.getMailTemplate(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page mail templates")
    @PreAuthorize("@ss.hasPermission('system:mail-template:query')")
    fun page(@Valid req: MailTemplatePageReqVO): CommonResult<PageResult<MailTemplateRespVO>> {
        val result = mailTemplateService.getMailTemplatePage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/list-all-simple", "/simple-list")
    @Operation(summary = "List enabled mail templates")
    fun simpleList(): CommonResult<List<MailTemplateSimpleRespVO>> = CommonResult.success(
        mailTemplateService.getMailTemplateListByStatus(CommonStatusEnum.ENABLE.status).map { it.toSimpleResponse() },
    )

    @PostMapping("/send-mail")
    @Operation(summary = "Send mail")
    @PreAuthorize("@ss.hasPermission('system:mail-template:send-mail')")
    fun sendMail(@Valid @RequestBody req: MailTemplateSendReqVO): CommonResult<Long> = CommonResult.success(
        mailSendService.sendSingleMailToAdmin(
            SecurityFrameworkUtils.getLoginUserId(), req.toMails, req.ccMails, req.bccMails,
            requireNotNull(req.templateCode), req.templateParams,
        ),
    )

    private fun MailTemplateDO.toSimpleResponse() = MailTemplateSimpleRespVO().apply {
        id = this@toSimpleResponse.id
        name = this@toSimpleResponse.name
        code = this@toSimpleResponse.code
    }

    private fun MailTemplateDO.toResponse() = MailTemplateRespVO().apply {
        id = this@toResponse.id
        name = this@toResponse.name
        code = this@toResponse.code
        accountId = this@toResponse.accountId
        nickname = this@toResponse.nickname
        title = this@toResponse.title
        content = this@toResponse.content
        params = this@toResponse.params
        status = this@toResponse.status
        remark = this@toResponse.remark
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
