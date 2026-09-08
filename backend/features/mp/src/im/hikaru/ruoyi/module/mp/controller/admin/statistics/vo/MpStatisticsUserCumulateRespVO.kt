package im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 某一天的消息发送概况数据 Response VO")
class MpStatisticsUserCumulateRespVO {
    @field:Schema(description = "日期", requiredMode = Schema.RequiredMode.REQUIRED)
    var refDate: LocalDateTime? = null
    @field:Schema(description = "累计粉丝量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var cumulateUser: Int? = null
}
