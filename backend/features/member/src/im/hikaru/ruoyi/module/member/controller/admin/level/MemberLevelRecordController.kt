package im.hikaru.ruoyi.module.member.controller.admin.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.record.MemberLevelRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.admin.level.vo.record.MemberLevelRecordRespVO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.convert.level.MemberLevelRecordConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.level.MemberLevelRecordService
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

@Tag(name = "管理后台 - 会员等级记录")
@RestController
@RequestMapping("/member/level-record")
@Validated
class MemberLevelRecordController(
    private val levelRecordService: MemberLevelRecordService,
) {
    @GetMapping("/get")
    @Operation(summary = "获得会员等级记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('member:level-record:query')")
    fun getLevelRecord(@RequestParam("id") id: Long): CommonResult<MemberLevelRecordRespVO> = CommonResult.success(MemberLevelRecordConvert.convert(levelRecordService.getLevelRecord(id) ?: throw exception(USER_NOT_EXISTS)))

    @GetMapping("/page")
    @Operation(summary = "获得会员等级记录分页")
    @PreAuthorize("@ss.hasPermission('member:level-record:query')")
    fun getLevelRecordPage(@Valid pageVO: MemberLevelRecordPageReqVO): CommonResult<PageResult<MemberLevelRecordRespVO>> = CommonResult.success(MemberLevelRecordConvert.convertPage(levelRecordService.getLevelRecordPage(pageVO)))
}
