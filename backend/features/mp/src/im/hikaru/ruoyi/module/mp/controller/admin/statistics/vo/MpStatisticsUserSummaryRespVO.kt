package im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 某一天的粉丝增减数据 Response VO")
class MpStatisticsUserSummaryRespVO {
    @field:Schema(description = "日期", requiredMode = Schema.RequiredMode.REQUIRED)
    var refDate: LocalDateTime? = null
    @field:Schema(description = "粉丝来源", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    var userSource: Int? = null
    @field:Schema(description = "新关注的粉丝数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    var newUser: Int? = null
    @field:Schema(description = "取消关注的粉丝数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    var cancelUser: Int? = null
}
