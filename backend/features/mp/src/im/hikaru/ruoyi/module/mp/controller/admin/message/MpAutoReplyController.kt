package im.hikaru.ruoyi.module.mp.controller.admin.message

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyUpdateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.convert.message.MpAutoReplyConvert
import im.hikaru.ruoyi.module.mp.service.message.MpAutoReplyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号自动回复")
@RestController
@RequestMapping("/mp/auto-reply")
@Validated
class MpAutoReplyController(
    private val mpAutoReplyService: MpAutoReplyService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得公众号自动回复分页")
    @PreAuthorize("@ss.hasPermission('mp:auto-reply:query')")
    fun getAutoReplyPage(@Valid pageVO: MpMessagePageReqVO): CommonResult<PageResult<MpAutoReplyRespVO>> = CommonResult.success(MpAutoReplyConvert.convertPage(mpAutoReplyService.getAutoReplyPage(pageVO)))

    @GetMapping("/get")
    @Operation(summary = "获得公众号自动回复")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mp:auto-reply:query')")
    fun getAutoReply(@RequestParam("id") id: Long): CommonResult<MpAutoReplyRespVO> = CommonResult.success(MpAutoReplyConvert.convert(mpAutoReplyService.getAutoReply(id)))

    @PostMapping("/create")
    @Operation(summary = "创建公众号自动回复")
    @PreAuthorize("@ss.hasPermission('mp:auto-reply:create')")
    fun createAutoReply(@Valid @RequestBody createReqVO: MpAutoReplyCreateReqVO): CommonResult<Long> = CommonResult.success(mpAutoReplyService.createAutoReply(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新公众号自动回复")
    @PreAuthorize("@ss.hasPermission('mp:auto-reply:update')")
    fun updateAutoReply(@Valid @RequestBody updateReqVO: MpAutoReplyUpdateReqVO): CommonResult<Boolean> { mpAutoReplyService.updateAutoReply(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公众号自动回复")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:auto-reply:delete')")
    fun deleteAutoReply(@RequestParam("id") id: Long): CommonResult<Boolean> { mpAutoReplyService.deleteAutoReply(id); return CommonResult.success(true) }
}
