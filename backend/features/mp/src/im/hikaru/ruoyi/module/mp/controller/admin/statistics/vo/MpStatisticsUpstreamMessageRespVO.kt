package im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 某一天的粉丝增减数据 Response VO")
class MpStatisticsUpstreamMessageRespVO {
    @field:Schema(description = "日期", requiredMode = Schema.RequiredMode.REQUIRED)
    var refDate: LocalDateTime? = null
    @field:Schema(description = "上行发送了（向公众号发送了）消息的粉丝数", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var messageUser: Int? = null
    @field:Schema(description = "上行发送了消息的消息总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    var messageCount: Int? = null
}
