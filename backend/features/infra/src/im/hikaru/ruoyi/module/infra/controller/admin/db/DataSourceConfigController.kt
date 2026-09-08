package im.hikaru.ruoyi.module.infra.controller.admin.db

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.db.vo.DataSourceConfigRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.service.db.DataSourceConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 数据源配置")
@RestController
@RequestMapping("/infra/data-source-config")
@Validated
class DataSourceConfigController(
    private val dataSourceConfigService: DataSourceConfigService,
) {

    @PostMapping("/create")
    @Operation(summary = "创建数据源配置")
    @PreAuthorize("@ss.hasPermission('infra:data-source-config:create')")
    fun createDataSourceConfig(@Valid @RequestBody createReqVO: DataSourceConfigSaveReqVO): CommonResult<Long> =
        CommonResult.success(dataSourceConfigService.createDataSourceConfig(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新数据源配置")
    @PreAuthorize("@ss.hasPermission('infra:data-source-config:update')")
    fun updateDataSourceConfig(@Valid @RequestBody updateReqVO: DataSourceConfigSaveReqVO): CommonResult<Boolean> {
        dataSourceConfigService.updateDataSourceConfig(updateReqVO)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('infra:data-source-config:delete')")
    fun deleteDataSourceConfig(@RequestParam("id") id: Long): CommonResult<Boolean> {
        dataSourceConfigService.deleteDataSourceConfig(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除数据源配置")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('infra:data-source-config:delete')")
    fun deleteDataSourceConfigList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        dataSourceConfigService.deleteDataSourceConfigList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('infra:data-source-config:query')")
    fun getDataSourceConfig(@RequestParam("id") id: Long): CommonResult<DataSourceConfigRespVO?> =
        CommonResult.success(BeanUtils.toBean(dataSourceConfigService.getDataSourceConfig(id), DataSourceConfigRespVO::class.java))

    @GetMapping("/list")
    @Operation(summary = "获得数据源配置列表")
    @PreAuthorize("@ss.hasPermission('infra:data-source-config:query')")
    fun getDataSourceConfigList(): CommonResult<List<DataSourceConfigRespVO>> =
        CommonResult.success(
            BeanUtils.toBean(dataSourceConfigService.getDataSourceConfigList(), DataSourceConfigRespVO::class.java)
                ?: emptyList(),
        )
}
