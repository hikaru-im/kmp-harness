package im.hikaru.ruoyi.module.member.controller.app.level

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.member.controller.app.level.vo.level.AppMemberLevelRespVO
import im.hikaru.ruoyi.module.member.convert.level.MemberLevelConvert
import im.hikaru.ruoyi.module.member.service.level.MemberLevelService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户 App - 会员等级")
@RestController
@RequestMapping("/member/level")
@Validated
class AppMemberLevelController(
    private val memberLevelService: MemberLevelService,
) {
    @GetMapping("/list")
    @Operation(summary = "获得会员等级列表")
    @PermitAll
    fun getLevelList(): CommonResult<List<AppMemberLevelRespVO>> = CommonResult.success(MemberLevelConvert.convertList02(memberLevelService.getEnableLevelList()))
}
