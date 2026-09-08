package im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 某一天的接口分析数据 Response VO")
class MpStatisticsInterfaceSummaryRespVO {
    @field:Schema(description = "日期", requiredMode = Schema.RequiredMode.REQUIRED)
    var refDate: LocalDateTime? = null
    @field:Schema(description = "通过服务器配置地址获得消息后，被动回复粉丝消息的次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var callbackCount: Int? = null
    @field:Schema(description = "上述动作的失败次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    var failCount: Int? = null
    @field:Schema(description = "总耗时，除以 callback_count 即为平均耗时", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    var totalTimeCost: Int? = null
    @field:Schema(description = "最大耗时", requiredMode = Schema.RequiredMode.REQUIRED, example = "40")
    var maxTimeCost: Int? = null
}
