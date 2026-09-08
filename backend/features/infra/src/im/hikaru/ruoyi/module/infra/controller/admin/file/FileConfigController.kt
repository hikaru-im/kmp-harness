package im.hikaru.ruoyi.module.infra.controller.admin.file

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.service.file.FileConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 文件配置")
@RestController
@RequestMapping("/infra/file-config")
@Validated
class FileConfigController(
    private val fileConfigService: FileConfigService,
) {

    @PostMapping("/create")
    @Operation(summary = "创建文件配置")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:create')")
    fun createFileConfig(@Valid @RequestBody createReqVO: FileConfigSaveReqVO): CommonResult<Long> =
        CommonResult.success(fileConfigService.createFileConfig(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新文件配置")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:update')")
    fun updateFileConfig(@Valid @RequestBody updateReqVO: FileConfigSaveReqVO): CommonResult<Boolean> {
        fileConfigService.updateFileConfig(updateReqVO)
        return CommonResult.success(true)
    }

    @PutMapping("/update-master")
    @Operation(summary = "更新文件配置为 Master")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:update')")
    fun updateFileConfigMaster(@RequestParam("id") id: Long): CommonResult<Boolean> {
        fileConfigService.updateFileConfigMaster(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件配置")
    @Parameter(name = "id", description = "编号", required = true)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:delete')")
    fun deleteFileConfig(@RequestParam("id") id: Long): CommonResult<Boolean> {
        fileConfigService.deleteFileConfig(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除文件配置")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:delete')")
    fun deleteFileConfigList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        fileConfigService.deleteFileConfigList(ids)
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "获得文件配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:query')")
    fun getFileConfig(@RequestParam("id") id: Long): CommonResult<FileConfigRespVO?> =
        CommonResult.success(BeanUtils.toBean(fileConfigService.getFileConfig(id), FileConfigRespVO::class.java))

    @GetMapping("/page")
    @Operation(summary = "获得文件配置分页")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:query')")
    fun getFileConfigPage(@Valid pageVO: FileConfigPageReqVO): CommonResult<PageResult<FileConfigRespVO>> =
        CommonResult.success(BeanUtils.toBean(fileConfigService.getFileConfigPage(pageVO), FileConfigRespVO::class.java)!!)

    @GetMapping("/test")
    @Operation(summary = "测试文件配置是否正确")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:file-config:query')")
    @Throws(Exception::class)
    fun testFileConfig(@RequestParam("id") id: Long): CommonResult<String> =
        CommonResult.success(fileConfigService.testFileConfig(id))
}
