package im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job

import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.infra.enums.DictTypeConstants
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "管理后台 - 定时任务 Response VO")
class JobRespVO {
    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试任务")
    var name: String? = null

    @Schema(description = "任务状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @DictFormat(DictTypeConstants.JOB_STATUS)
    var status: Int? = null

    @Schema(description = "处理器的名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "sysUserSessionTimeoutJob")
    var handlerName: String? = null

    @Schema(description = "处理器的参数", example = "yudao")
    var handlerParam: String? = null

    @Schema(description = "CRON 表达式", requiredMode = Schema.RequiredMode.REQUIRED, example = "0/10 * * * * ? *")
    var cronExpression: String? = null

    @Schema(description = "重试次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @field:NotNull(message = "重试次数不能为空")
    var retryCount: Int? = null

    @Schema(description = "重试间隔", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    var retryInterval: Int? = null

    @Schema(description = "监控超时时间", example = "1000")
    var monitorTimeout: Int? = null

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
