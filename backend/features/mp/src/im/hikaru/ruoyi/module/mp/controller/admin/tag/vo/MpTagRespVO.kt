package im.hikaru.ruoyi.module.mp.controller.admin.tag.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 公众号标签 Response VO")
class MpTagRespVO : MpTagBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "此标签下粉丝数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    var count: Int? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
