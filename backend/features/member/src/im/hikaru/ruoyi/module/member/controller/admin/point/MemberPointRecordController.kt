package im.hikaru.ruoyi.module.member.controller.admin.point

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod.MemberPointRecordRespVO
import im.hikaru.ruoyi.module.member.convert.point.MemberPointRecordConvert
import im.hikaru.ruoyi.module.member.service.point.MemberPointRecordService
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
@RequestMapping("/member/point/record")
@Validated
class MemberPointRecordController(
    private val pointRecordService: MemberPointRecordService,
    private val memberUserService: MemberUserService,
) {
    @GetMapping("/page")
    @Operation(summary = "获得用户积分记录分页")
    @PreAuthorize("@ss.hasPermission('point:record:query')")
    fun getPointRecordPage(@Valid pageVO: MemberPointRecordPageReqVO): CommonResult<PageResult<MemberPointRecordRespVO>> {
        val page = pointRecordService.getPointRecordPage(pageVO)
        return CommonResult.success(MemberPointRecordConvert.convertPage(page, memberUserService.getUserList(page.list.mapNotNull { it.userId }.toSet())))
    }
}
