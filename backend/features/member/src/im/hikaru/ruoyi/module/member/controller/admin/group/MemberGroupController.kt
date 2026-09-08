package im.hikaru.ruoyi.module.member.controller.admin.group

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.*
import im.hikaru.ruoyi.module.member.convert.group.MemberGroupConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.GROUP_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.group.MemberGroupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 用户分组")
@RestController
@RequestMapping("/member/group")
@Validated
class MemberGroupController(
    private val memberGroupService: MemberGroupService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建用户分组")
    @PreAuthorize("@ss.hasPermission('member:group:create')")
    fun createGroup(@Valid @RequestBody createReqVO: MemberGroupCreateReqVO): CommonResult<Long> = CommonResult.success(memberGroupService.createGroup(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新用户分组")
    @PreAuthorize("@ss.hasPermission('member:group:update')")
    fun updateGroup(@Valid @RequestBody updateReqVO: MemberGroupUpdateReqVO): CommonResult<Boolean> { memberGroupService.updateGroup(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用户分组")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('member:group:delete')")
    fun deleteGroup(@RequestParam("id") id: Long): CommonResult<Boolean> { memberGroupService.deleteGroup(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得用户分组")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('member:group:query')")
    fun getGroup(@RequestParam("id") id: Long): CommonResult<MemberGroupRespVO> = CommonResult.success(MemberGroupConvert.convert(memberGroupService.getGroup(id) ?: throw exception(GROUP_NOT_EXISTS)))

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取会员分组精简信息列表", description = "只包含被开启的会员分组，主要用于前端的下拉选项")
    fun getSimpleGroupList(): CommonResult<List<MemberGroupSimpleRespVO>> = CommonResult.success(MemberGroupConvert.convertSimpleList(memberGroupService.getEnableGroupList()))

    @GetMapping("/page")
    @Operation(summary = "获得用户分组分页")
    @PreAuthorize("@ss.hasPermission('member:group:query')")
    fun getGroupPage(@Valid pageVO: MemberGroupPageReqVO): CommonResult<PageResult<MemberGroupRespVO>> = CommonResult.success(MemberGroupConvert.convertPage(memberGroupService.getGroupPage(pageVO)))
}
