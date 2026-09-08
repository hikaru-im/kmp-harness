package im.hikaru.ruoyi.module.member.controller.admin.config

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.member.controller.admin.config.vo.MemberConfigRespVO
import im.hikaru.ruoyi.module.member.controller.admin.config.vo.MemberConfigSaveReqVO
import im.hikaru.ruoyi.module.member.convert.config.MemberConfigConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.config.MemberConfigDO
import im.hikaru.ruoyi.module.member.service.config.MemberConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 会员设置")
@RestController
@RequestMapping("/member/config")
@Validated
class MemberConfigController(
    private val memberConfigService: MemberConfigService,
) {
    @PutMapping("/save")
    @Operation(summary = "保存会员配置")
    @PreAuthorize("@ss.hasPermission('member:config:save')")
    fun saveConfig(@Valid @RequestBody saveReqVO: MemberConfigSaveReqVO): CommonResult<Boolean> { memberConfigService.saveConfig(saveReqVO); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得会员配置")
    @PreAuthorize("@ss.hasPermission('member:config:query')")
    fun getConfig(): CommonResult<MemberConfigRespVO> = CommonResult.success(MemberConfigConvert.convert(memberConfigService.getConfig() ?: MemberConfigDO()))
}
