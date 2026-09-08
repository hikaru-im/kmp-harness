package im.hikaru.ruoyi.module.infra.controller.admin.job

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.framework.quartz.core.util.CronUtils
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobDO
import im.hikaru.ruoyi.module.infra.service.job.JobService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.quartz.SchedulerException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.io.IOException

@Tag(name = "管理后台 - 定时任务")
@RestController
@RequestMapping("/infra/job")
@Validated
class JobController(
    private val jobService: JobService,
) {

    @PostMapping("/create")
    @Operation(summary = "创建定时任务")
    @PreAuthorize("@ss.hasPermission('infra:job:create')")
    @Throws(SchedulerException::class)
    fun createJob(@Valid @RequestBody createReqVO: JobSaveReqVO): CommonResult<Long> =
        CommonResult.success(jobService.createJob(createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新定时任务")
    @PreAuthorize("@ss.hasPermission('infra:job:update')")
    @Throws(SchedulerException::class)
    fun updateJob(@Valid @RequestBody updateReqVO: JobSaveReqVO): CommonResult<Boolean> {
        jobService.updateJob(updateReqVO)
        return CommonResult.success(true)
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新定时任务的状态")
    @Parameters(
        Parameter(name = "id", description = "编号", required = true, example = "1024"),
        Parameter(name = "status", description = "状态", required = true, example = "1"),
    )
    @PreAuthorize("@ss.hasPermission('infra:job:update')")
    @Throws(SchedulerException::class)
    fun updateJobStatus(
        @RequestParam(value = "id") id: Long,
        @RequestParam("status") status: Int,
    ): CommonResult<Boolean> {
        jobService.updateJobStatus(id, status)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除定时任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('infra:job:delete')")
    @Throws(SchedulerException::class)
    fun deleteJob(@RequestParam("id") id: Long): CommonResult<Boolean> {
        jobService.deleteJob(id)
        return CommonResult.success(true)
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除定时任务")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('infra:job:delete')")
    @Throws(SchedulerException::class)
    fun deleteJobList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> {
        jobService.deleteJobList(ids)
        return CommonResult.success(true)
    }

    @PutMapping("/trigger")
    @Operation(summary = "触发定时任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('infra:job:trigger')")
    @Throws(SchedulerException::class)
    fun triggerJob(@RequestParam("id") id: Long): CommonResult<Boolean> {
        jobService.triggerJob(id)
        return CommonResult.success(true)
    }

    @PostMapping("/sync")
    @Operation(summary = "同步定时任务")
    @PreAuthorize("@ss.hasPermission('infra:job:create')")
    @Throws(SchedulerException::class)
    fun syncJob(): CommonResult<Boolean> {
        jobService.syncJob()
        return CommonResult.success(true)
    }

    @GetMapping("/get")
    @Operation(summary = "获得定时任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('infra:job:query')")
    fun getJob(@RequestParam("id") id: Long): CommonResult<JobRespVO?> =
        CommonResult.success(BeanUtils.toBean(jobService.getJob(id), JobRespVO::class.java))

    @GetMapping("/page")
    @Operation(summary = "获得定时任务分页")
    @PreAuthorize("@ss.hasPermission('infra:job:query')")
    fun getJobPage(@Valid pageVO: JobPageReqVO): CommonResult<PageResult<JobRespVO>> {
        val pageResult = jobService.getJobPage(pageVO)
        return CommonResult.success(BeanUtils.toBean(pageResult, JobRespVO::class.java)!!)
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出定时任务 Excel")
    @PreAuthorize("@ss.hasPermission('infra:job:export')")
    @Throws(IOException::class)
    fun exportJobExcel(@Valid exportReqVO: JobPageReqVO, response: HttpServletResponse) {
        exportReqVO.pageSize = PageParam.PAGE_SIZE_NONE
        val list: List<JobDO> = jobService.getJobPage(exportReqVO).list
        // 导出 Excel
        ExcelUtils.write(
            response, "定时任务.xls", "数据", JobRespVO::class.java,
            BeanUtils.toBean(list, JobRespVO::class.java) ?: emptyList(),
        )
    }

    @GetMapping("/get_next_times")
    @Operation(summary = "获得定时任务的下 n 次执行时间")
    @Parameters(
        Parameter(name = "id", description = "编号", required = true, example = "1024"),
        Parameter(name = "count", description = "数量", example = "5"),
    )
    @PreAuthorize("@ss.hasPermission('infra:job:query')")
    fun getJobNextTimes(
        @RequestParam("id") id: Long,
        @RequestParam(value = "count", required = false, defaultValue = "5") count: Int,
    ): CommonResult<List<LocalDateTime>> {
        val job: JobDO? = jobService.getJob(id)
        if (job == null) {
            return CommonResult.success(emptyList())
        }
        return CommonResult.success(CronUtils.getNextTimes(job.cronExpression!!, count))
    }
}
