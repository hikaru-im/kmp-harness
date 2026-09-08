package im.hikaru.ruoyi.module.system.controller.admin.mail

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountRespVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.account.MailAccountSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO
import im.hikaru.ruoyi.module.system.service.mail.MailAccountService
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

@Tag(name = "Management - Mail account")
@RestController
@RequestMapping("/system/mail-account")
class MailAccountController(
    private val mailAccountService: MailAccountService,
) {
    @PostMapping("/create")
    @Operation(summary = "Create mail account")
    @PreAuthorize("@ss.hasPermission('system:mail-account:create')")
    fun create(@Valid @RequestBody req: MailAccountSaveReqVO): CommonResult<Long> =
        CommonResult.success(mailAccountService.createMailAccount(req))

    @PutMapping("/update")
    @Operation(summary = "Update mail account")
    @PreAuthorize("@ss.hasPermission('system:mail-account:update')")
    fun update(@Valid @RequestBody req: MailAccountSaveReqVO): CommonResult<Boolean> {
        mailAccountService.updateMailAccount(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete mail account")
    @PreAuthorize("@ss.hasPermission('system:mail-account:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        mailAccountService.deleteMailAccount(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete mail accounts")
    @PreAuthorize("@ss.hasPermission('system:mail-account:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        mailAccountService.deleteMailAccountList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "Get mail account")
    @PreAuthorize("@ss.hasPermission('system:mail-account:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<MailAccountRespVO?> =
        CommonResult.success(mailAccountService.getMailAccount(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page mail accounts")
    @PreAuthorize("@ss.hasPermission('system:mail-account:query')")
    fun page(@Valid req: MailAccountPageReqVO): CommonResult<PageResult<MailAccountRespVO>> {
        val result = mailAccountService.getMailAccountPage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/list-all-simple", "/simple-list")
    @Operation(summary = "List mail accounts")
    fun simpleList(): CommonResult<List<MailAccountSimpleRespVO>> =
        CommonResult.success(mailAccountService.getMailAccountList().map { it.toSimpleResponse() })

    private fun MailAccountDO.toSimpleResponse() = MailAccountSimpleRespVO().apply {
        id = this@toSimpleResponse.id
        mail = this@toSimpleResponse.mail
    }

    private fun MailAccountDO.toResponse() = MailAccountRespVO().apply {
        id = this@toResponse.id
        mail = this@toResponse.mail
        username = this@toResponse.username
        password = this@toResponse.password
        host = this@toResponse.host
        port = this@toResponse.port
        sslEnable = this@toResponse.sslEnable
        starttlsEnable = this@toResponse.starttlsEnable
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
