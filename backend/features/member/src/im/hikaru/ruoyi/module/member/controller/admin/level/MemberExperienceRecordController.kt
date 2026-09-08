package im.hikaru.ruoyi.module.member.controller.admin.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience.MemberExperienceRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.experience.MemberExperienceRecordRespVO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.convert.level.MemberExperienceRecordConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.level.MemberExperienceRecordService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "管理后台 - 会员经验记录")
@RestController
@RequestMapping("/member/experience-record")
@Validated
class MemberExperienceRecordController(
    private val experienceRecordService: MemberExperienceRecordService,
) {
    @GetMapping("/get")
    @Operation(summary = "获得会员经验记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('member:experience-record:query')")
    fun getExperienceRecord(@RequestParam("id") id: Long): CommonResult<MemberExperienceRecordRespVO> = CommonResult.success(MemberExperienceRecordConvert.convert(experienceRecordService.getExperienceRecord(id) ?: throw exception(USER_NOT_EXISTS)))

    @GetMapping("/page")
    @Operation(summary = "获得会员经验记录分页")
    @PreAuthorize("@ss.hasPermission('member:experience-record:query')")
    fun getExperienceRecordPage(@Valid pageVO: MemberExperienceRecordPageReqVO): CommonResult<PageResult<MemberExperienceRecordRespVO>> = CommonResult.success(MemberExperienceRecordConvert.convertPage(experienceRecordService.getExperienceRecordPage(pageVO)))
}
