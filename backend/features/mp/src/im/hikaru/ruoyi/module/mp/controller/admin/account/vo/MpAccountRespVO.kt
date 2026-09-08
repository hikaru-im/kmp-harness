package im.hikaru.ruoyi.module.mp.controller.admin.account.vo

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - 公众号账号 Response VO")
class MpAccountRespVO : MpAccountBaseVO() {
    @field:Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @field:Schema(description = "二维码图片URL", example = "https://www.iocoder.cn/1024.png")
    var qrCodeUrl: String? = null
    @field:Schema(description = "create time", requiredMode = Schema.RequiredMode.REQUIRED)
    var createTime: LocalDateTime? = null
}
