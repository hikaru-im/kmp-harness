package im.hikaru.ruoyi.module.member.controller.app.signin

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.record.AppMemberSignInRecordRespVO
import im.hikaru.ruoyi.module.member.controller.app.signin.vo.record.AppMemberSignInRecordSummaryRespVO
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.convert.signin.MemberSignInRecordConvert
import im.hikaru.ruoyi.module.member.service.signin.MemberSignInRecordService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户 App - 签到记录")
@RestController
@RequestMapping("/member/sign-in/record")
@Validated
class AppMemberSignInRecordController(
    private val signInRecordService: MemberSignInRecordService,
) {
    @GetMapping("/get-summary")
    @Operation(summary = "获得个人签到统计")
    fun getSignInRecordSummary(): CommonResult<AppMemberSignInRecordSummaryRespVO> = CommonResult.success(signInRecordService.getSignInRecordSummary(userId()))

    @PostMapping("/create")
    @Operation(summary = "签到")
    fun createSignInRecord(): CommonResult<AppMemberSignInRecordRespVO> = CommonResult.success(MemberSignInRecordConvert.convert(signInRecordService.createSignRecord(userId())))

    @GetMapping("/page")
    @Operation(summary = "获得签到记录分页")
    fun getSignRecordPage(pageParam: PageParam): CommonResult<PageResult<AppMemberSignInRecordRespVO>> = CommonResult.success(MemberSignInRecordConvert.convertPage(signInRecordService.getSignRecordPage(userId(), pageParam)))

    private fun userId(): Long = requireNotNull(WebFrameworkUtils.getLoginUserId())
}
