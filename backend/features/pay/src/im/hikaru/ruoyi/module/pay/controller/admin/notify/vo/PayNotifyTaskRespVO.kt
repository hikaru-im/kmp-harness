package im.hikaru.ruoyi.module.pay.controller.admin.notify.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 回调通知 Response VO")
open class PayNotifyTaskRespVO {
    @field:Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "3380")
    var id: Long? = null
    @field:Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10636")
    var appId: Long? = null
    @field:Schema(description = "应用名称", example = "wx_pay")
    var appName: String? = null
    @field:Schema(description = "通知类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    var type: Byte? = null
    @field:Schema(description = "数据编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6722")
    var dataId: Long? = null
    @field:Schema(description = "商户订单编号", example = "26697")
    var merchantOrderId: String? = null
    @field:Schema(description = "商户退款编号", example = "26697")
    var merchantRefundId: String? = null
    @field:Schema(description = "商户转账编号", example = "26697")
    var merchantTransferId: String? = null
    @field:Schema(description = "通知状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    var status: Byte? = null
    @field:Schema(description = "下一次通知时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var nextNotifyTime: LocalDateTime? = null
    @field:Schema(description = "最后一次执行时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var lastExecuteTime: LocalDateTime? = null
    @field:Schema(description = "当前通知次数", requiredMode = Schema.RequiredMode.REQUIRED)
    var notifyTimes: Byte? = null
    @field:Schema(description = "最大可通知次数", requiredMode = Schema.RequiredMode.REQUIRED)
    var maxNotifyTimes: Byte? = null
    @field:Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
    @field:Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var updateTime: LocalDateTime? = null
}
