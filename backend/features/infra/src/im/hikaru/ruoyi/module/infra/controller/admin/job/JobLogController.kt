package im.hikaru.ruoyi.module.infra.controller.admin.job

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log.JobLogPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log.JobLogRespVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobLogDO
import im.hikaru.ruoyi.module.infra.service.job.JobLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.IOException

@Tag(name = "管理后台 - 定时任务日志")
@RestController
@RequestMapping("/infra/job-log")
@Validated
class JobLogController(
    private val jobLogService: JobLogService,
) {

    @GetMapping("/get")
    @Operation(summary = "获得定时任务日志")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('infra:job:query')")
    fun getJobLog(@RequestParam("id") id: Long): CommonResult<JobLogRespVO?> =
        CommonResult.success(BeanUtils.toBean(jobLogService.getJobLog(id), JobLogRespVO::class.java))

    @GetMapping("/page")
    @Operation(summary = "获得定时任务日志分页")
    @PreAuthorize("@ss.hasPermission('infra:job:query')")
    fun getJobLogPage(@Valid pageVO: JobLogPageReqVO): CommonResult<PageResult<JobLogRespVO>> {
        val pageResult = jobLogService.getJobLogPage(pageVO)
        return CommonResult.success(BeanUtils.toBean(pageResult, JobLogRespVO::class.java)!!)
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出定时任务日志 Excel")
    @PreAuthorize("@ss.hasPermission('infra:job:export')")
    @Throws(IOException::class)
    fun exportJobLogExcel(@Valid exportReqVO: JobLogPageReqVO, response: HttpServletResponse) {
        exportReqVO.pageSize = PageParam.PAGE_SIZE_NONE
        val list: List<JobLogDO> = jobLogService.getJobLogPage(exportReqVO).list
        // 导出 Excel
        ExcelUtils.write(
            response, "任务日志.xls", "数据", JobLogRespVO::class.java,
            BeanUtils.toBean(list, JobLogRespVO::class.java) ?: emptyList(),
        )
    }
}
