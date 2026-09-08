package im.hikaru.ruoyi.module.system.controller.admin.notify

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.message.NotifyMessageRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyMessageDO
import im.hikaru.ruoyi.module.system.service.notify.NotifyMessageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.datetime.toJavaLocalDateTime
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Management - Notify message")
@RestController
@RequestMapping("/system/notify-message")
@Validated
class NotifyMessageController(
    private val notifyMessageService: NotifyMessageService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get notify message")
    @PreAuthorize("@ss.hasPermission('system:notify-message:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<NotifyMessageRespVO?> =
        CommonResult.success(notifyMessageService.getNotifyMessage(id)?.toResponse())

    @GetMapping("/page")
    @Operation(summary = "Page notify messages")
    @PreAuthorize("@ss.hasPermission('system:notify-message:query')")
    fun page(@Valid req: NotifyMessagePageReqVO): CommonResult<PageResult<NotifyMessageRespVO>> {
        val result = notifyMessageService.getNotifyMessagePage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/my-page")
    @Operation(summary = "Page my notify messages")
    fun myPage(@Valid req: NotifyMessageMyPageReqVO): CommonResult<PageResult<NotifyMessageRespVO>> {
        val result = notifyMessageService.getMyNotifyMessagePage(req, requireLoginId(), UserTypeEnum.ADMIN.value)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @PutMapping("/update-read")
    @Operation(summary = "Mark notify messages as read")
    fun updateRead(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        notifyMessageService.updateNotifyMessageRead(ids, requireLoginId(), UserTypeEnum.ADMIN.value)
        return CommonResult.success(true)
    }

    @PutMapping("/update-all-read")
    @Operation(summary = "Mark all notify messages as read")
    fun updateAllRead(): CommonResult<Boolean> {
        notifyMessageService.updateAllNotifyMessageRead(requireLoginId(), UserTypeEnum.ADMIN.value)
        return CommonResult.success(true)
    }

    @GetMapping("/get-unread-list")
    @Operation(summary = "List latest unread notify messages")
    fun unreadList(@RequestParam(name = "size", defaultValue = "10") size: Int): CommonResult<List<NotifyMessageRespVO>> =
        CommonResult.success(
            notifyMessageService.getUnreadNotifyMessageList(requireLoginId(), UserTypeEnum.ADMIN.value, size)
                .map { it.toResponse() },
        )

    @GetMapping("/get-unread-count")
    @Operation(summary = "Count unread notify messages")
    fun unreadCount(): CommonResult<Long> = CommonResult.success(
        notifyMessageService.getUnreadNotifyMessageCount(requireLoginId(), UserTypeEnum.ADMIN.value),
    )

    private fun requireLoginId(): Long =
        requireNotNull(SecurityFrameworkUtils.getLoginUserId()) { "No authenticated user" }

    private fun NotifyMessageDO.toResponse() = NotifyMessageRespVO().apply {
        id = this@toResponse.id
        userId = this@toResponse.userId
        userType = this@toResponse.userType
        templateId = this@toResponse.templateId
        templateCode = this@toResponse.templateCode
        templateNickname = this@toResponse.templateNickname
        templateContent = this@toResponse.templateContent
        templateType = this@toResponse.templateType
        templateParams = this@toResponse.templateParams
        readStatus = this@toResponse.readStatus
        readTime = this@toResponse.readTime?.toJavaLocalDateTime()
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
