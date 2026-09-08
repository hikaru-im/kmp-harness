package im.hikaru.ruoyi.module.mp.controller.admin.tag

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.tag.vo.*
import im.hikaru.ruoyi.module.mp.convert.tag.MpTagConvert
import im.hikaru.ruoyi.module.mp.service.tag.MpTagService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号标签")
@RestController
@RequestMapping("/mp/tag")
@Validated
class MpTagController(
    private val mpTagService: MpTagService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建公众号标签")
    @PreAuthorize("@ss.hasPermission('mp:tag:create')")
    fun createTag(@Valid @RequestBody createReqVO: MpTagCreateReqVO): CommonResult<Long> = CommonResult.success(mpTagService.createTag(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新公众号标签")
    @PreAuthorize("@ss.hasPermission('mp:tag:update')")
    fun updateTag(@Valid @RequestBody updateReqVO: MpTagUpdateReqVO): CommonResult<Boolean> { mpTagService.updateTag(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公众号标签")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:tag:delete')")
    fun deleteTag(@RequestParam("id") id: Long): CommonResult<Boolean> { mpTagService.deleteTag(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获取公众号标签详情")
    @PreAuthorize("@ss.hasPermission('mp:tag:query')")
    fun get(@RequestParam("id") id: Long): CommonResult<MpTagRespVO> = CommonResult.success(MpTagConvert.convert(mpTagService.get(id)))

    @GetMapping("/page")
    @Operation(summary = "获取公众号标签分页")
    @PreAuthorize("@ss.hasPermission('mp:tag:query')")
    fun getTagPage(pageReqVO: MpTagPageReqVO): CommonResult<PageResult<MpTagRespVO>> = CommonResult.success(MpTagConvert.convertPage(mpTagService.getTagPage(pageReqVO)))

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取公众号账号精简信息列表")
    @PreAuthorize("@ss.hasPermission('mp:account:query')")
    fun getSimpleTags(): CommonResult<List<MpTagSimpleRespVO>> = CommonResult.success(MpTagConvert.convertSimpleList(mpTagService.getTagList()))

    @PostMapping("/sync")
    @Operation(summary = "同步公众号标签")
    @Parameter(name = "accountId", description = "公众号账号的编号", required = true)
    @PreAuthorize("@ss.hasPermission('mp:tag:sync')")
    fun syncTag(@RequestParam("accountId") accountId: Long): CommonResult<Boolean> { mpTagService.syncTag(accountId); return CommonResult.success(true) }
}
