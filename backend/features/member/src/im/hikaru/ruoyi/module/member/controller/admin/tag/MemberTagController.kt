package im.hikaru.ruoyi.module.member.controller.admin.tag

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagCreateReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagRespVO
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagUpdateReqVO
import im.hikaru.ruoyi.module.member.convert.tag.MemberTagConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.TAG_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.tag.MemberTagService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 会员标签")
@RestController
@RequestMapping("/member/tag")
@Validated
class MemberTagController(
    private val memberTagService: MemberTagService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建会员标签")
    @PreAuthorize("@ss.hasPermission('member:tag:create')")
    fun createTag(@Valid @RequestBody createReqVO: MemberTagCreateReqVO): CommonResult<Long> = CommonResult.success(memberTagService.createTag(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新会员标签")
    @PreAuthorize("@ss.hasPermission('member:tag:update')")
    fun updateTag(@Valid @RequestBody updateReqVO: MemberTagUpdateReqVO): CommonResult<Boolean> { memberTagService.updateTag(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除会员标签")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('member:tag:delete')")
    fun deleteTag(@RequestParam("id") id: Long): CommonResult<Boolean> { memberTagService.deleteTag(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得会员标签")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('member:tag:query')")
    fun getMemberTag(@RequestParam("id") id: Long): CommonResult<MemberTagRespVO> = CommonResult.success(MemberTagConvert.convert(memberTagService.getTag(id) ?: throw exception(TAG_NOT_EXISTS)))

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取会员标签精简信息列表", description = "只包含被开启的会员标签，主要用于前端的下拉选项")
    fun getSimpleTagList(): CommonResult<List<MemberTagRespVO>> = CommonResult.success(MemberTagConvert.convertList(memberTagService.getTagList()))

    @GetMapping("/list")
    @Operation(summary = "获得会员标签列表")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "1024,2048")
    @PreAuthorize("@ss.hasPermission('member:tag:query')")
    fun getMemberTagList(@RequestParam("ids") ids: Collection<Long>): CommonResult<List<MemberTagRespVO>> = CommonResult.success(MemberTagConvert.convertList(memberTagService.getTagList(ids)))

    @GetMapping("/page")
    @Operation(summary = "获得会员标签分页")
    @PreAuthorize("@ss.hasPermission('member:tag:query')")
    fun getTagPage(@Valid pageVO: MemberTagPageReqVO): CommonResult<PageResult<MemberTagRespVO>> = CommonResult.success(MemberTagConvert.convertPage(memberTagService.getTagPage(pageVO)))
}
