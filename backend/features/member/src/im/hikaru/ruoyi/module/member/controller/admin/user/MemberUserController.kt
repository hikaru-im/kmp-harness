package im.hikaru.ruoyi.module.member.controller.admin.user

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.controller.admin.user.vo.*
import im.hikaru.ruoyi.module.member.convert.user.MemberUserConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.point.MemberPointBizTypeEnum
import im.hikaru.ruoyi.module.member.service.group.MemberGroupService
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import im.hikaru.ruoyi.module.member.service.point.MemberPointRecordService
import im.hikaru.ruoyi.module.member.service.tag.MemberTagService
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 会员用户")
@RestController
@RequestMapping("/member/user")
@Validated
class MemberUserController(
    private val memberUserService: MemberUserService,
    private val memberTagService: MemberTagService,
    private val memberLevelService: MemberLevelService,
    private val memberGroupService: MemberGroupService,
    private val memberPointRecordService: MemberPointRecordService,
) {
    @PutMapping("/update")
    @Operation(summary = "更新会员用户")
    @PreAuthorize("@ss.hasPermission('member:user:update')")
    fun updateUser(@Valid @RequestBody updateReqVO: MemberUserUpdateReqVO): CommonResult<Boolean> { memberUserService.updateUser(updateReqVO); return CommonResult.success(true) }

    @PutMapping("/update-level")
    @Operation(summary = "更新会员用户等级")
    @PreAuthorize("@ss.hasPermission('member:user:update-level')")
    fun updateUserLevel(@Valid @RequestBody updateReqVO: MemberUserUpdateLevelReqVO): CommonResult<Boolean> { memberLevelService.updateUserLevel(updateReqVO); return CommonResult.success(true) }

    @PutMapping("/update-point")
    @Operation(summary = "更新会员用户积分")
    @PreAuthorize("@ss.hasPermission('member:user:update-point')")
    fun updateUserPoint(@Valid @RequestBody updateReqVO: MemberUserUpdatePointReqVO): CommonResult<Boolean> {
        memberPointRecordService.createPointRecord(requireNotNull(updateReqVO.id), requireNotNull(updateReqVO.point), MemberPointBizTypeEnum.ADMIN, (WebFrameworkUtils.getLoginUserId() ?: 0L).toString())
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "获得会员用户")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('member:user:query')")
    fun getUser(@RequestParam("id") id: Long): CommonResult<MemberUserRespVO> {
        val user = memberUserService.getUser(id) ?: throw exception(USER_NOT_EXISTS)
        val response = requireNotNull(BeanUtils.toBean(user, MemberUserRespVO::class.java))
        response.levelName = user.levelId?.let(memberLevelService::getLevel)?.name
        return CommonResult.success(response)
    }

    @GetMapping("/page")
    @Operation(summary = "获得会员用户分页")
    @PreAuthorize("@ss.hasPermission('member:user:query')")
    fun getUserPage(@Valid pageVO: MemberUserPageReqVO): CommonResult<PageResult<MemberUserRespVO>> {
        val page = memberUserService.getUserPage(pageVO)
        val tags = memberTagService.getTagList(page.list.flatMap { it.tagIds.orEmpty() }.toSet())
        val levels = memberLevelService.getLevelList(page.list.mapNotNull { it.levelId }.filter { it > 0 }.toSet())
        val groups = memberGroupService.getGroupList(page.list.mapNotNull { it.groupId }.filter { it > 0 }.toSet())
        return CommonResult.success(MemberUserConvert.convertPage(page, tags, levels, groups))
    }
}
