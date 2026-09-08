package im.hikaru.ruoyi.module.system.controller.admin.notice

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.api.websocket.WebSocketSenderApi
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticeRespVO
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticeSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notice.NoticeDO
import im.hikaru.ruoyi.module.system.service.notice.NoticeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Management - Notice")
@RestController
@RequestMapping("/system/notice")
@Validated
class NoticeController(
    private val noticeService: NoticeService,
    private val webSocketSenderApi: WebSocketSenderApi,
) {
    @PostMapping("/create")
    @Operation(summary = "Create notice")
    @PreAuthorize("@ss.hasPermission('system:notice:create')")
    fun create(@Valid @RequestBody req: NoticeSaveReqVO): CommonResult<Long> =
        CommonResult.success(noticeService.createNotice(req))

    @PutMapping("/update")
    @Operation(summary = "Update notice")
    @PreAuthorize("@ss.hasPermission('system:notice:update')")
    fun update(@Valid @RequestBody req: NoticeSaveReqVO): CommonResult<Boolean> {
        noticeService.updateNotice(req)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete notice")
    @PreAuthorize("@ss.hasPermission('system:notice:delete')")
    fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> {
        noticeService.deleteNotice(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "Delete notices")
    @PreAuthorize("@ss.hasPermission('system:notice:delete')")
    fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        noticeService.deleteNoticeList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/page")
    @Operation(summary = "Page notices")
    @PreAuthorize("@ss.hasPermission('system:notice:query')")
    fun page(@Valid req: NoticePageReqVO): CommonResult<PageResult<NoticeRespVO>> {
        val result = noticeService.getNoticePage(req)
        return CommonResult.success(PageResult(result.total, result.list.map { it.toResponse() }))
    }

    @GetMapping("/get")
    @Operation(summary = "Get notice")
    @PreAuthorize("@ss.hasPermission('system:notice:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<NoticeRespVO?> =
        CommonResult.success(noticeService.getNotice(id)?.toResponse())

    @PostMapping("/push")
    @Operation(summary = "Push notice to online administrators")
    @PreAuthorize("@ss.hasPermission('system:notice:update')")
    fun push(@RequestParam("id") id: Long): CommonResult<Boolean> {
        val notice = requireNotNull(noticeService.getNotice(id)) { "Notice does not exist" }
        webSocketSenderApi.sendObject(UserTypeEnum.ADMIN.value, "notice-push", notice)
        return CommonResult.success(true)
    }

    private fun NoticeDO.toResponse() = NoticeRespVO().apply {
        id = this@toResponse.id
        title = this@toResponse.title
        type = this@toResponse.type
        content = this@toResponse.content
        status = this@toResponse.status
        createTime = this@toResponse.createTime?.toJavaLocalDateTime()
    }
}
