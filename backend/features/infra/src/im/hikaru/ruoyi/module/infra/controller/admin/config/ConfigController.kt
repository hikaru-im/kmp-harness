package im.hikaru.ruoyi.module.infra.controller.admin.config

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.convert.config.ConfigConvert
import im.hikaru.ruoyi.module.infra.dal.dataobject.config.ConfigDO
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants
import im.hikaru.ruoyi.module.infra.service.config.ConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 参数配置")
@RestController
@RequestMapping("/infra/config")
@Validated
class ConfigController(
    private val configService: ConfigService,
) {

    @PostMapping("/create")
    @Operation(summary = "创建参数配置")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:create')")
    fun createConfig(@Valid @RequestBody createReqVO: ConfigSaveReqVO): CommonResult<Long> =
        CommonResult.success(configService.createConfig(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "修改参数配置")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:update')")
    fun updateConfig(@Valid @RequestBody updateReqVO: ConfigSaveReqVO): CommonResult<Boolean> {
        configService.updateConfig(updateReqVO)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除参数配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:delete')")
    fun deleteConfig(@RequestParam("id") id: Long): CommonResult<Boolean> {
        configService.deleteConfig(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除参数配置")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:delete')")
    fun deleteConfigList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        configService.deleteConfigList(ids)
        return CommonResult.success(true)
    }

    @GetMapping(value = ["/get"])
    @Operation(summary = "获得参数配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:query')")
    fun getConfig(@RequestParam("id") id: Long): CommonResult<ConfigRespVO?> =
        CommonResult.success(ConfigConvert.convert(configService.getConfig(id)))

    @GetMapping(value = ["/get-value-by-key"])
    @Operation(summary = "根据参数键名查询参数值", description = "不可见的配置，不允许返回给前端")
    @Parameter(name = "key", description = "参数键", required = true, example = "yunai.biz.username")
    fun getConfigKey(@RequestParam("key") key: String): CommonResult<String?> {
        val config: ConfigDO = configService.getConfigByKey(key) ?: return CommonResult.success(null)
        if (!config.visible!!) {
            throw im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception(
                ErrorCodeConstants.CONFIG_GET_VALUE_ERROR_IF_VISIBLE,
            )
        }
        return CommonResult.success(config.value)
    }

    @GetMapping("/page")
    @Operation(summary = "获取参数配置分页")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:query')")
    fun getConfigPage(@Valid pageReqVO: ConfigPageReqVO): CommonResult<PageResult<ConfigRespVO>> =
        CommonResult.success(ConfigConvert.convertPage(configService.getConfigPage(pageReqVO)))

    @GetMapping("/export-excel")
    @Operation(summary = "导出参数配置")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:config:export')")
    @Throws(java.io.IOException::class)
    fun exportConfig(exportReqVO: ConfigPageReqVO, response: HttpServletResponse) {
        exportReqVO.pageSize = PageParam.PAGE_SIZE_NONE
        val list = configService.getConfigPage(exportReqVO).list
        ExcelUtils.write(response, "参数配置.xls", "数据", ConfigRespVO::class.java, ConfigConvert.convertList(list) ?: emptyList())
    }
}
