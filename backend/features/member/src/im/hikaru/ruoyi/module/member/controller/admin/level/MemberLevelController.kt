package im.hikaru.ruoyi.module.member.controller.admin.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.level.*
import im.hikaru.ruoyi.module.member.convert.level.MemberLevelConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.LEVEL_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 会员等级")
@RestController
@RequestMapping("/member/level")
@Validated
class MemberLevelController(
    private val memberLevelService: MemberLevelService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建会员等级")
    @PreAuthorize("@ss.hasPermission('member:level:create')")
    fun createLevel(@Valid @RequestBody createReqVO: MemberLevelCreateReqVO): CommonResult<Long> = CommonResult.success(memberLevelService.createLevel(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新会员等级")
    @PreAuthorize("@ss.hasPermission('member:level:update')")
    fun updateLevel(@Valid @RequestBody updateReqVO: MemberLevelUpdateReqVO): CommonResult<Boolean> { memberLevelService.updateLevel(updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除会员等级")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('member:level:delete')")
    fun deleteLevel(@RequestParam("id") id: Long): CommonResult<Boolean> { memberLevelService.deleteLevel(id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得会员等级")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('member:level:query')")
    fun getLevel(@RequestParam("id") id: Long): CommonResult<MemberLevelRespVO> = CommonResult.success(MemberLevelConvert.convert(memberLevelService.getLevel(id) ?: throw exception(LEVEL_NOT_EXISTS)))

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取会员等级精简信息列表", description = "只包含被开启的会员等级，主要用于前端的下拉选项")
    fun getSimpleLevelList(): CommonResult<List<MemberLevelSimpleRespVO>> = CommonResult.success(MemberLevelConvert.convertSimpleList(memberLevelService.getEnableLevelList()))

    @GetMapping("/list")
    @Operation(summary = "获得会员等级列表")
    @PreAuthorize("@ss.hasPermission('member:level:query')")
    fun getLevelList(@Valid listReqVO: MemberLevelListReqVO): CommonResult<List<MemberLevelRespVO>> = CommonResult.success(MemberLevelConvert.convertList(memberLevelService.getLevelList(listReqVO)))
}
