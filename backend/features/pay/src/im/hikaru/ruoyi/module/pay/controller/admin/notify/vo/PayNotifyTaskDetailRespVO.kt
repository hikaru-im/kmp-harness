package im.hikaru.ruoyi.module.pay.controller.admin.notify.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 回调通知的明细 Response VO")
class PayNotifyTaskDetailRespVO : PayNotifyTaskRespVO() {
    @field:Schema(description = "回调日志列表")
    var logs: List<Log>? = null

    @Schema(description = "管理后台 - 回调日志")
    class Log {
        @field:Schema(description = "日志编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "8848")
        var id: Long? = null
        @field:Schema(description = "通知状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        var status: Byte? = null
        @field:Schema(description = "当前通知次数", requiredMode = Schema.RequiredMode.REQUIRED)
        var notifyTimes: Byte? = null
        @field:Schema(description = "HTTP 响应结果", requiredMode = Schema.RequiredMode.REQUIRED)
        var response: String? = null
        @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
        var createTime: LocalDateTime? = null
    }
}
