package im.hikaru.ruoyi.module.member.controller.app.point

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.app.point.vo.AppMemberPointRecordRespVO
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.convert.point.MemberPointRecordConvert
import im.hikaru.ruoyi.module.member.service.point.MemberPointRecordService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户 App - 签到记录")
@RestController
@RequestMapping("/member/point/record")
@Validated
class AppMemberPointRecordController(
    private val pointRecordService: MemberPointRecordService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得用户积分记录分页")
    fun getPointRecordPage(@Valid pageReqVO: AppMemberPointRecordPageReqVO): CommonResult<PageResult<AppMemberPointRecordRespVO>> = CommonResult.success(MemberPointRecordConvert.convertPage02(pointRecordService.getPointRecordPage(requireNotNull(WebFrameworkUtils.getLoginUserId()), pageReqVO)))
}
