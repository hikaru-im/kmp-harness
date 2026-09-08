package im.hikaru.ruoyi.module.mp.controller.admin.message

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessageRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessageSendReqVO
import im.hikaru.ruoyi.module.mp.convert.message.MpMessageConvert
import im.hikaru.ruoyi.module.mp.service.message.MpMessageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号消息")
@RestController
@RequestMapping("/mp/message")
@Validated
class MpMessageController(
    private val mpMessageService: MpMessageService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得公众号消息分页")
    @PreAuthorize("@ss.hasPermission('mp:message:query')")
    fun getMessagePage(@Valid pageVO: MpMessagePageReqVO): CommonResult<PageResult<MpMessageRespVO>> = CommonResult.success(MpMessageConvert.convertPage(mpMessageService.getMessagePage(pageVO)))

    @PostMapping("/send")
    @Operation(summary = "给粉丝发送消息")
    @PreAuthorize("@ss.hasPermission('mp:message:send')")
    fun sendMessage(@Valid @RequestBody reqVO: MpMessageSendReqVO): CommonResult<MpMessageRespVO> = CommonResult.success(MpMessageConvert.convert(mpMessageService.sendKefuMessage(reqVO)))
}
