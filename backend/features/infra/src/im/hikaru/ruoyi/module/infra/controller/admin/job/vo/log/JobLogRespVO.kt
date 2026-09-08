package im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log

import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.infra.enums.DictTypeConstants
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 定时任务日志 Response VO")
class JobLogRespVO {
    @Schema(description = "日志编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var jobId: Long? = null

    @Schema(description = "处理器的名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "sysUserSessionTimeoutJob")
    var handlerName: String? = null

    @Schema(description = "处理器的参数", example = "yudao")
    var handlerParam: String? = null

    @Schema(description = "第几次执行", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var executeIndex: Int? = null

    @Schema(description = "开始执行时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var beginTime: LocalDateTime? = null

    @Schema(description = "结束执行时间")
    var endTime: LocalDateTime? = null

    @Schema(description = "执行时长", example = "123")
    var duration: Int? = null

    @Schema(description = "任务状态，参见 JobLogStatusEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @DictFormat(DictTypeConstants.JOB_LOG_STATUS)
    var status: Int? = null

    @Schema(description = "结果数据", example = "执行成功")
    var result: String? = null

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
