package im.hikaru.ruoyi.module.member.controller.app.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.app.level.vo.experience.AppMemberExperienceRecordRespVO
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.convert.level.MemberExperienceRecordConvert
import im.hikaru.ruoyi.module.member.service.level.MemberExperienceRecordService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户 App - 会员经验记录")
@RestController
@RequestMapping("/member/experience-record")
@Validated
class AppMemberExperienceRecordController(
    private val experienceRecordService: MemberExperienceRecordService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得会员经验记录分页")
    fun getExperienceRecordPage(@Valid pageParam: PageParam): CommonResult<PageResult<AppMemberExperienceRecordRespVO>> = CommonResult.success(MemberExperienceRecordConvert.convertPage02(experienceRecordService.getExperienceRecordPage(requireNotNull(WebFrameworkUtils.getLoginUserId()), pageParam)))
}
