package im.hikaru.ruoyi.module.member.controller.app.point.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "用户 App - 用户积分记录 Response VO")
class AppMemberPointRecordRespVO {
    @field:Schema(description = "自增主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "31457")
    var id: Long? = null
    @field:Schema(description = "积分标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "你猜")
    var title: String? = null
    @field:Schema(description = "积分描述", example = "你猜")
    var description: String? = null
    @field:Schema(description = "积分", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var point: Int? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
