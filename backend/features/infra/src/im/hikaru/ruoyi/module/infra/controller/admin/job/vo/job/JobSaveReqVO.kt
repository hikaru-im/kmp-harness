package im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "管理后台 - 定时任务创建/修改 Request VO")
class JobSaveReqVO {
    @Schema(description = "任务编号", example = "1024")
    var id: Long? = null

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试任务")
    @field:NotEmpty(message = "任务名称不能为空")
    var name: String? = null

    @Schema(description = "处理器的名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "sysUserSessionTimeoutJob")
    @field:NotEmpty(message = "处理器的名字不能为空")
    var handlerName: String? = null

    @Schema(description = "处理器的参数", example = "yudao")
    var handlerParam: String? = null

    @Schema(description = "CRON 表达式", requiredMode = Schema.RequiredMode.REQUIRED, example = "0/10 * * * * ? *")
    @field:NotEmpty(message = "CRON 表达式不能为空")
    var cronExpression: String? = null

    @Schema(description = "重试次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @field:NotNull(message = "重试次数不能为空")
    var retryCount: Int? = null

    @Schema(description = "重试间隔", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @field:NotNull(message = "重试间隔不能为空")
    var retryInterval: Int? = null

    @Schema(description = "监控超时时间", example = "1000")
    var monitorTimeout: Int? = null
}
