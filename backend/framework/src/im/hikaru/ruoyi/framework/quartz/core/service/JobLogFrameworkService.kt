package im.hikaru.ruoyi.framework.quartz.core.service

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * Job 日志 Framework Service 接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface JobLogFrameworkService {

    /**
     * 创建 Job 日志
     *
     * @return Job 日志的编号
     */
    fun createJobLog(
        @NotNull(message = "任务编号不能为空") jobId: Long,
        @NotNull(message = "开始时间") beginTime: LocalDateTime,
        @NotEmpty(message = "Job 处理器的名字不能为空") jobHandlerName: String,
        jobHandlerParam: String?,
        @NotNull(message = "第几次执行不能为空") executeIndex: Int,
    ): Long

    /**
     * 更新 Job 日志的执行结果
     */
    fun updateJobLogResultAsync(
        @NotNull(message = "日志编号不能为空") logId: Long,
        @NotNull(message = "结束时间不能为空") endTime: LocalDateTime,
        @NotNull(message = "运行时长不能为空") duration: Int,
        success: Boolean,
        result: String?,
    )
}
