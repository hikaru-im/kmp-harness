package im.hikaru.ruoyi.module.member.controller.admin.signin

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record.MemberSignInRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record.MemberSignInRecordRespVO
import im.hikaru.ruoyi.module.member.convert.signin.MemberSignInRecordConvert
import im.hikaru.ruoyi.module.member.service.signin.MemberSignInRecordService
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "管理后台 - 签到记录")
@RestController
@RequestMapping("/member/sign-in/record")
@Validated
class MemberSignInRecordController(
    private val signInRecordService: MemberSignInRecordService,
    private val memberUserService: MemberUserService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得签到记录分页")
    @PreAuthorize("@ss.hasPermission('point:sign-in-record:query')")
    fun getSignInRecordPage(@Valid pageVO: MemberSignInRecordPageReqVO): CommonResult<PageResult<MemberSignInRecordRespVO>> {
        val page = signInRecordService.getSignInRecordPage(pageVO)
        return CommonResult.success(MemberSignInRecordConvert.convertPage(page, memberUserService.getUserList(page.list.mapNotNull { it.userId }.toSet())))
    }
}
