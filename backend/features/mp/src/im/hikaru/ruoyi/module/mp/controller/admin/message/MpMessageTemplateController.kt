package im.hikaru.ruoyi.module.mp.controller.admin.message

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateListReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateSendReqVO
import im.hikaru.ruoyi.module.mp.convert.message.MpMessageTemplateConvert
import im.hikaru.ruoyi.module.mp.service.message.MpMessageTemplateService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号模版消息")
@RestController
@RequestMapping("/mp/message-template")
@Validated
class MpMessageTemplateController(
    private val messageTemplateService: MpMessageTemplateService,
) {
    @DeleteMapping("/delete")
    @Operation(summary = "删除模版消息")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:message-template:delete')")
    fun deleteMessageTemplate(@RequestParam("id") id: Long): CommonResult<Boolean> { messageTemplateService.deleteMessageTemplate(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得模版消息")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mp:message-template:query')")
    fun getMessageTemplate(@RequestParam("id") id: Long): CommonResult<MpMessageTemplateRespVO> = CommonResult.success(MpMessageTemplateConvert.convert(messageTemplateService.getMessageTemplate(id)))

    @GetMapping("/list")
    @Operation(summary = "获得模版消息列表")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('mp:message-template:query')")
    fun getMessageTemplateList(listReqVO: MpMessageTemplateListReqVO): CommonResult<List<MpMessageTemplateRespVO>> = CommonResult.success(MpMessageTemplateConvert.convertList(messageTemplateService.getMessageTemplateList(listReqVO)))

    @PostMapping("/sync")
    @Operation(summary = "同步公众号模板")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('mp:message-template:sync')")
    fun syncMessageTemplate(@RequestParam("accountId") accountId: Long): CommonResult<Boolean> { messageTemplateService.syncMessageTemplate(accountId); return CommonResult.success(true) }

    @PostMapping("/send")
    @Operation(summary = "给粉丝发送模版消息")
    @PreAuthorize("@ss.hasPermission('mp:message-template:send')")
    fun sendMessageTemplate(@Valid @RequestBody sendReqVO: MpMessageTemplateSendReqVO): CommonResult<Boolean> { messageTemplateService.sendMessageTempalte(sendReqVO); return CommonResult.success(true) }
}
