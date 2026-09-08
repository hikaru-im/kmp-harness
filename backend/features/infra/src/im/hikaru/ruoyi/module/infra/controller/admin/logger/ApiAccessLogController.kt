package im.hikaru.ruoyi.module.infra.controller.admin.logger

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogRespVO
import im.hikaru.ruoyi.module.infra.service.logger.ApiAccessLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - API 访问日志")
@RestController
@RequestMapping("/infra/api-access-log")
@Validated
class ApiAccessLogController(
    private val apiAccessLogService: ApiAccessLogService,
) {

    @GetMapping("/get")
    @Operation(summary = "获得 API 访问日志")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-access-log:query')")
    fun getApiAccessLog(@RequestParam("id") id: Long): CommonResult<ApiAccessLogRespVO?> =
        CommonResult.success(BeanUtils.toBean(apiAccessLogService.getApiAccessLog(id), ApiAccessLogRespVO::class.java))

    @GetMapping("/page")
    @Operation(summary = "获得API 访问日志分页")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-access-log:query')")
    fun getApiAccessLogPage(@Valid pageReqVO: ApiAccessLogPageReqVO): CommonResult<PageResult<ApiAccessLogRespVO>> {
        val pageResult = apiAccessLogService.getApiAccessLogPage(pageReqVO)
        return CommonResult.success(BeanUtils.toBean(pageResult, ApiAccessLogRespVO::class.java)!!)
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出API 访问日志 Excel")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-access-log:export')")
    @Throws(java.io.IOException::class)
    fun exportApiAccessLogExcel(@Valid exportReqVO: ApiAccessLogPageReqVO, response: HttpServletResponse) {
        exportReqVO.pageSize = PageParam.PAGE_SIZE_NONE
        val list = apiAccessLogService.getApiAccessLogPage(exportReqVO).list
        ExcelUtils.write(response, "API 访问日志.xls", "数据", ApiAccessLogRespVO::class.java,
            BeanUtils.toBean(list, ApiAccessLogRespVO::class.java) ?: emptyList())
    }
}
