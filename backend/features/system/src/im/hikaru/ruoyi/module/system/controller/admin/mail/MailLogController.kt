package im.hikaru.ruoyi.module.system.controller.admin.mail

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log.MailLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log.MailLogRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailLogDO
import im.hikaru.ruoyi.module.system.service.mail.MailLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - Mail log")
@RestController
@RequestMapping("/system/mail-log")
class MailLogController(
    private val mailLogService: MailLogService,
) {
    @GetMapping("/page")
    @Operation(summary = "Page mail logs")
    @PreAuthorize("@ss.hasPermission('system:mail-log:query')")
    fun page(@Valid req: MailLogPageReqVO): CommonResult<PageResult<MailLogRespVO>> {
        val result = mailLogService.getMailLogPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/get")
    @Operation(summary = "Get mail log")
    @PreAuthorize("@ss.hasPermission('system:mail-log:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<MailLogRespVO?> =
        CommonResult.success(mailLogService.getMailLog(id)?.toResponse())

    private fun MailLogDO.toResponse() = MailLogRespVO().apply {
        id = this@toResponse.id
        userId = this@toResponse.userId
        userType = this@toResponse.userType
        toMails = this@toResponse.toMails
        ccMails = this@toResponse.ccMails
        bccMails = this@toResponse.bccMails
        accountId = this@toResponse.accountId
        fromMail = this@toResponse.fromMail
        templateId = this@toResponse.templateId
        templateCode = this@toResponse.templateCode
        templateNickname = this@toResponse.templateNickname
        templateTitle = this@toResponse.templateTitle
        templateContent = this@toResponse.templateContent
        templateParams = this@toResponse.templateParams
        sendStatus = this@toResponse.sendStatus
        sendTime = this@toResponse.sendTime?.toJavaLocalDateTime()
        sendMessageId = this@toResponse.sendMessageId
        sendException = this@toResponse.sendException
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
