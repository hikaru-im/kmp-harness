package im.hikaru.ruoyi.module.infra.controller.admin.logger

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogRespVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiErrorLogDO
import im.hikaru.ruoyi.module.infra.service.logger.ApiErrorLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - API 错误日志")
@RestController
@RequestMapping("/infra/api-error-log")
@Validated
class ApiErrorLogController(
    private val apiErrorLogService: ApiErrorLogService,
) {

    @PutMapping("/update-status")
    @Operation(summary = "更新 API 错误日志的状态")
    @Parameters(
        Parameter(name = "id", description = "编号", required = true, example = "1024"),
        Parameter(name = "processStatus", description = "处理状态", required = true, example = "1"),
    )
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-error-log:update-status')")
    fun updateApiErrorLogProcess(
        @RequestParam("id") id: Long,
        @RequestParam("processStatus") processStatus: Int,
    ): CommonResult<Boolean> {
        apiErrorLogService.updateApiErrorLogProcess(id, processStatus, SecurityFrameworkUtils.getLoginUserId())
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "获得 API 错误日志")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-error-log:query')")
    fun getApiErrorLog(@RequestParam("id") id: Long): CommonResult<ApiErrorLogRespVO?> =
        CommonResult.success(BeanUtils.toBean(apiErrorLogService.getApiErrorLog(id), ApiErrorLogRespVO::class.java))

    @GetMapping("/page")
    @Operation(summary = "获得 API 错误日志分页")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-error-log:query')")
    fun getApiErrorLogPage(@Valid pageReqVO: ApiErrorLogPageReqVO): CommonResult<PageResult<ApiErrorLogRespVO>> {
        val pageResult = apiErrorLogService.getApiErrorLogPage(pageReqVO)
        return CommonResult.success(BeanUtils.toBean(pageResult, ApiErrorLogRespVO::class.java)!!)
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出 API 错误日志 Excel")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('infra:api-error-log:export')")
    @Throws(java.io.IOException::class)
    fun exportApiErrorLogExcel(@Valid exportReqVO: ApiErrorLogPageReqVO, response: HttpServletResponse) {
        exportReqVO.pageSize = PageParam.PAGE_SIZE_NONE
        val list = apiErrorLogService.getApiErrorLogPage(exportReqVO).list
        ExcelUtils.write(response, "API 错误日志.xls", "数据", ApiErrorLogRespVO::class.java,
            BeanUtils.toBean(list, ApiErrorLogRespVO::class.java) ?: emptyList())
    }
}
